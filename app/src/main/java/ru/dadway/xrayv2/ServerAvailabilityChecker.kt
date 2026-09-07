package ru.dadway.xrayv2

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.measureTimeMillis

object ServerAvailabilityChecker {
    suspend fun checkAll(nodes: List<ServerNode>): List<ServerNode> = coroutineScope {
        val availabilityByEndpoint = nodes
            .distinctBy(::endpointKey)
            .map { node -> async(Dispatchers.IO) { endpointKey(node) to check(node) } }
            .awaitAll()
            .toMap()
        nodes.map { node -> node.copy(availability = availabilityByEndpoint.getValue(endpointKey(node))) }
    }

    private fun endpointKey(node: ServerNode) = "${node.host.lowercase()}:${node.port}"

    private suspend fun check(node: ServerNode): Availability =
        measureLatency(node, attempts = 1)?.let(Availability::Available) ?: Availability.Unavailable

    suspend fun measureLatency(
        node: ServerNode,
        attempts: Int = 3,
        timeoutMillis: Int = 2_500,
    ): Long? = withContext(Dispatchers.IO) {
        require(attempts > 0) { "Количество измерений должно быть больше нуля" }
        medianLatency(
            buildList {
                repeat(attempts) {
                    tcpLatency(node.host, node.port, timeoutMillis)?.let { latency -> add(latency) }
                }
            },
        )
    }

    private fun tcpLatency(host: String, port: Int, timeoutMillis: Int): Long? = runCatching {
        var latency = 0L
        Socket().use { socket ->
            latency = measureTimeMillis { socket.connect(InetSocketAddress(host, port), timeoutMillis) }
        }
        latency.coerceAtLeast(1)
    }.getOrNull()

    internal fun medianLatency(samples: List<Long>): Long? {
        if (samples.isEmpty()) return null
        val sorted = samples.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }
}

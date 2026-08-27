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

    private suspend fun check(node: ServerNode): Availability = withContext(Dispatchers.IO) {
        runCatching {
            var latency = 0L
            Socket().use { socket ->
                latency = measureTimeMillis { socket.connect(InetSocketAddress(node.host, node.port), 2500) }
            }
            Availability.Available(latency.coerceAtLeast(1))
        }.getOrDefault(Availability.Unavailable)
    }
}

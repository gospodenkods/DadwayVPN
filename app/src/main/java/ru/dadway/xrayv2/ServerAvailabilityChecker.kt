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
        nodes.map { node -> async(Dispatchers.IO) { node.copy(availability = check(node)) } }.awaitAll()
    }

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

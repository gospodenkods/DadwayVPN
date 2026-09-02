package ru.dadway.xrayv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

class ConnectionTesterTest {
    @Test
    fun waitsUntilLocalEndpointStartsListening() {
        val port = ServerSocket(0).use { it.localPort }
        val server = thread(start = true, isDaemon = true, name = "delayed-socks-test") {
            Thread.sleep(150)
            ServerSocket().use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress("127.0.0.1", port))
                socket.accept().use { }
            }
        }

        assertTrue(
            ConnectionTester.waitForEndpoint(
                host = "127.0.0.1",
                port = port,
                timeoutMillis = 1_500,
                pollIntervalMillis = 25,
                connectTimeoutMillis = 50,
            ),
        )
        server.join(1_000)
    }

    @Test
    fun retriesTransientFailuresThreeTimesWithDelay() {
        var attempts = 0
        val delays = mutableListOf<Long>()

        val result = ConnectionTester.retryWithDelay(
            attempts = 3,
            delayMillis = 800,
            sleeper = { delays += it },
        ) {
            attempts += 1
            if (attempts < 3) throw SocketTimeoutException("temporary timeout")
            "connected"
        }

        assertEquals("connected", result)
        assertEquals(3, attempts)
        assertEquals(listOf(800L, 800L), delays)
    }
}

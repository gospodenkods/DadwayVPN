package ru.dadway.xrayv2

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object ConnectionTester {
    data class Result(val ip: String, val pingMs: Long, val bytesPerSecond: Long)

    private const val PROXY_READY_TIMEOUT_MS = 10_000L
    private const val PROXY_POLL_INTERVAL_MS = 200L
    private const val PROXY_CONNECT_TIMEOUT_MS = 250
    private const val TEST_ATTEMPTS = 3
    private const val TEST_RETRY_DELAY_MS = 800L

    private fun proxy() = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", XrayConfigBuilder.SOCKS_PORT))

    fun test(): Result {
        awaitProxyReady()
        return retryWithDelay(TEST_ATTEMPTS, TEST_RETRY_DELAY_MS) { testOnce() }
    }

    fun awaitProxyReady() {
        if (!waitForEndpoint("127.0.0.1", XrayConfigBuilder.SOCKS_PORT, PROXY_READY_TIMEOUT_MS)) {
            throw SocketTimeoutException(
                "Локальный SOCKS-прокси 127.0.0.1:${XrayConfigBuilder.SOCKS_PORT} не запустился за 10 секунд",
            )
        }
    }

    private fun testOnce(): Result {
        val start = System.nanoTime()
        val ipConn = URL("https://api.ipify.org").openConnection(proxy()) as HttpsURLConnection
        ipConn.connectTimeout = 10_000; ipConn.readTimeout = 10_000
        val ip = try {
            ipConn.inputStream.bufferedReader().use { it.readText().trim() }
        } finally {
            ipConn.disconnect()
        }
        val ping = (System.nanoTime() - start) / 1_000_000

        val speedStart = System.nanoTime()
        val c = URL("https://speed.cloudflare.com/__down?bytes=1000000").openConnection(proxy()) as HttpsURLConnection
        c.connectTimeout = 12_000; c.readTimeout = 20_000
        var total = 0L
        try {
            c.inputStream.use { input ->
                val buf = ByteArray(32 * 1024)
                while (true) { val n = input.read(buf); if (n <= 0) break; total += n }
            }
        } finally {
            c.disconnect()
        }
        val seconds = ((System.nanoTime() - speedStart) / 1_000_000_000.0).coerceAtLeast(0.001)
        return Result(ip, ping, (total / seconds).toLong())
    }

    internal fun waitForEndpoint(
        host: String,
        port: Int,
        timeoutMillis: Long,
        pollIntervalMillis: Long = PROXY_POLL_INTERVAL_MS,
        connectTimeoutMillis: Int = PROXY_CONNECT_TIMEOUT_MS,
    ): Boolean {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        do {
            val connected = runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), connectTimeoutMillis)
                }
            }.isSuccess
            if (connected) return true

            val remainingMillis = (deadline - System.nanoTime()) / 1_000_000
            if (remainingMillis <= 0) return false
            Thread.sleep(minOf(pollIntervalMillis, remainingMillis))
        } while (true)
    }

    internal fun <T> retryWithDelay(
        attempts: Int,
        delayMillis: Long,
        sleeper: (Long) -> Unit = Thread::sleep,
        block: () -> T,
    ): T {
        require(attempts > 0) { "Количество попыток должно быть больше нуля" }
        var lastFailure: Throwable? = null
        repeat(attempts) { attempt ->
            try {
                return block()
            } catch (error: Exception) {
                lastFailure = error
                if (attempt < attempts - 1) sleeper(delayMillis)
            }
        }
        throw checkNotNull(lastFailure)
    }
}

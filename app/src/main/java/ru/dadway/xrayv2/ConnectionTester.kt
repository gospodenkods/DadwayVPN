package ru.dadway.xrayv2

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object ConnectionTester {
    data class Result(val ip: String, val pingMs: Long, val bytesPerSecond: Long)

    private fun proxy() = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", XrayConfigBuilder.SOCKS_PORT))

    fun test(): Result {
        val start = System.nanoTime()
        val ipConn = URL("https://api.ipify.org").openConnection(proxy()) as HttpsURLConnection
        ipConn.connectTimeout = 10_000; ipConn.readTimeout = 10_000
        val ip = ipConn.inputStream.bufferedReader().use { it.readText().trim() }
        ipConn.disconnect()
        val ping = (System.nanoTime() - start) / 1_000_000

        val speedStart = System.nanoTime()
        val c = URL("https://speed.cloudflare.com/__down?bytes=1000000").openConnection(proxy()) as HttpsURLConnection
        c.connectTimeout = 12_000; c.readTimeout = 20_000
        var total = 0L
        c.inputStream.use { input ->
            val buf = ByteArray(32 * 1024)
            while (true) { val n = input.read(buf); if (n <= 0) break; total += n }
        }
        c.disconnect()
        val seconds = ((System.nanoTime() - speedStart) / 1_000_000_000.0).coerceAtLeast(0.001)
        return Result(ip, ping, (total / seconds).toLong())
    }
}

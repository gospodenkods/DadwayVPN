package ru.dadway.xrayv2

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object MetricsReader {
    data class Totals(val down: Long, val up: Long)

    fun read(): Totals {
        val c = URL("http://127.0.0.1:${XrayConfigBuilder.METRICS_PORT}/debug/vars").openConnection() as HttpURLConnection
        c.connectTimeout = 1000; c.readTimeout = 1000
        val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        c.disconnect()
        var down = 0L; var up = 0L
        fun walk(value: Any?, key: String = "") {
            when (value) {
                is JSONObject -> value.keys().forEach { k -> walk(value.opt(k), k) }
                is Number -> {
                    val lk = key.lowercase()
                    if (lk.contains("downlink")) down += value.toLong()
                    if (lk.contains("uplink")) up += value.toLong()
                }
            }
        }
        walk(json)
        return Totals(down, up)
    }
}

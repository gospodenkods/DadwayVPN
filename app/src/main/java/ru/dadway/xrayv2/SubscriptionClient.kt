package ru.dadway.xrayv2

import android.content.Context
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL

object SubscriptionClient {
    private const val PREFS = "dadway_v2"

    fun fetch(context: Context, subscriptionUrl: String, cacheKey: String): String {
        val connection = (URL(subscriptionUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "text/plain, */*")
            setRequestProperty("User-Agent", "DadwayVPN/8.3.0 Android")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code при загрузке подписки")
            val raw = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
            val decoded = decodeIfBase64(raw)
            require(decoded.contains("://")) { "Подписка не содержит ссылок подключения" }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(cacheKey, decoded).apply()
            return decoded
        } finally {
            connection.disconnect()
        }
    }

    fun cached(context: Context, cacheKey: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(cacheKey, null)

    private fun decodeIfBase64(value: String): String {
        if (value.contains("://")) return value
        return runCatching {
            val normalized = value.replace("\n", "").replace("\r", "").trim()
            String(Base64.decode(normalized, Base64.DEFAULT), Charsets.UTF_8).trim()
        }.getOrDefault(value)
    }
}

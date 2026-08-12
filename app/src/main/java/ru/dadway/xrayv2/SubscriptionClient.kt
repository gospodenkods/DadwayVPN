package ru.dadway.xrayv2

import android.content.Context
import android.util.Base64
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class SubscriptionAccessException(val statusCode: Int) : IOException(
    when (statusCode) {
        403 -> "Доступ к подписке запрещён"
        404 -> "Подписка отключена"
        410 -> "Срок действия подписки истёк"
        else -> "Подписка недоступна (HTTP $statusCode)"
    }
)

object SubscriptionClient {
    private const val PREFS = "dadway_v2"
    private val deniedStatusCodes = setOf(401, 403, 404, 410)

    fun fetch(context: Context, subscriptionUrl: String, cacheKey: String): String {
        val connection = (URL(subscriptionUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept", "text/plain, */*")
            setRequestProperty("Cache-Control", "no-cache, no-store")
            setRequestProperty("Pragma", "no-cache")
            setRequestProperty("User-Agent", "DadwayVPN/${BuildConfig.VERSION_NAME} Android")
        }
        try {
            val code = connection.responseCode
            if (code in deniedStatusCodes) {
                clear(context, cacheKey)
                throw SubscriptionAccessException(code)
            }
            if (code !in 200..299) throw IOException("HTTP $code при загрузке подписки")
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

    fun clear(context: Context, cacheKey: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(cacheKey).apply()
    }

    private fun decodeIfBase64(value: String): String {
        if (value.contains("://")) return value
        return runCatching {
            val normalized = value.replace("\n", "").replace("\r", "").trim()
            String(Base64.decode(normalized, Base64.DEFAULT), Charsets.UTF_8).trim()
        }.getOrDefault(value)
    }
}

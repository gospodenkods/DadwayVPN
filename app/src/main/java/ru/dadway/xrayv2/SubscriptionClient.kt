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
        val raw = try {
            fetchRaw(subscriptionUrl, SubscriptionDeviceId.get(context))
        } catch (error: SubscriptionAccessException) {
            clear(context, cacheKey)
            throw error
        }
        val decoded = decodeIfBase64(raw.trim())
        require(decoded.contains("://")) { "Подписка не содержит ссылок подключения" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(cacheKey, decoded).apply()
        return decoded
    }

    internal fun fetchRaw(
        subscriptionUrl: String,
        hwid: String,
        openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    ): String {
        val original = URL(subscriptionUrl.substringBefore('#'))
        require(original.protocol.equals("https", true)) { "Подписка должна использовать HTTPS" }
        var target = original
        repeat(6) { redirectCount ->
            val connection = openConnection(target).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                requestMethod = "GET"
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("Accept", "text/plain, */*")
                setRequestProperty("Cache-Control", "no-cache, no-store")
                setRequestProperty("Pragma", "no-cache")
                setRequestProperty("User-Agent", "DadwayVPN/${BuildConfig.VERSION_NAME} Android")
                // Do not expose the device identifier to a different origin through a redirect.
                if (sameOrigin(original, target)) setRequestProperty("X-HWID", hwid)
            }
            try {
                val code = connection.responseCode
                if (code in deniedStatusCodes) throw SubscriptionAccessException(code)
                if (code in setOf(301, 302, 303, 307, 308)) {
                    if (redirectCount == 5) throw IOException("Слишком много перенаправлений подписки")
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("Сервер подписки не указал адрес перенаправления")
                    val next = URL(target, location)
                    if (!next.protocol.equals("https", true)) throw IOException("Небезопасное перенаправление подписки")
                    target = next
                    return@repeat
                }
                if (code !in 200..299) throw IOException("HTTP $code при загрузке подписки")
                return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Не удалось загрузить подписку")
    }

    private fun sameOrigin(first: URL, second: URL): Boolean =
        first.protocol.equals(second.protocol, true) && first.host.equals(second.host, true) &&
            (if (first.port == -1) first.defaultPort else first.port) ==
            (if (second.port == -1) second.defaultPort else second.port)

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

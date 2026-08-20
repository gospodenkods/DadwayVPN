package ru.dadway.xrayv2

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.util.UUID

data class SubscriptionSource(
    val id: String,
    val url: String,
    val enabled: Boolean,
) {
    val title: String
        get() = runCatching {
            val parsed = URL(url)
            "${parsed.host}${parsed.path}".removeSuffix("/")
        }.getOrDefault(url)
}

object SubscriptionStore {
    private const val PREFS = "dadway_subscriptions"
    private const val KEY_SOURCES = "sources_v1"
    private const val DEFAULT_URL = "https://devel.dadway.ru/sub/zpp#dadway.ru"

    fun all(context: Context): List<SubscriptionSource> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_SOURCES, null)
        if (saved.isNullOrBlank()) {
            return listOf(SubscriptionSource("default-zpp", DEFAULT_URL, true)).also { save(context, it) }
        }
        return runCatching {
            val array = JSONArray(saved)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(SubscriptionSource(item.getString("id"), item.getString("url"), item.optBoolean("enabled", true)))
                }
            }
        }.getOrElse {
            listOf(SubscriptionSource("default-zpp", DEFAULT_URL, true)).also { save(context, it) }
        }
    }

    fun add(context: Context, url: String): SubscriptionSource {
        val normalized = normalize(url)
        require(all(context).none { it.url.equals(normalized, true) }) { "Такая подписка уже добавлена" }
        val source = SubscriptionSource(UUID.randomUUID().toString(), normalized, true)
        save(context, all(context) + source)
        return source
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        save(context, all(context).map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun remove(context: Context, id: String) {
        val source = all(context).firstOrNull { it.id == id }
        save(context, all(context).filterNot { it.id == id })
        source?.let { SubscriptionClient.clear(context, cacheKey(it)) }
    }

    fun cacheKey(source: SubscriptionSource): String = "subscription_${source.id.replace("-", "_")}"

    private fun save(context: Context, sources: List<SubscriptionSource>) {
        val array = JSONArray()
        sources.forEach { source ->
            array.put(JSONObject().put("id", source.id).put("url", source.url).put("enabled", source.enabled))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SOURCES, array.toString()).apply()
    }

    private fun normalize(value: String): String {
        val trimmed = value.trim()
        val parsed = runCatching { URL(trimmed.substringBefore('#')) }.getOrNull()
        require(parsed != null && parsed.protocol == "https" && parsed.host.isNotBlank()) {
            "Введите корректную HTTPS-ссылку подписки"
        }
        return trimmed
    }
}

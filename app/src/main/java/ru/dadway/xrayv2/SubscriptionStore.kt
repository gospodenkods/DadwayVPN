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
            val segments = parsed.path.split('/').filter(String::isNotBlank)
            val safePath = when {
                segments.isEmpty() -> ""
                segments.size <= 2 || segments.first().equals("sub", true) ->
                    "/${segments.joinToString("/")}"
                else -> "/${segments.dropLast(1).joinToString("/")}/…${segments.last().takeLast(4)}"
            }
            "${parsed.host}$safePath"
        }.getOrDefault(url)
}

object SubscriptionStore {
    private const val PREFS = "dadway_subscriptions"
    private const val KEY_SOURCES = "sources_v1"
    private const val KEY_DEFAULTS_VERSION = "defaults_version"
    private const val DEFAULTS_VERSION = 4
    private val BUNDLED_SOURCE_IDS = setOf("default-promo", "default-zpp", "default-germany-1")

    fun all(context: Context): List<SubscriptionSource> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_SOURCES, null)
        if (saved.isNullOrBlank()) {
            return defaultSources().also {
                save(context, it)
                prefs.edit().putInt(KEY_DEFAULTS_VERSION, DEFAULTS_VERSION).apply()
            }
        }
        val sources = runCatching {
            val array = JSONArray(saved)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(SubscriptionSource(item.getString("id"), item.getString("url"), item.optBoolean("enabled", true)))
                }
            }
        }.getOrElse {
            emptyList<SubscriptionSource>().also { save(context, it) }
        }
        if (prefs.getInt(KEY_DEFAULTS_VERSION, 1) >= DEFAULTS_VERSION) return sources

        val bundled = sources.filter(::isBundledSource)
        bundled.forEach { SubscriptionClient.clear(context, cacheKey(it)) }
        val migrated = removeBundledSources(sources)
        save(context, migrated)
        prefs.edit().putInt(KEY_DEFAULTS_VERSION, DEFAULTS_VERSION).apply()
        return migrated
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

    private fun defaultSources(): List<SubscriptionSource> = emptyList()

    internal fun removeBundledSources(sources: List<SubscriptionSource>): List<SubscriptionSource> =
        sources.filterNot(::isBundledSource)

    private fun isBundledSource(source: SubscriptionSource): Boolean = source.id in BUNDLED_SOURCE_IDS
}

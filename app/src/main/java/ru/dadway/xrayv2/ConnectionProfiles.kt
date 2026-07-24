package ru.dadway.xrayv2

import android.content.Context

/** A connection source shown in the profile drop-down. */
data class ConnectionProfile(
    val id: String,
    val title: String,
    val source: Source
) {
    sealed interface Source {
        data class Subscription(
            val url: String,
            val cacheKey: String
        ) : Source
    }
}

object ConnectionProfiles {
    private const val PREFS = "dadway_connection_profiles"
    private const val KEY_SELECTED = "selected_profile_id"

    const val DEFAULT_ID = "dadway_subscription"
    const val RESERVE_ID = "reserve_subscription"
    const val NETHERLANDS_ID = "netherlands_subscription"
    // Kept as an alias so existing service code and saved installations remain compatible.
    const val SABINA_ID = RESERVE_ID

    private const val PRIMARY_SUBSCRIPTION_URL =
        "https://promo.dadway.ru/sub/tnt9ztgjgvizzclm"
    private const val RESERVE_SUBSCRIPTION_URL =
        "https://zpp.div3.ru:2096/sub/m315s5c3qc51hkoj"
    private const val NETHERLANDS_SUBSCRIPTION_URL =
        "http://mikrot.icu:2096/sub/hqx2y9f5rar310zd"

    val all: List<ConnectionProfile> = listOf(
        ConnectionProfile(
            DEFAULT_ID,
            "Россия",
            ConnectionProfile.Source.Subscription(
                url = PRIMARY_SUBSCRIPTION_URL,
                cacheKey = "primary_subscription_text"
            )
        ),
        ConnectionProfile(
            RESERVE_ID,
            "USA",
            ConnectionProfile.Source.Subscription(
                url = RESERVE_SUBSCRIPTION_URL,
                cacheKey = "reserve_subscription_text"
            )
        ),
        ConnectionProfile(
            NETHERLANDS_ID,
            "Netherlands",
            ConnectionProfile.Source.Subscription(
                url = NETHERLANDS_SUBSCRIPTION_URL,
                cacheKey = "netherlands_subscription_text"
            )
        )
    )

    fun byId(id: String): ConnectionProfile =
        all.firstOrNull { it.id == id } ?: error("Неизвестный профиль подключения: $id")

    fun selected(context: Context): ConnectionProfile {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED, DEFAULT_ID)
        return all.firstOrNull { it.id == id } ?: all.first()
    }

    fun select(context: Context, id: String) {
        require(all.any { it.id == id }) { "Неизвестный профиль подключения" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED, id)
            .apply()
    }

    /**
     * Returns the first share link used by libXray conversion.  The original
     * link is also passed to XrayConfigBuilder so query parameters such as
     * REALITY sni/fp/pbk/sid and XHTTP path are not lost by older converters.
     */
    fun firstShareLink(text: String): String? = text
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { line ->
            line.startsWith("vless://", ignoreCase = true) ||
                line.startsWith("vmess://", ignoreCase = true) ||
                line.startsWith("trojan://", ignoreCase = true) ||
                line.startsWith("ss://", ignoreCase = true)
        }

    fun connectionText(
        context: Context,
        profile: ConnectionProfile = selected(context)
    ): String = when (val source = profile.source) {
        is ConnectionProfile.Source.Subscription ->
            runCatching {
                SubscriptionClient.fetch(context, source.url, source.cacheKey)
            }.getOrElse {
                SubscriptionClient.cached(context, source.cacheKey) ?: throw it
            }
    }
}

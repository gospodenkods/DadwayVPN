package ru.dadway.xrayv2

import android.content.Context

object ConnectionProfiles {
    const val SUBSCRIPTION_URL = "https://devel.dadway.ru/sub/dadway"
    private const val PREFS = "dadway_servers"
    private const val CACHE_KEY = "dadway_subscription_text"
    private const val KEY_SELECTED = "selected_server_id"

    fun load(context: Context, refresh: Boolean = true): List<ServerNode> {
        val text = if (refresh) {
            runCatching { SubscriptionClient.fetch(context, SUBSCRIPTION_URL, CACHE_KEY) }
                .getOrElse { SubscriptionClient.cached(context, CACHE_KEY) ?: throw it }
        } else {
            SubscriptionClient.cached(context, CACHE_KEY)
                ?: SubscriptionClient.fetch(context, SUBSCRIPTION_URL, CACHE_KEY)
        }
        return ServerNodeParser.parseSubscription(text).also {
            require(it.isNotEmpty()) { "Подписка не содержит поддерживаемых серверов" }
        }
    }

    fun selected(context: Context, nodes: List<ServerNode> = load(context, false)): ServerNode {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SELECTED, null)
        return nodes.firstOrNull { it.id == id } ?: nodes.first()
    }

    fun select(context: Context, node: ServerNode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SELECTED, node.id).apply()
    }

    fun connectionText(context: Context): Pair<ServerNode, String> {
        val nodes = load(context, true)
        val node = selected(context, nodes)
        return node to node.link
    }

    fun firstShareLink(text: String): String? = text.lineSequence().map(String::trim)
        .firstOrNull { it.contains("://") }
}

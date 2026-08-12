package ru.dadway.xrayv2

import android.content.Context
import java.io.IOException

data class SubscriptionLoadResult(
    val nodes: List<ServerNode>,
    val fromCache: Boolean,
)

object ConnectionProfiles {
    const val SUBSCRIPTION_URL =
        "https://devel.dadway.ru/sub/promo#https%3A%2F%2Fdadway.ru"
    private const val PREFS = "dadway_servers"
    private const val CACHE_KEY = "dadway_subscription_text"
    private const val KEY_SELECTED = "selected_server_id"

    fun loadWithStatus(
        context: Context,
        refresh: Boolean = true,
        allowCachedOnNetworkError: Boolean = true,
    ): SubscriptionLoadResult {
        var fromCache = false
        val text = if (refresh) {
            try {
                SubscriptionClient.fetch(context, SUBSCRIPTION_URL, CACHE_KEY)
            } catch (error: SubscriptionAccessException) {
                clear(context)
                throw error
            } catch (error: IOException) {
                if (!allowCachedOnNetworkError) throw error
                fromCache = true
                SubscriptionClient.cached(context, CACHE_KEY) ?: throw error
            }
        } else {
            SubscriptionClient.cached(context, CACHE_KEY)
                ?: SubscriptionClient.fetch(context, SUBSCRIPTION_URL, CACHE_KEY)
        }
        val nodes = ServerNodeParser.parseSubscription(text).also {
            require(it.isNotEmpty()) { "Подписка не содержит поддерживаемых серверов" }
        }
        return SubscriptionLoadResult(nodes, fromCache)
    }

    fun load(context: Context, refresh: Boolean = true): List<ServerNode> =
        loadWithStatus(context, refresh).nodes

    fun selected(context: Context, nodes: List<ServerNode> = load(context, false)): ServerNode {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SELECTED, null)
        return nodes.firstOrNull { it.id == id } ?: nodes.first()
    }

    fun select(context: Context, node: ServerNode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SELECTED, node.id).apply()
    }

    fun clear(context: Context) {
        SubscriptionClient.clear(context, CACHE_KEY)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_SELECTED).apply()
    }

    fun connectionText(context: Context): Pair<ServerNode, String> {
        val nodes = loadWithStatus(context, refresh = true, allowCachedOnNetworkError = false).nodes
        val node = selected(context, nodes)
        return node to node.link
    }

    fun firstShareLink(text: String): String? = text.lineSequence().map(String::trim)
        .firstOrNull { it.contains("://") }
}

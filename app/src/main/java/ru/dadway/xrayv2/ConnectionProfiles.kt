package ru.dadway.xrayv2

import android.content.Context

data class SubscriptionLoadResult(
    val nodes: List<ServerNode>,
    val fromCache: Boolean,
)

object ConnectionProfiles {
    private const val PREFS = "dadway_servers"
    private const val KEY_SELECTED = "selected_server_id"

    fun loadWithStatus(
        context: Context,
        refresh: Boolean = true,
        allowCachedOnNetworkError: Boolean = true,
    ): SubscriptionLoadResult {
        val sources = SubscriptionStore.all(context).filter(SubscriptionSource::enabled)
        require(sources.isNotEmpty()) { "Нет активных подписок. Включите или добавьте подписку в настройках" }
        var usedCache = false
        var lastError: Throwable? = null
        val nodes = sources.flatMap { source ->
            val cacheKey = SubscriptionStore.cacheKey(source)
            val text = runCatching {
                if (refresh) SubscriptionClient.fetch(context, source.url, cacheKey)
                else SubscriptionClient.cached(context, cacheKey)
                    ?: SubscriptionClient.fetch(context, source.url, cacheKey)
            }.recoverCatching { error ->
                lastError = error
                if (!allowCachedOnNetworkError || error is SubscriptionAccessException) throw error
                usedCache = true
                SubscriptionClient.cached(context, cacheKey) ?: throw error
            }.getOrNull() ?: return@flatMap emptyList()
            ServerNodeParser.parseSubscription(text, source)
        }.distinctBy(ServerNode::id)
        if (nodes.isEmpty()) throw lastError ?: IllegalArgumentException("Активные подписки не содержат поддерживаемых серверов")
        return SubscriptionLoadResult(nodes, usedCache)
    }

    fun load(context: Context, refresh: Boolean = true): List<ServerNode> =
        loadWithStatus(context, refresh).nodes

    fun selected(context: Context, nodes: List<ServerNode> = load(context, false)): ServerNode {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SELECTED, null)
        return nodes.firstOrNull { it.id == id }
            // Migrate selections saved before server IDs became source-aware.
            ?: nodes.firstOrNull { it.legacyId == id }
            // Preserve a renamed node when the subscription still points to the same endpoint.
            ?: nodes.firstOrNull { id?.startsWith("${it.host}:${it.port}:", ignoreCase = true) == true }
            ?: nodes.first()
    }

    fun select(context: Context, node: ServerNode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SELECTED, node.id).apply()
    }

    fun clear(context: Context) {
        SubscriptionStore.all(context).forEach { SubscriptionClient.clear(context, SubscriptionStore.cacheKey(it)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_SELECTED).apply()
    }

    fun connectionText(context: Context): Pair<ServerNode, String> {
        val nodes = loadWithStatus(context, refresh = true, allowCachedOnNetworkError = true).nodes
        val node = selected(context, nodes)
        return node to node.link
    }

    fun validateSelectedSource(context: Context, node: ServerNode): Boolean {
        val source = SubscriptionStore.all(context)
            .firstOrNull { it.id == node.subscriptionId && it.enabled }
            ?: return false
        val text = SubscriptionClient.fetch(context, source.url, SubscriptionStore.cacheKey(source))
        return ServerNodeParser.parseSubscription(text, source).any { it.id == node.id }
    }

    fun firstShareLink(text: String): String? = text.lineSequence().map(String::trim)
        .firstOrNull { it.contains("://") }
}

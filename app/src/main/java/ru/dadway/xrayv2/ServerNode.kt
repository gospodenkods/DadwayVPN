package ru.dadway.xrayv2

import android.net.Uri
import java.net.IDN

data class ServerNode(
    val id: String,
    val name: String,
    val link: String,
    val host: String,
    val port: Int,
    val country: Country,
    val subscriptionId: String? = null,
    val availability: Availability = Availability.Unknown
)

enum class Country(val displayName: String) {
    RUSSIA("Россия"),
    GERMANY("Германия"),
    USA("США"),
    NETHERLANDS("Нидерланды"),
    UNITED_KINGDOM("Великобритания"),
    UNKNOWN("Сервер")
}

sealed interface Availability {
    data object Unknown : Availability
    data class Available(val latencyMs: Long) : Availability
    data object Unavailable : Availability
}

object ServerNodeParser {
    fun parseSubscription(text: String, subscriptionId: String? = null): List<ServerNode> = text
        .lineSequence()
        .map(String::trim)
        .filter { it.startsWith("vless://", true) || it.startsWith("vmess://", true) ||
            it.startsWith("trojan://", true) || it.startsWith("ss://", true) }
        .mapNotNull(::parse)
        .map { it.copy(subscriptionId = subscriptionId) }
        .distinctBy(ServerNode::id)
        .toList()

    private fun parse(link: String): ServerNode? = runCatching {
        val uri = Uri.parse(link)
        val encodedName = link.substringAfter('#', "")
        val name = (if (encodedName.isBlank()) uri.host.orEmpty() else Uri.decode(encodedName)).trim()
        val host = uri.host?.let(IDN::toASCII).orEmpty()
        val port = if (uri.port > 0) uri.port else defaultPort(uri.scheme)
        require(host.isNotBlank() && port > 0)
        val id = "$host:$port:${name.lowercase()}"
        ServerNode(id, name.ifBlank { host }, link, host, port, countryFromName(name))
    }.getOrNull()

    fun countryFromName(name: String): Country {
        val value = name.lowercase()
        return when {
            listOf("россия", "russia", "ru ", "🇷🇺").any(value::contains) -> Country.RUSSIA
            listOf("германия", "germany", "de ", "🇩🇪").any(value::contains) -> Country.GERMANY
            listOf("сша", "usa", "united states", "us ", "🇺🇸").any(value::contains) -> Country.USA
            listOf("нидерланды", "netherlands", "holland", "nl ", "🇳🇱").any(value::contains) -> Country.NETHERLANDS
            listOf("великобритания", "united kingdom", "great britain", "uk ", "gb ", "🇬🇧").any(value::contains) -> Country.UNITED_KINGDOM
            else -> Country.UNKNOWN
        }
    }

    private fun defaultPort(scheme: String?) = when (scheme?.lowercase()) {
        "trojan" -> 443
        else -> -1
    }
}

package ru.dadway.xrayv2

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object XrayConfigBuilder {
    const val SOCKS_PORT = 10808
    const val METRICS_PORT = 49227
    const val TUN_INTERFACE_NAME = "xray0"
    const val VPN_DNS_SERVER = "1.1.1.1"
    const val LIBXRAY_DNS_ENDPOINT = "1.1.1.1:53"

    data class Built(val json: String, val server: String, val protocol: String)

    fun build(base: JSONObject, tunFd: Int, filesDir: String, sourceLink: String? = null): Built {
        val outbounds = base.optJSONArray("outbounds") ?: JSONArray().also { base.put("outbounds", it) }
        normalizeConvertedOutbounds(outbounds)

        val proxy = findProxyOutbound(outbounds) ?: error("В подписке нет outbound-конфигурации")
        sourceLink
            ?.takeIf { it.startsWith("vless://", ignoreCase = true) }
            ?.let { applyShareLinkHints(proxy, it) }

        normalizeRealityClientSettings(outbounds)
        validateProxyOutbound(proxy)

        val proxyTag = proxy.optString("tag").ifBlank { "proxy" }.also { proxy.put("tag", it) }
        ensureOutbound(outbounds, "direct", "freedom")
        ensureOutbound(outbounds, "block", "blackhole")

        base.put("inbounds", JSONArray()
            .put(JSONObject()
                .put("tag", "tun-in")
                .put("protocol", "tun")
                .put("port", 0)
                .put("settings", JSONObject()
                    // Xray-core 26.7.28 probes net.Interfaces() when name is
                    // empty. Android 16 denies that netlink request. The TUN
                    // fd is supplied by VpnService, so retain the explicit
                    // name used by the previously working core release.
                    .put("name", TUN_INTERFACE_NAME)
                    .put("mtu", 1500)))
            .put(JSONObject()
                .put("tag", "socks-in")
                .put("listen", "127.0.0.1")
                .put("port", SOCKS_PORT)
                .put("protocol", "socks")
                .put("settings", JSONObject().put("udp", true))))

        base.put("env", JSONObject()
            .put("xray.tun.fd", tunFd.toString())
            .put("xray.location.asset", filesDir)
            .put("xray.location.cert", filesDir))

        base.put("dns", JSONObject().put("servers", JSONArray().put(VPN_DNS_SERVER).put("8.8.8.8")))

        base.put("routing", JSONObject()
            .put("domainStrategy", "IPIfNonMatch")
            .put("rules", JSONArray()
                .put(JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray().put("tun-in").put("socks-in"))
                    .put("domain", JSONArray().put("domain:ru").put("domain:by").put("domain:su"))
                    .put("outboundTag", "direct"))
                .put(JSONObject()
                    .put("type", "field")
                    .put("ip", JSONArray()
                        .put("10.0.0.0/8")
                        .put("100.64.0.0/10")
                        .put("127.0.0.0/8")
                        .put("169.254.0.0/16")
                        .put("172.16.0.0/12")
                        .put("192.168.0.0/16")
                        .put("224.0.0.0/4")
                        .put("255.255.255.255/32")
                        .put("::1/128")
                        .put("fc00::/7")
                        .put("fe80::/10")
                        .put("ff00::/8"))
                    .put("outboundTag", "direct"))
                .put(JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray().put("tun-in").put("socks-in"))
                    .put("outboundTag", proxyTag))))

        base.put("metrics", JSONObject().put("listen", "127.0.0.1:$METRICS_PORT"))
        base.put("stats", JSONObject())
        base.put("policy", JSONObject().put("system", JSONObject()
            .put("statsInboundDownlink", true)
            .put("statsInboundUplink", true)
            .put("statsOutboundDownlink", true)
            .put("statsOutboundUplink", true)))
        base.put("log", JSONObject().put("loglevel", "warning"))

        return Built(
            json = base.toString(),
            server = extractServer(proxy).takeUnless { it == proxyTag }
                ?: sourceLink?.let(::extractSourceEndpoint)
                ?: proxyTag,
            protocol = proxy.optString("protocol", "unknown")
        )
    }

    /** Restore parameters that some libXray converters omit from VLESS links. */
    private fun applyShareLinkHints(proxy: JSONObject, link: String) {
        val params = parseQuery(link)
        if (!params.value("security").equals("reality", ignoreCase = true)) return

        val stream = proxy.optJSONObject("streamSettings")
            ?: JSONObject().also { proxy.put("streamSettings", it) }
        stream.put("security", "reality")

        val reality = stream.optJSONObject("realitySettings")
            ?: JSONObject().also { stream.put("realitySettings", it) }

        val sni = sequenceOf(
            params.value("sni"),
            params.value("serverName"),
            reality.optString("serverName").trim().takeIf { it.isNotEmpty() }
        ).filterNotNull().firstOrNull { it.isNotBlank() }
            ?: error("В конфигурации REALITY отсутствует SNI/serverName")

        // Client-side REALITY uses singular serverName. serverNames is a
        // server-side option and must not be sent by an outbound client.
        reality.put("serverName", sni.trim())
        reality.remove("serverNames")
        reality.remove("server_name")

        params.value("fp")?.takeIf { it.isNotBlank() }?.let { reality.put("fingerprint", it) }
        params.value("pbk")?.takeIf { it.isNotBlank() }?.let { reality.put("publicKey", it) }
        params.value("sid")?.let { reality.put("shortId", it) }
        params.value("spx")?.takeIf { it.isNotBlank() }?.let { reality.put("spiderX", it) }
        params.value("pqv")?.takeIf { it.isNotBlank() }?.let { reality.put("mldsa65Verify", it) }

        if (params.value("type").equals("xhttp", ignoreCase = true)) {
            stream.put("network", "xhttp")
            val xhttp = stream.optJSONObject("xhttpSettings")
                ?: JSONObject().also { stream.put("xhttpSettings", it) }
            xhttp.put("path", params.value("path").orEmpty().ifBlank { "/" })
            params.value("mode")?.takeIf { it.isNotBlank() }?.let { xhttp.put("mode", it) }
            params.value("host")?.takeIf { it.isNotBlank() }?.let { xhttp.put("host", it) }
        }
    }

    /** Rebuild REALITY outbound settings using only client-side fields. */
    private fun normalizeRealityClientSettings(outbounds: JSONArray) {
        for (i in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(i) ?: continue
            val stream = outbound.optJSONObject("streamSettings") ?: continue
            if (!stream.optString("security").equals("reality", ignoreCase = true)) continue

            val source = stream.optJSONObject("realitySettings") ?: JSONObject()
            val fromArray = source.optJSONArray("serverNames")?.let { names ->
                (0 until names.length()).asSequence()
                    .map { names.optString(it).trim() }
                    .firstOrNull { it.isNotEmpty() }
            }
            val sni = sequenceOf(
                source.optString("serverName").trim(),
                source.optString("server_name").trim(),
                fromArray.orEmpty()
            ).firstOrNull { it.isNotEmpty() }
                ?: error("В конфигурации REALITY отсутствует SNI/serverName")

            // Do not mutate the converter result in place: libXray may include
            // server-only fields (privateKey, serverNames, shortIds, dest, etc.).
            // Their presence makes Xray parse an outbound as a REALITY server.
            val client = JSONObject()
                .put("serverName", sni)

            copyNonBlank(source, client, "fingerprint")
            copyNonBlank(source, client, "publicKey")
            copyNonBlank(source, client, "shortId")
            copyNonBlank(source, client, "spiderX")
            copyNonBlank(source, client, "mldsa65Verify")

            stream.put("realitySettings", client)
        }
    }

    private fun copyNonBlank(source: JSONObject, target: JSONObject, key: String) {
        val value = source.optString(key).trim()
        if (value.isNotEmpty()) target.put(key, value)
    }

    private fun validateProxyOutbound(proxy: JSONObject) {
        val protocol = proxy.optString("protocol").trim()
        require(protocol.isNotEmpty()) { "У proxy-outbound отсутствует protocol" }

        val stream = proxy.optJSONObject("streamSettings") ?: return
        if (!stream.optString("security").equals("reality", ignoreCase = true)) return

        val reality = stream.optJSONObject("realitySettings")
            ?: error("У REALITY отсутствует realitySettings")
        require(reality.optString("serverName").isNotBlank()) {
            "У REALITY отсутствует serverName"
        }
        require(reality.optString("publicKey").isNotBlank()) {
            "У REALITY отсутствует publicKey/pbk"
        }
    }

    private fun findProxyOutbound(outbounds: JSONArray): JSONObject? {
        for (i in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(i) ?: continue
            val protocol = outbound.optString("protocol")
            if (!protocol.equals("freedom", true) && !protocol.equals("blackhole", true) && protocol.isNotBlank()) {
                return outbound
            }
        }
        return null
    }

    private fun parseQuery(link: String): Map<String, String> {
        val raw = URI(link).rawQuery.orEmpty()
        if (raw.isBlank()) return emptyMap()
        return raw.split('&').mapNotNull { item ->
            val parts = item.split('=', limit = 2)
            val key = decode(parts[0]).trim()
            if (key.isBlank()) null else key to decode(parts.getOrElse(1) { "" })
        }.toMap()
    }

    private fun Map<String, String>.value(key: String): String? =
        entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    /** Remove profile names accidentally written to sendThrough by converters. */
    private fun normalizeConvertedOutbounds(outbounds: JSONArray) {
        for (i in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(i) ?: continue
            val sendThrough = outbound.optString("sendThrough").trim()
            if (sendThrough.isNotEmpty() && !isRuntimeSendThrough(sendThrough)) {
                outbound.remove("sendThrough")
            }
        }
    }

    private fun isRuntimeSendThrough(value: String): Boolean {
        if (value == "origin" || value == "srcip") return true
        if (IPV4.matches(value)) return true
        return value.contains(':') && IPV6_CHARS.matches(value)
    }

    private val IPV4 = Regex("""^(?:\d{1,3}\.){3}\d{1,3}(?:/\d{1,2})?$""")
    private val IPV6_CHARS = Regex("""^[0-9A-Fa-f:]+(?:/\d{1,3})?$""")

    private fun ensureOutbound(a: JSONArray, tag: String, protocol: String) {
        for (i in 0 until a.length()) if (a.optJSONObject(i)?.optString("tag") == tag) return
        a.put(JSONObject().put("tag", tag).put("protocol", protocol).put("settings", JSONObject()))
    }

    private fun extractServer(o: JSONObject): String {
        val vnext = o.optJSONObject("settings")?.optJSONArray("vnext")?.optJSONObject(0)
        if (vnext != null) return "${vnext.optString("address")}:${vnext.optInt("port")}" 
        val servers = o.optJSONObject("settings")?.optJSONArray("servers")?.optJSONObject(0)
        if (servers != null) return "${servers.optString("address")}:${servers.optInt("port")}" 
        return o.optString("tag", "proxy")
    }

    private fun extractSourceEndpoint(link: String): String? = runCatching {
        val uri = URI(link)
        val host = uri.host?.takeIf(String::isNotBlank) ?: return@runCatching null
        val port = uri.port.takeIf { it > 0 } ?: return@runCatching null
        "$host:$port"
    }.getOrNull()
}

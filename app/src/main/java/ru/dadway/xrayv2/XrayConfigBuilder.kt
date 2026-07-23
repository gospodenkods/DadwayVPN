package ru.dadway.xrayv2

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object XrayConfigBuilder {
    const val SOCKS_PORT = 10808
    const val METRICS_PORT = 49227

    data class Built(val json: String, val server: String)

    fun build(base: JSONObject, tunFd: Int, filesDir: String, sourceLink: String? = null): Built {
        val outbounds = base.optJSONArray("outbounds") ?: JSONArray().also { base.put("outbounds", it) }
        normalizeConvertedOutbounds(outbounds)
        sourceLink?.takeIf { it.startsWith("vless://", ignoreCase = true) }?.let { applyShareLinkHints(outbounds, it) }
        normalizeRealityServerNames(outbounds)
        val proxy = findProxyOutbound(outbounds) ?: error("В подписке нет outbound-конфигурации")
        val proxyTag = proxy.optString("tag").ifBlank { "proxy" }.also { proxy.put("tag", it) }
        ensureOutbound(outbounds, "direct", "freedom")
        ensureOutbound(outbounds, "block", "blackhole")

        val inbounds = JSONArray()
            .put(JSONObject()
                .put("tag", "tun-in")
                .put("protocol", "tun")
                .put("port", 0)
                .put("settings", JSONObject().put("mtu", 1500)))
            .put(JSONObject()
                .put("tag", "socks-in")
                .put("listen", "127.0.0.1")
                .put("port", SOCKS_PORT)
                .put("protocol", "socks")
                .put("settings", JSONObject().put("udp", true)))
        base.put("inbounds", inbounds)

        base.put("env", JSONObject()
            .put("xray.tun.fd", tunFd.toString())
            .put("xray.location.asset", filesDir)
            .put("xray.location.cert", filesDir))

        base.put("dns", JSONObject()
            .put("servers", JSONArray()
                .put("1.1.1.1")
                .put("8.8.8.8")))

        base.put("routing", JSONObject()
            .put("domainStrategy", "IPIfNonMatch")
            .put("rules", JSONArray()
                .put(JSONObject().put("type", "field").put("inboundTag", JSONArray().put("tun-in").put("socks-in"))
                    .put("domain", JSONArray().put("domain:ru").put("domain:by").put("domain:su"))
                    .put("outboundTag", "direct"))
                .put(JSONObject().put("type", "field")
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
                .put(JSONObject().put("type", "field").put("inboundTag", JSONArray().put("tun-in").put("socks-in")).put("outboundTag", proxyTag))))

        base.put("metrics", JSONObject().put("listen", "127.0.0.1:$METRICS_PORT"))
        base.put("stats", JSONObject())
        base.put("policy", JSONObject().put("system", JSONObject()
            .put("statsInboundDownlink", true).put("statsInboundUplink", true)
            .put("statsOutboundDownlink", true).put("statsOutboundUplink", true)))
        base.put("log", JSONObject().put("loglevel", "warning"))

        return Built(base.toString(), extractServer(proxy))
    }


    /**
     * Some libXray builds do not copy all REALITY/XHTTP query parameters from
     * a VLESS share link. Restore the runtime-critical values directly from
     * the original link before Xray validates the JSON configuration.
     */
    private fun applyShareLinkHints(outbounds: JSONArray, link: String) {
        val proxy = findProxyOutbound(outbounds) ?: return
        val params = parseQuery(link)
        val security = params["security"].orEmpty()
        if (!security.equals("reality", ignoreCase = true)) return

        val stream = proxy.optJSONObject("streamSettings")
            ?: JSONObject().also { proxy.put("streamSettings", it) }
        stream.put("security", "reality")

        val reality = stream.optJSONObject("realitySettings")
            ?: JSONObject().also { stream.put("realitySettings", it) }
        val sni = sequenceOf(
            params["sni"],
            params["serverName"],
            reality.optString("serverName").takeIf { it.isNotBlank() }
        ).filterNotNull().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?: error("В конфигурации REALITY отсутствует SNI/serverName")
        // Different Xray/libXray generations accept different client-side
        // spellings. Keep both forms populated and never leave an empty array.
        reality.put("serverName", sni)
        reality.put("serverNames", JSONArray().put(sni))

        params["fp"]?.takeIf { it.isNotBlank() }?.let { reality.put("fingerprint", it) }
        params["pbk"]?.takeIf { it.isNotBlank() }?.let { reality.put("publicKey", it) }
        params["sid"]?.let { reality.put("shortId", it) }
        params["spx"]?.takeIf { it.isNotBlank() }?.let { reality.put("spiderX", it) }
        params["pqv"]?.takeIf { it.isNotBlank() }?.let {
            // Current Xray/libXray builds may use either key.
            reality.put("mldsa65Verify", it)
        }

        if (params["type"].equals("xhttp", ignoreCase = true)) {
            stream.put("network", "xhttp")
            val xhttp = stream.optJSONObject("xhttpSettings")
                ?: JSONObject().also { stream.put("xhttpSettings", it) }
            xhttp.put("path", params["path"].orEmpty().ifBlank { "/" })
            params["mode"]?.takeIf { it.isNotBlank() }?.let { xhttp.put("mode", it) }
            params["host"]?.takeIf { it.isNotBlank() }?.let { xhttp.put("host", it) }
        }
    }


    /**
     * libXray 26.x may emit an empty `serverNames` array even when
     * `serverName` is present. Xray then validates the empty array first and
     * aborts with `empty "serverNames"`. Synchronise both representations for
     * every REALITY outbound before the config is handed to Xray-core.
     */
    private fun normalizeRealityServerNames(outbounds: JSONArray) {
        for (i in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(i) ?: continue
            val stream = outbound.optJSONObject("streamSettings") ?: continue
            if (!stream.optString("security").equals("reality", ignoreCase = true)) continue

            val reality = stream.optJSONObject("realitySettings")
                ?: JSONObject().also { stream.put("realitySettings", it) }
            val fromArray = reality.optJSONArray("serverNames")
                ?.let { names ->
                    (0 until names.length())
                        .asSequence()
                        .map { names.optString(it).trim() }
                        .firstOrNull { it.isNotEmpty() }
                }
            val sni = sequenceOf(
                reality.optString("serverName").trim(),
                reality.optString("server_name").trim(),
                fromArray.orEmpty()
            ).firstOrNull { it.isNotEmpty() }
                ?: error("В конфигурации REALITY отсутствует SNI/serverName")

            reality.put("serverName", sni)
            reality.put("serverNames", JSONArray().put(sni))
            reality.remove("server_name")
        }
    }

    private fun findProxyOutbound(outbounds: JSONArray): JSONObject? {
        for (i in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(i) ?: continue
            val protocol = outbound.optString("protocol")
            if (!protocol.equals("freedom", true) && !protocol.equals("blackhole", true)) {
                return outbound
            }
        }
        return outbounds.optJSONObject(0)
    }

    private fun parseQuery(link: String): Map<String, String> {
        val raw = URI(link).rawQuery.orEmpty()
        if (raw.isBlank()) return emptyMap()
        return raw.split('&').mapNotNull { item ->
            val parts = item.split('=', limit = 2)
            val key = decode(parts[0])
            if (key.isBlank()) null else key to decode(parts.getOrElse(1) { "" })
        }.toMap()
    }

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    /**
     * libXray uses sendThrough as temporary storage for the profile name when
     * converting share links. Xray-core itself interprets sendThrough as a
     * local source IP/interface. A profile name such as "server-app" is not a
     * valid source address, so it must not reach the runtime configuration.
     */
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
}

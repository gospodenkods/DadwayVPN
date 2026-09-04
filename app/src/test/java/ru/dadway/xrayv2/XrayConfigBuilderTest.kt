package ru.dadway.xrayv2

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class XrayConfigBuilderTest {
    @Test
    fun suppliesExplicitTunNameForAndroidVpnFileDescriptor() {
        val base = JSONObject().put(
            "outbounds",
            JSONArray().put(
                JSONObject()
                    .put("tag", "proxy")
                    .put("protocol", "vless")
                    .put("settings", JSONObject()),
            ),
        )

        val built = XrayConfigBuilder.build(
            base = base,
            tunFd = 42,
            filesDir = "/data/user/0/ru.dadway.xrayv2/files",
        )
        val config = JSONObject(built.json)
        val tunInbound = config.getJSONArray("inbounds").getJSONObject(0)

        assertEquals("tun", tunInbound.getString("protocol"))
        assertEquals(XrayConfigBuilder.TUN_INTERFACE_NAME, tunInbound.getJSONObject("settings").getString("name"))
        assertEquals("42", config.getJSONObject("env").getString("xray.tun.fd"))
    }

    @Test
    fun replacesChromeRealityFingerprintWithMobileCompatibleSafari() {
        val proxy = JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(
                JSONObject()
                    .put("address", "example.com")
                    .put("port", 443)
                    .put("users", JSONArray().put(JSONObject().put("id", "00000000-0000-0000-0000-000000000000"))),
            )))
            .put("streamSettings", JSONObject()
                .put("network", "xhttp")
                .put("security", "reality")
                .put("realitySettings", JSONObject()
                    .put("serverName", "www.ebay.de")
                    .put("fingerprint", "chrome")
                    .put("publicKey", "public-key")))
        val base = JSONObject().put("outbounds", JSONArray().put(proxy))

        val built = XrayConfigBuilder.build(base, 42, "/tmp")
        val reality = JSONObject(built.json)
            .getJSONArray("outbounds")
            .getJSONObject(0)
            .getJSONObject("streamSettings")
            .getJSONObject("realitySettings")

        assertEquals(XrayConfigBuilder.REALITY_FINGERPRINT, reality.getString("fingerprint"))
    }
}

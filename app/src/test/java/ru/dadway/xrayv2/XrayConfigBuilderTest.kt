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
}

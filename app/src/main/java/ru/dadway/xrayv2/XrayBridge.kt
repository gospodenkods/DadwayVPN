package ru.dadway.xrayv2

import libXray.DialerController
import libXray.LibXray
import org.json.JSONObject

object XrayBridge {
    data class Response(val success: Boolean, val data: Any?, val error: String)

    private val lifecycle = XrayLifecycle<DialerController>(
        setDns = { controller ->
            LibXray.setDNS(controller, XrayConfigBuilder.LIBXRAY_DNS_ENDPOINT)
        },
        resetDns = LibXray::resetDNS,
        startCore = { configJson ->
            val response = invoke(
                "runXrayFromJson",
                JSONObject().put("configJSON", configJson),
            )
            check(response.success) { response.error }
        },
        stopCore = {
            val response = invoke("stopXray")
            check(response.success) { response.error }
        },
    )

    fun invoke(method: String, payload: JSONObject = JSONObject()): Response {
        val request = JSONObject().put("apiVersion", 1).put("method", method).put("payload", payload)
        val raw = LibXray.invoke(request.toString())
        val json = JSONObject(raw)
        return Response(json.optBoolean("success"), json.opt("data"), json.optString("error"))
    }

    fun linksToConfig(links: String): JSONObject {
        val r = invoke("convertShareLinksToXrayJson", JSONObject().put("text", links))
        check(r.success) { r.error }
        val configText = when (val d = r.data) {
            is String -> d
            is JSONObject -> d.toString()
            else -> d?.toString() ?: error("libXray вернул пустую конфигурацию")
        }
        return JSONObject(configText)
    }

    fun run(configJson: String, controller: DialerController) = lifecycle.start(configJson, controller)

    fun stop() = lifecycle.stop()
    fun isRunning(): Boolean = lifecycle.isRunning()
    fun version(): String = invoke("xrayVersion").data?.toString() ?: "unknown"
}

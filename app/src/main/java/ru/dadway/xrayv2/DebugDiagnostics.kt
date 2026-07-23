package ru.dadway.xrayv2

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Diagnostic capture for the 7.5 Debug build. Raw configs stay in app-private storage. */
object DebugDiagnostics {
    private const val BASE_RAW = "dadway-xray-base-raw.json"
    private const val FINAL_RAW = "dadway-xray-final-raw.json"
    private const val BASE_SAFE = "dadway-xray-base-redacted.json"
    private const val FINAL_SAFE = "dadway-xray-final-redacted.json"

    fun captureBase(context: Context, base: JSONObject, sourceLink: String?) {
        write(context, BASE_RAW, base.toString(2))
        write(context, BASE_SAFE, prettyJson(redact(base)))
        LogStore.add(context, "DEBUG 7.5: конвертер вернул JSON (${base.toString().length} символов)")
        LogStore.add(context, "DEBUG 7.5: исходная ссылка: ${describeLink(sourceLink)}")
        logRealitySummary(context, "после конвертера", base)
    }

    fun captureFinal(context: Context, finalJson: String) {
        val json = JSONObject(finalJson)
        write(context, FINAL_RAW, json.toString(2))
        val safe = redact(json)
        write(context, FINAL_SAFE, prettyJson(safe))
        LogStore.add(context, "DEBUG 7.5: итоговый JSON сохранён во внутреннее хранилище (${finalJson.length} символов)")
        logRealitySummary(context, "перед runXrayFromJson", json)
        LogStore.add(context, "DEBUG 7.5: итоговый JSON с удалёнными секретами:\n${prettyJson(safe)}")
    }

    fun exportText(context: Context): String = buildString {
        appendLine("Dadway VPN 7.5 Debug diagnostic export")
        appendLine("ВНИМАНИЕ: секреты UUID/ключи/пароли удалены.")
        appendLine()
        appendLine("========== APPLICATION LOG ==========")
        append(LogStore.read(context))
        appendLine()
        appendLine("========== BASE CONFIG (REDACTED) ==========")
        append(read(context, BASE_SAFE))
        appendLine()
        appendLine("========== FINAL CONFIG (REDACTED) ==========")
        append(read(context, FINAL_SAFE))
    }

    private fun logRealitySummary(context: Context, stage: String, root: JSONObject) {
        val outbounds = root.optJSONArray("outbounds")
        if (outbounds == null) {
            LogStore.add(context, "DEBUG 7.5 [$stage]: outbounds отсутствует")
            return
        }
        LogStore.add(context, "DEBUG 7.5 [$stage]: outbounds=${outbounds.length()}")
        for (i in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(i) ?: continue
            val stream = outbound.optJSONObject("streamSettings")
            val security = stream?.optString("security").orEmpty()
            if (!security.equals("reality", true)) continue
            val reality = stream?.optJSONObject("realitySettings")
            val serverName = reality?.optString("serverName").orEmpty()
            val namesAny = reality?.opt("serverNames")
            val namesDescription = when (namesAny) {
                null, JSONObject.NULL -> "отсутствует"
                is JSONArray -> "массив(length=${namesAny.length()}, first=${namesAny.optString(0)})"
                else -> "${namesAny::class.java.simpleName}=$namesAny"
            }
            val keys = reality?.keys()?.asSequence()?.toList()?.sorted()?.joinToString(",") ?: "—"
            LogStore.add(
                context,
                "DEBUG 7.5 [$stage]: outbound[$i] tag=${outbound.optString("tag")}, " +
                    "network=${stream?.optString("network")}, serverName='$serverName', " +
                    "serverNames=$namesDescription, realityKeys=[$keys], " +
                    "publicKeyPresent=${!reality?.optString("publicKey").isNullOrBlank()}"
            )
        }
    }

    private fun describeLink(link: String?): String {
        if (link.isNullOrBlank()) return "не найдена"
        return runCatching {
            val scheme = link.substringBefore("://", "unknown")
            val query = link.substringAfter('?', "").substringBefore('#')
            val keys = query.split('&').mapNotNull { it.substringBefore('=').takeIf(String::isNotBlank) }
            "scheme=$scheme, queryKeys=${keys.joinToString(",")}, length=${link.length}"
        }.getOrElse { "не удалось разобрать, length=${link.length}" }
    }

    private fun redact(value: Any?): Any? = when (value) {
        is JSONObject -> JSONObject().also { target ->
            value.keys().forEach { key ->
                val raw = value.opt(key)
                target.put(key, if (isSecretKey(key)) redactSecret(raw) else redact(raw))
            }
        }
        is JSONArray -> JSONArray().also { target ->
            for (i in 0 until value.length()) target.put(redact(value.opt(i)))
        }
        else -> value
    }

    private fun isSecretKey(key: String): Boolean = key.lowercase() in setOf(
        "id", "uuid", "password", "pass", "privatekey", "publickey",
        "pbk", "shortid", "sid", "mldsa65verify", "token", "authorization"
    )

    private fun redactSecret(value: Any?): Any = when (value) {
        null, JSONObject.NULL -> JSONObject.NULL
        is JSONArray -> JSONArray().also { target -> repeat(value.length()) { target.put("<redacted>") } }
        else -> "<redacted>"
    }

    private fun prettyJson(value: Any?): String = when (value) {
        is JSONObject -> value.toString(2)
        is JSONArray -> value.toString(2)
        null, JSONObject.NULL -> "null"
        else -> value.toString()
    }

    private fun write(context: Context, name: String, text: String) {
        File(context.filesDir, name).writeText(text, Charsets.UTF_8)
    }

    private fun read(context: Context, name: String): String {
        val file = File(context.filesDir, name)
        return if (file.exists()) file.readText(Charsets.UTF_8) else "Файл ещё не создан.\n"
    }
}

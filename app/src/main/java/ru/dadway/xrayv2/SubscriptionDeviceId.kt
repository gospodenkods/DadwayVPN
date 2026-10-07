package ru.dadway.xrayv2

import android.content.Context
import android.provider.Settings
import java.io.File
import java.security.MessageDigest
import java.util.UUID

internal object SubscriptionDeviceId {
    @Synchronized
    fun get(context: Context): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
        val seed = usableAndroidId(androidId) ?: run {
            // A fallback installation ID must not be restored onto another device.
            val file = File(context.noBackupFilesDir, "subscription-device-id")
            runCatching { file.readText().trim().also { UUID.fromString(it) } }.getOrNull()
                ?: UUID.randomUUID().toString().also { file.writeText(it) }
        }
        return derive(context.packageName, seed)
    }

    internal fun usableAndroidId(value: String?): String? = value?.trim()?.lowercase()
        ?.takeIf { it.matches(Regex("[0-9a-f]{1,16}")) && it.any { char -> char != '0' } && it != "9774d56d682e549c" }

    internal fun derive(packageName: String, seed: String): String = MessageDigest.getInstance("SHA-256")
        .digest("DadwayVPN-HWID-v1:$packageName:$seed".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

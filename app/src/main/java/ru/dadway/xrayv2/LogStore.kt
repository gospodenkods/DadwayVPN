package ru.dadway.xrayv2

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogStore {
    private val lock = Any()
    private const val MAX_BYTES = 2_000_000L

    fun file(context: Context): File = File(context.filesDir, "dadway-vpn.log")

    fun add(context: Context, message: String) {
        synchronized(lock) {
            val logFile = file(context)
            if (logFile.exists() && logFile.length() > MAX_BYTES) {
                logFile.writeText("")
            }
            val timestamp = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                Locale.US
            ).format(Date())
            logFile.appendText("[$timestamp] $message\n", Charsets.UTF_8)
        }
    }

    fun read(context: Context): String = synchronized(lock) {
        val logFile = file(context)
        if (logFile.exists()) {
            logFile.readText(Charsets.UTF_8)
        } else {
            "Лог пока пуст.\n"
        }
    }
}

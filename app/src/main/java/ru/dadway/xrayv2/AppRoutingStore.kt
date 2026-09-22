package ru.dadway.xrayv2

import android.content.Context

enum class AppRoutingMode { ALL, SELECTED }

data class AppRoutingSettings(
    val mode: AppRoutingMode = AppRoutingMode.ALL,
    val packages: Set<String> = emptySet(),
)

object AppRoutingStore {
    private const val PREFS = "dadway_app_routing"
    private const val KEY_MODE = "mode"
    private const val KEY_PACKAGES = "packages"

    fun read(context: Context): AppRoutingSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mode = runCatching {
            AppRoutingMode.valueOf(prefs.getString(KEY_MODE, AppRoutingMode.ALL.name).orEmpty())
        }.getOrDefault(AppRoutingMode.ALL)
        return AppRoutingSettings(
            mode = mode,
            packages = prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty().toSet(),
        )
    }

    fun save(context: Context, mode: AppRoutingMode, packages: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, mode.name)
            .putStringSet(KEY_PACKAGES, packages.toSet())
            .apply()
    }

    fun summary(context: Context): String = read(context).let { settings ->
        if (settings.mode == AppRoutingMode.ALL) "Все приложения"
        else "Выбрано: ${settings.packages.size}"
    }
}

object AppRoutingPolicy {
    fun selectedPackages(settings: AppRoutingSettings, ownPackage: String): List<String> =
        settings.packages.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filterNot { it == ownPackage }
            .distinct()
            .sorted()
            .toList()
}

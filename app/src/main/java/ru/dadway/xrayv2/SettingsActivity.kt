package ru.dadway.xrayv2

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class SettingsActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_REFRESH_SERVERS = "refresh_servers"
        private const val TELEGRAM_SOCKS_URI = "tg://socks?server=127.0.0.1&port=10808"
        private const val TELEGRAM_SOCKS_WEB = "https://t.me/socks?server=127.0.0.1&port=10808"
    }

    private var refreshRequested = false
    private lateinit var routingSummary: TextView
    private lateinit var proxyStatus: TextView
    private lateinit var telegramProxyButton: MaterialButton
    private val stateListener: (UiState) -> Unit = { state ->
        runOnUiThread { renderProxyState(state.running) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        applySystemInsets()
        refreshRequested = savedInstanceState?.getBoolean(EXTRA_REFRESH_SERVERS) ?: false
        routingSummary = findViewById(R.id.routingSummary)
        proxyStatus = findViewById(R.id.proxyStatus)
        telegramProxyButton = findViewById(R.id.telegramProxyButton)

        findViewById<ImageButton>(R.id.settingsBackButton).setOnClickListener { finish() }
        findViewById<View>(R.id.subscriptionsSettingsRow).setOnClickListener {
            SubscriptionDialogs.show(this) { requestServerRefresh() }
        }
        findViewById<View>(R.id.appRoutingSettingsRow).setOnClickListener {
            startActivity(Intent(this, AppRoutingActivity::class.java))
        }
        findViewById<View>(R.id.refreshServersSettingsRow).setOnClickListener {
            requestServerRefresh()
            Toast.makeText(this, "Обновление серверов запущено", Toast.LENGTH_SHORT).show()
            finish()
        }
        telegramProxyButton.setOnClickListener { openTelegramProxy() }
        findViewById<ImageButton>(R.id.copyProxyButton).setOnClickListener { copyProxyLink() }
        findViewById<View>(R.id.themeSettingsRow).setOnClickListener { showThemeSettings() }
    }

    override fun onStart() {
        super.onStart()
        AppState.observe(stateListener)
    }

    override fun onStop() {
        AppState.remove(stateListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        routingSummary.text = AppRoutingStore.summary(this)
        renderProxyState(AppState.current.running)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(EXTRA_REFRESH_SERVERS, refreshRequested)
        super.onSaveInstanceState(outState)
    }

    override fun finish() {
        if (refreshRequested) {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_REFRESH_SERVERS, true))
        }
        super.finish()
    }

    private fun requestServerRefresh() {
        refreshRequested = true
        setResult(RESULT_OK, Intent().putExtra(EXTRA_REFRESH_SERVERS, true))
    }

    private fun renderProxyState(running: Boolean) {
        proxyStatus.text = if (running) {
            "●  Доступен при подключённом VPN"
        } else {
            "●  Запустите VPN для подключения"
        }
        proxyStatus.setTextColor(
            ContextCompat.getColor(this, if (running) R.color.dadway_success else R.color.dadway_text_secondary)
        )
        telegramProxyButton.alpha = if (running) 1f else 0.72f
    }

    private fun openTelegramProxy() {
        if (!AppState.current.running) {
            Toast.makeText(this, "Сначала подключите VPN", Toast.LENGTH_LONG).show()
            return
        }
        val direct = Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_SOCKS_URI))
        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_SOCKS_WEB))
        runCatching {
            if (direct.resolveActivity(packageManager) != null) startActivity(direct) else startActivity(fallback)
        }.onFailure {
            Toast.makeText(this, "Не удалось открыть Telegram", Toast.LENGTH_LONG).show()
        }
    }

    private fun copyProxyLink() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Dadway VPN SOCKS5", TELEGRAM_SOCKS_URI))
        Toast.makeText(this, "Ссылка SOCKS5 скопирована", Toast.LENGTH_SHORT).show()
    }

    private fun showThemeSettings() {
        val labels = arrayOf("Системная тема", "Светлая тема", "Тёмная тема")
        val modes = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES,
        )
        val prefs = getSharedPreferences("dadway_ui", MODE_PRIVATE)
        val current = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AlertDialog.Builder(this)
            .setTitle("Тема оформления")
            .setSingleChoiceItems(labels, modes.indexOf(current).coerceAtLeast(0)) { dialog, which ->
                prefs.edit().putInt("theme_mode", modes[which]).apply()
                dialog.dismiss()
                AppCompatDelegate.setDefaultNightMode(modes[which])
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun applySavedTheme() {
        val mode = getSharedPreferences("dadway_ui", MODE_PRIVATE)
            .getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        if (AppCompatDelegate.getDefaultNightMode() != mode) AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun applySystemInsets() {
        findViewById<View>(R.id.settingsRoot).applySafeDrawingInsets()
    }
}

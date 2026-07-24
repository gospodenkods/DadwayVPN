package ru.dadway.xrayv2

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var status: TextView
    private lateinit var server: TextView
    private lateinit var ip: TextView
    private lateinit var ping: TextView
    private lateinit var down: TextView
    private lateinit var up: TextView
    private lateinit var total: TextView
    private lateinit var connect: ImageButton
    private lateinit var statusIcon: ImageView
    private lateinit var profileRussia: View
    private lateinit var profileUsa: View
    private lateinit var profileNetherlands: View
    private lateinit var connectCaption: TextView
    private val listener: (UiState) -> Unit = { runOnUiThread { render(it) } }

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) startService()
    }
    private val saveLog = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer -> writer.write("Dadway VPN 8.2 log export\n\n" + LogStore.read(this)) }
        }.onSuccess { toast("Лог сохранён") }.onFailure { toast("Ошибка: ${it.message}") }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.statusText)
        server = findViewById(R.id.serverText)
        ip = findViewById(R.id.ipText)
        ping = findViewById(R.id.pingText)
        down = findViewById(R.id.downloadText)
        up = findViewById(R.id.uploadText)
        total = findViewById(R.id.totalText)
        connect = findViewById(R.id.connectButton)
        statusIcon = findViewById(R.id.statusIcon)
        profileRussia = findViewById(R.id.profileRussia)
        profileUsa = findViewById(R.id.profileUsa)
        profileNetherlands = findViewById(R.id.profileNetherlands)
        connectCaption = findViewById(R.id.connectCaption)
        setupProfileCards()

        findViewById<TextView>(R.id.websiteLink).setOnClickListener {
            openExternal("https://dadway.ru")
        }
        findViewById<TextView>(R.id.telegramLink).setOnClickListener {
            openExternal("https://t.me/gds_technical")
        }

        connect.setOnClickListener { if (AppState.current.running) stopService() else requestVpn() }
        findViewById<MaterialButton>(R.id.updateButton).setOnClickListener { updateSubscription() }
        findViewById<MaterialButton>(R.id.testButton).setOnClickListener { testConnection() }
        findViewById<MaterialButton>(R.id.ipButton).setOnClickListener { testConnection() }
        findViewById<MaterialButton>(R.id.saveLogsButton).setOnClickListener {
            saveLog.launch("dadway-vpn-8.2-${System.currentTimeMillis()}.txt")
        }
        findViewById<MaterialButton>(R.id.settingsButton).setOnClickListener { showSettings() }

        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        AppState.observe(listener)
    }

    override fun onDestroy() {
        AppState.remove(listener)
        scope.cancel()
        super.onDestroy()
    }

    private fun openExternal(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            toast("Не удалось открыть ссылку")
        }
    }

    private fun setupProfileCards() {
        profileRussia.setOnClickListener { selectProfile(ConnectionProfiles.DEFAULT_ID) }
        profileUsa.setOnClickListener { selectProfile(ConnectionProfiles.RESERVE_ID) }
        profileNetherlands.setOnClickListener { selectProfile(ConnectionProfiles.NETHERLANDS_ID) }
        updateProfileCards()
    }

    private fun selectProfile(id: String) {
        if (AppState.current.running) {
            toast("Отключите VPN для смены сервера")
            return
        }
        ConnectionProfiles.select(this, id)
        server.text = ConnectionProfiles.byId(id).title
        updateProfileCards()
    }

    private fun updateProfileCards() {
        val selectedId = ConnectionProfiles.selected(this).id
        profileRussia.setBackgroundResource(if (selectedId == ConnectionProfiles.DEFAULT_ID) R.drawable.card_selected else R.drawable.card_unselected)
        profileUsa.setBackgroundResource(if (selectedId == ConnectionProfiles.RESERVE_ID) R.drawable.card_selected else R.drawable.card_unselected)
        profileNetherlands.setBackgroundResource(if (selectedId == ConnectionProfiles.NETHERLANDS_ID) R.drawable.card_selected else R.drawable.card_unselected)
    }

    private fun requestVpn() {
        VpnService.prepare(this)?.let(vpnPermission::launch) ?: startService()
    }

    private fun startService() = ContextCompat.startForegroundService(
        this,
        Intent(this, DadwayVpnService::class.java).setAction(DadwayVpnService.ACTION_START)
    )

    private fun stopService() = startService(
        Intent(this, DadwayVpnService::class.java).setAction(DadwayVpnService.ACTION_STOP)
    )

    private fun updateSubscription() = scope.launch {
        toast("Обновление подписки…")
        val profile = ConnectionProfiles.selected(this@MainActivity)
        runCatching {
            withContext(Dispatchers.IO) {
                when (val source = profile.source) {
                    is ConnectionProfile.Source.Subscription ->
                        SubscriptionClient.fetch(
                            context = this@MainActivity,
                            subscriptionUrl = source.url,
                            cacheKey = source.cacheKey
                        )
                }
            }
        }
            .onSuccess {
                LogStore.add(this@MainActivity, "Подписка обновлена вручную")
                toast("Подписка обновлена")
            }
            .onFailure {
                LogStore.add(this@MainActivity, "Ошибка подписки: ${it.message}")
                toast("Ошибка: ${it.message}")
            }
    }

    private fun testConnection() = scope.launch {
        if (!AppState.current.running) {
            toast("Сначала подключите VPN")
            return@launch
        }
        ping.text = "Тест…"
        runCatching { withContext(Dispatchers.IO) { ConnectionTester.test() } }
            .onSuccess { result ->
                AppState.update {
                    it.copy(
                        externalIp = result.ip,
                        pingMs = result.pingMs,
                        downBps = result.bytesPerSecond
                    )
                }
                LogStore.add(
                    this@MainActivity,
                    "Тест: IP=${result.ip}, ping=${result.pingMs} мс, speed=${result.bytesPerSecond} B/s"
                )
            }
            .onFailure {
                ping.text = "Ошибка"
                toast("Тест не выполнен: ${it.message}")
            }
    }

    private fun showSettings() {
        AlertDialog.Builder(this)
            .setTitle("Настройки Dadway VPN")
            .setMessage(
                "Подписка обновляется автоматически при подключении.\n\n" +
                    "Маршрутизация напрямую: .ru, .by, .su и локальные сети.\n\n" +
                    "Выбранная конфигурация: ${ConnectionProfiles.selected(this).title}\n\n" +
                    "Текущий сервер: ${AppState.current.server}"
            )
            .setPositiveButton("Обновить подписку") { _, _ -> updateSubscription() }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun render(state: UiState) {
        status.text = state.status
        server.text = if (!state.running && state.server == "—") {
            ConnectionProfiles.selected(this).title
        } else {
            state.server
        }
        ip.text = state.externalIp
        ping.text = state.pingMs?.let { "$it мс" } ?: "—"
        down.text = "↓ ${formatRate(state.downBps)}"
        up.text = "↑ ${formatRate(state.upBps)}"
        total.text = "↓ ${formatBytes(state.totalDown)}\n↑ ${formatBytes(state.totalUp)}"

        val profileEnabled = !state.running && !state.status.contains("Подключ", ignoreCase = true)
        profileRussia.isEnabled = profileEnabled
        profileUsa.isEnabled = profileEnabled
        profileNetherlands.isEnabled = profileEnabled
        updateProfileCards()

        if (state.running) {
            connect.setBackgroundResource(R.drawable.btn_disconnect_selector)
            connect.contentDescription = getString(R.string.disconnect)
            statusIcon.setImageResource(R.drawable.status_connected)
            connectCaption.text = "ОТКЛЮЧИТЬ" 
            status.setTextColor(ContextCompat.getColor(this, R.color.dadway_success))
        } else if (state.status.contains("Подключ", ignoreCase = true) && !state.status.equals("Подключено", ignoreCase = true)) {
            connect.setBackgroundResource(R.drawable.connecting_ring)
            connect.contentDescription = getString(R.string.connecting)
            statusIcon.setImageResource(R.drawable.status_connecting)
            connectCaption.text = "ПОДКЛЮЧЕНИЕ…"
            status.setTextColor(ContextCompat.getColor(this, R.color.dadway_warning))
        } else {
            connect.setBackgroundResource(R.drawable.btn_connect_selector)
            connect.contentDescription = getString(R.string.connect)
            statusIcon.setImageResource(R.drawable.status_disconnected)
            connectCaption.text = "ПОДКЛЮЧИТЬСЯ"
            status.setTextColor(ContextCompat.getColor(this, R.color.dadway_danger))
        }
    }

    private fun formatRate(value: Long) = "${formatBytes(value)}/с"

    private fun formatBytes(value: Long): String = when {
        value >= 1_073_741_824 -> String.format(Locale.US, "%.2f ГБ", value / 1_073_741_824.0)
        value >= 1_048_576 -> String.format(Locale.US, "%.2f МБ", value / 1_048_576.0)
        value >= 1024 -> String.format(Locale.US, "%.1f КБ", value / 1024.0)
        else -> "$value Б"
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}

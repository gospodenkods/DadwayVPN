package ru.dadway.xrayv2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
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
    private lateinit var connectCaption: TextView
    private lateinit var selectedCard: View
    private lateinit var selectedFlag: ImageView
    private lateinit var selectedName: TextView
    private lateinit var selectedSource: TextView
    private lateinit var selectedStatus: TextView
    private var nodes: List<ServerNode> = emptyList()
    private var serverListStatus = "Загрузка серверов…"
    private var serverSheet: BottomSheetDialog? = null
    private var autoTestJob: Job? = null
    private var wasRunning = false
    private val listener: (UiState) -> Unit = { runOnUiThread { render(it) } }

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) startVpnService()
    }
    private val saveLog = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(logExportText())
            }
        }.onSuccess { toast("Лог сохранён") }.onFailure { toast("Ошибка: ${it.message}") }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) requestVpnPermission() else showNotificationRequiredDialog()
    }
    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.data?.getBooleanExtra(SettingsActivity.EXTRA_REFRESH_SERVERS, false) == true) {
            refreshServers(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applySystemInsets()
        bindViews()
        findViewById<TextView>(R.id.versionText).text = "Версия ${BuildConfig.VERSION_NAME}"

        selectedCard.setOnClickListener { showServerSheet() }
        findViewById<View>(R.id.refreshServersButton).setOnClickListener { refreshServers(true) }
        findViewById<TextView>(R.id.websiteLink).setOnClickListener { openExternal("https://dadway.ru") }
        findViewById<TextView>(R.id.telegramLink).setOnClickListener { openExternal("https://t.me/gds_technical") }
        findViewById<TextView>(R.id.projectHelpLink).setOnClickListener {
            openExternal("https://pay.cloudtips.ru/p/19a29f12")
        }
        findViewById<MaterialButton>(R.id.buyTelegramButton).setOnClickListener {
            openExternal("https://t.me/vpnfactorytrue_bot")
        }
        findViewById<MaterialButton>(R.id.buyWebsiteButton).setOnClickListener {
            openExternal("https://dadway.top")
        }
        connect.setOnClickListener { if (AppState.current.running) stopVpnService() else requestVpn() }
        findViewById<MaterialButton>(R.id.testButton).setOnClickListener { testConnection() }
        findViewById<MaterialButton>(R.id.ipButton).setOnClickListener { testConnection() }
        findViewById<MaterialButton>(R.id.saveLogsButton).setOnClickListener {
            val version = BuildConfig.VERSION_NAME.replace(Regex("[^A-Za-z0-9._-]"), "_")
            saveLog.launch("dadway-vpn-$version-${System.currentTimeMillis()}.txt")
        }
        findViewById<MaterialButton>(R.id.settingsButton).setOnClickListener {
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
        }

        AppState.observe(listener)
        refreshServers(false)
    }

    private fun applySavedTheme() {
        val mode = getSharedPreferences("dadway_ui", MODE_PRIVATE)
            .getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        if (AppCompatDelegate.getDefaultNightMode() != mode) AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun bindViews() {
        status = findViewById(R.id.statusText)
        server = findViewById(R.id.serverText)
        ip = findViewById(R.id.ipText)
        ping = findViewById(R.id.pingText)
        down = findViewById(R.id.downloadText)
        up = findViewById(R.id.uploadText)
        total = findViewById(R.id.totalText)
        connect = findViewById(R.id.connectButton)
        statusIcon = findViewById(R.id.statusIcon)
        connectCaption = findViewById(R.id.connectCaption)
        selectedCard = findViewById(R.id.selectedServerCard)
        selectedFlag = findViewById(R.id.selectedServerFlag)
        selectedName = findViewById(R.id.selectedServerName)
        selectedSource = findViewById(R.id.selectedServerSource)
        selectedStatus = findViewById(R.id.selectedServerStatus)
    }

    private fun applySystemInsets() {
        val root = findViewById<View>(R.id.rootScroll)
        root.applySafeDrawingInsets()
    }

    override fun onDestroy() {
        serverSheet?.dismiss()
        AppState.remove(listener)
        scope.cancel()
        super.onDestroy()
    }

    private fun refreshServers(showFeedback: Boolean) = scope.launch {
        if (showFeedback) toast("Обновляем список серверов…")
        selectedStatus.text = "Проверка доступности…"
        runCatching {
            val loaded = withContext(Dispatchers.IO) { ConnectionProfiles.loadWithStatus(this@MainActivity, true) }
            loaded.copy(nodes = ServerAvailabilityChecker.checkAll(loaded.nodes))
        }.onSuccess {
            nodes = it.nodes
            serverListStatus = if (it.fromCache) {
                "Нет связи с сервером • показан сохранённый список"
            } else {
                "Обновлено только что • ${nodes.size} серверов"
            }
            renderSelectedServer()
            serverSheet?.let { dialog -> renderServerSheet(dialog) }
            if (showFeedback) toast(if (it.fromCache) "Нет связи: показан сохранённый список" else "Список серверов обновлён")
        }.onFailure {
            nodes = emptyList()
            serverListStatus = it.message ?: "Подписки недоступны"
            val noSubscriptions = SubscriptionStore.all(this@MainActivity).none(SubscriptionSource::enabled)
            selectedName.text = if (noSubscriptions) "Добавьте подписку" else "Нет доступных серверов"
            selectedSource.text = if (noSubscriptions) {
                "Настройки → Управление подписками"
            } else {
                "Проверьте активные подписки"
            }
            server.text = "—"
            serverSheet?.let { dialog -> renderServerSheet(dialog) }
            selectedStatus.text = if (noSubscriptions) "Список подписок пуст" else "Не удалось загрузить серверы"
            if (showFeedback || !noSubscriptions) toast("Ошибка подписки: ${it.message}")
            LogStore.add(this@MainActivity, "Ошибка обновления подписки: ${it.message}")
        }
    }

    private fun showServerSheet() {
        if (AppState.current.running) {
            toast("Отключите VPN для смены сервера")
            return
        }
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(R.layout.sheet_servers)
        dialog.findViewById<View>(R.id.serverSheetRoot)?.let {
            // Material handles the sheet's status-bar overlap; protect the other edges here.
            it.applySafeDrawingInsets(includeIme = false, includeTop = false)
        }
        dialog.setOnShowListener {
            dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
                sheet.background = ColorDrawable(Color.TRANSPARENT)
                sheet.layoutParams = sheet.layoutParams.apply { height = ViewGroup.LayoutParams.MATCH_PARENT }
                BottomSheetBehavior.from(sheet).apply {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    skipCollapsed = true
                    isFitToContents = true
                }
            }
            dialog.window?.decorView?.setBackgroundColor(Color.TRANSPARENT)
        }
        dialog.setOnDismissListener { serverSheet = null }
        serverSheet = dialog
        renderServerSheet(dialog)
        dialog.show()
    }

    private fun renderServerSheet(dialog: BottomSheetDialog) {
        val list = dialog.findViewById<LinearLayout>(R.id.serverList) ?: return
        val updated = dialog.findViewById<TextView>(R.id.serversUpdatedText)
        list.removeAllViews()
        if (nodes.isEmpty()) {
            updated?.text = serverListStatus
            return
        }
        updated?.text = serverListStatus
        val selectedId = ConnectionProfiles.selected(this, nodes).id
        nodes.forEach { node ->
            val item = LayoutInflater.from(this).inflate(R.layout.item_server, list, false)
            val available = node.availability !is Availability.Unavailable
            item.findViewById<ImageView>(R.id.serverFlag).setImageResource(flagFor(node.country))
            item.findViewById<TextView>(R.id.serverName).text = node.name
            item.findViewById<TextView>(R.id.serverSource).text = "Источник: ${node.subscriptionTitle ?: "не указан"}"
            val availability = item.findViewById<TextView>(R.id.serverAvailability)
            val latency = item.findViewById<TextView>(R.id.serverLatency)
            when (val state = node.availability) {
                is Availability.Available -> {
                    availability.text = "●  Доступен"
                    availability.setTextColor(color(R.color.dadway_success))
                    latency.text = "${state.latencyMs} мс"
                }
                Availability.Unavailable -> {
                    availability.text = "●  Недоступен"
                    availability.setTextColor(color(R.color.dadway_danger))
                    latency.text = ""
                    item.alpha = 0.58f
                }
                Availability.Unknown -> {
                    availability.text = "●  Проверка…"
                    availability.setTextColor(color(R.color.dadway_warning))
                    latency.text = ""
                }
            }
            item.setBackgroundResource(if (node.id == selectedId) R.drawable.card_selected else R.drawable.card_unselected)
            item.isEnabled = available
            item.setOnClickListener {
                ConnectionProfiles.select(this, node)
                renderSelectedServer()
                dialog.dismiss()
            }
            list.addView(item)
        }
        dialog.findViewById<ImageButton>(R.id.sheetRefreshButton)?.setOnClickListener { refreshServers(true) }
    }

    private fun renderSelectedServer() {
        if (nodes.isEmpty()) return
        val node = ConnectionProfiles.selected(this, nodes)
        selectedFlag.setImageResource(flagFor(node.country))
        selectedName.text = node.name
        selectedSource.text = "Источник: ${node.subscriptionTitle ?: "не указан"}"
        server.text = node.name
        when (val state = node.availability) {
            is Availability.Available -> {
                selectedStatus.text = "●  Доступен  •  ${state.latencyMs} мс"
                selectedStatus.setTextColor(color(R.color.dadway_success))
            }
            Availability.Unavailable -> {
                selectedStatus.text = "●  Недоступен"
                selectedStatus.setTextColor(color(R.color.dadway_danger))
            }
            Availability.Unknown -> {
                selectedStatus.text = "●  Проверка доступности…"
                selectedStatus.setTextColor(color(R.color.dadway_warning))
            }
        }
    }

    private fun flagFor(country: Country) = when (country) {
        Country.RUSSIA -> R.drawable.flag_russia
        Country.GERMANY -> R.drawable.flag_germany
        Country.USA -> R.drawable.flag_usa
        Country.NETHERLANDS -> R.drawable.flag_netherlands
        Country.UNITED_KINGDOM -> R.drawable.flag_uk
        Country.UNKNOWN -> R.drawable.flag_unknown
    }

    private fun requestVpn() {
        if (nodes.isEmpty()) {
            if (SubscriptionStore.all(this).none(SubscriptionSource::enabled)) {
                toast("Добавьте активную подписку в настройках")
                showSubscriptions()
            } else {
                toast("Дождитесь загрузки серверов")
            }
            return
        }
        val selected = ConnectionProfiles.selected(this, nodes)
        if (selected.availability is Availability.Unavailable) {
            toast("Выбранный сервер недоступен. Выберите другой")
            showServerSheet()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestVpnPermission()
        }
    }

    private fun requestVpnPermission() {
        VpnService.prepare(this)?.let(vpnPermission::launch) ?: startVpnService()
    }

    private fun showNotificationRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("Разрешите уведомления")
            .setMessage("Dadway VPN показывает активное подключение и выбранный сервер в системной шторке. Без этого разрешения подключение не будет скрыто запускаться в фоне.")
            .setPositiveButton("Открыть настройки") { _, _ ->
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun startVpnService() = ContextCompat.startForegroundService(
        this, Intent(this, DadwayVpnService::class.java).setAction(DadwayVpnService.ACTION_START)
    )
    private fun stopVpnService() = startService(Intent(this, DadwayVpnService::class.java).setAction(DadwayVpnService.ACTION_STOP))

    private fun testConnection() = scope.launch {
        if (!AppState.current.running) { toast("Сначала подключите VPN"); return@launch }
        runCatching { withContext(Dispatchers.IO) { ConnectionTester.test() } }
        .onSuccess { result ->
            AppState.update {
                it.copy(
                    externalIp = result.ip,
                    downBps = result.bytesPerSecond,
                )
            }
        }.onFailure { toast("Тест не выполнен: ${it.message}") }
    }

    private fun runAutomaticConnectionTest(firstConnection: Boolean) {
        autoTestJob?.cancel()
        autoTestJob = scope.launch {
            if (firstConnection) {
                AppState.update { it.copy(status = "Проверка внешнего IP…") }
                delay(350)
                AppState.update { it.copy(status = "Измерение скорости…") }
            }
            runCatching { withContext(Dispatchers.IO) { ConnectionTester.test() } }
            .onSuccess { result ->
                AppState.update {
                    it.copy(
                        status = "Готово",
                        externalIp = result.ip,
                        downBps = result.bytesPerSecond,
                    )
                }
                getSharedPreferences("dadway_onboarding", MODE_PRIVATE).edit()
                    .putBoolean("first_connection_test_completed", true).apply()
            }.onFailure { LogStore.add(this@MainActivity, "Автоматический тест: ${it.message}") }
        }
    }

    private fun showSubscriptions() {
        SubscriptionDialogs.show(this) { refreshServers(false) }
    }

    private fun render(state: UiState) {
        val justConnected = state.running && !wasRunning
        wasRunning = state.running
        if (justConnected) {
            val completed = getSharedPreferences("dadway_onboarding", MODE_PRIVATE)
                .getBoolean("first_connection_test_completed", false)
            runAutomaticConnectionTest(firstConnection = !completed)
        }
        ip.text = state.externalIp
        ping.text = state.pingMs?.let { "$it мс" } ?: "—"
        down.text = "↓ ${formatRate(state.downBps)}"
        up.text = "↑ ${formatRate(state.upBps)}"
        total.text = "↓ ${formatBytes(state.totalDown)}  •  ↑ ${formatBytes(state.totalUp)}"
        selectedCard.isEnabled = !state.running && !state.status.contains("Подключение", true)

        when {
            state.running -> {
                status.text = when {
                    state.status.startsWith("Проверка") || state.status.startsWith("Измерение") || state.status == "Готово" -> state.status
                    else -> "Защита активна"
                }
                status.setTextColor(color(R.color.dadway_success))
                connect.setBackgroundResource(R.drawable.btn_disconnect_selector)
                connectCaption.text = "ОТКЛЮЧИТЬ"
                statusIcon.setImageResource(R.drawable.status_connected)
            }
            state.status.contains("Подключение", true) -> {
                status.text = "Подключение…"
                status.setTextColor(color(R.color.dadway_warning))
                connect.setBackgroundResource(R.drawable.connecting_ring)
                connectCaption.text = "ПОДКЛЮЧЕНИЕ…"
                statusIcon.setImageResource(R.drawable.status_connecting)
            }
            else -> {
                status.text = if (state.status.startsWith("Ошибка")) state.status else "Защита выключена"
                status.setTextColor(color(R.color.dadway_danger))
                connect.setBackgroundResource(R.drawable.btn_connect_selector)
                connectCaption.text = "ПОДКЛЮЧИТЬСЯ"
                statusIcon.setImageResource(R.drawable.status_disconnected)
            }
        }
    }

    private fun openExternal(url: String) = runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { toast("Не удалось открыть ссылку") }
    private fun logExportText() = buildString {
        appendLine("Dadway VPN log export")
        appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Package: $packageName")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Exported: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())}")
        appendLine()
        append(LogStore.read(this@MainActivity))
    }
    private fun formatRate(value: Long) = "${formatBytes(value)}/с"
    private fun formatBytes(value: Long): String = when {
        value >= 1_073_741_824 -> String.format(Locale.US, "%.2f ГБ", value / 1_073_741_824.0)
        value >= 1_048_576 -> String.format(Locale.US, "%.2f МБ", value / 1_048_576.0)
        value >= 1024 -> String.format(Locale.US, "%.1f КБ", value / 1024.0)
        else -> "$value Б"
    }
    private fun color(id: Int) = ContextCompat.getColor(this, id)
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}

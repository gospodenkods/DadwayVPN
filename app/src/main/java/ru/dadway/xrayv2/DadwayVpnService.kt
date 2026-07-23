package ru.dadway.xrayv2

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class DadwayVpnService : VpnService() {
    companion object {
        const val ACTION_START = "ru.dadway.xrayv2.START"
        const val ACTION_STOP = "ru.dadway.xrayv2.STOP"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tun: ParcelFileDescriptor? = null
    private var metricsJob: Job? = null

    override fun onCreate() { super.onCreate(); createChannel() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVpn()
            else -> if (!AppState.current.running) startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        startForeground(42, notification("Подключение…"))
        AppState.update { it.copy(status = "Подключение…") }
        scope.launch {
            try {
                val requestedProfile = ConnectionProfiles.selected(this@DadwayVpnService)
                var activeProfile = requestedProfile
                val links = try {
                    ConnectionProfiles.connectionText(this@DadwayVpnService, requestedProfile)
                } catch (primaryError: Throwable) {
                    if (requestedProfile.id != ConnectionProfiles.DEFAULT_ID) throw primaryError
                    activeProfile = ConnectionProfiles.byId(ConnectionProfiles.SABINA_ID)
                    LogStore.add(
                        this@DadwayVpnService,
                        "Основной сервер недоступен (${primaryError.message}). Автоматическое переключение на резервный сервер"
                    )
                    ConnectionProfiles.connectionText(this@DadwayVpnService, activeProfile)
                }
                LogStore.add(this@DadwayVpnService, "Выбран профиль: ${activeProfile.title}")

                tun = Builder()
                    .setSession("Dadway VPN")
                    .setMtu(1500)
                    .addAddress("172.19.0.1", 30)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("1.1.1.1")
                    .addDisallowedApplication(packageName)
                    .establish() ?: error("Android не создал VPN-интерфейс")

                val sourceLink = ConnectionProfiles.firstShareLink(links)
                val base = XrayBridge.linksToConfig(links)
                val built = XrayConfigBuilder.build(
                    base = base,
                    tunFd = tun!!.fd,
                    filesDir = filesDir.absolutePath,
                    sourceLink = sourceLink
                )
                LogStore.add(this@DadwayVpnService, "Запуск VPN, сервер ${built.server}")
                XrayBridge.run(built.json)

                AppState.update { it.copy(running = true, status = "Подключено", server = built.server) }
                updateNotification("Подключено: ${built.server}")
                startMetrics()
            } catch (t: Throwable) {
                LogStore.add(this@DadwayVpnService, "ОШИБКА запуска: ${t.stackTraceToString()}")
                AppState.update { it.copy(running = false, status = "Ошибка: ${t.message}") }
                stopVpn()
            }
        }
    }

    private fun startMetrics() {
        metricsJob?.cancel()
        metricsJob = scope.launch {
            var previous = MetricsReader.Totals(0, 0)
            while (isActive) {
                delay(1000)
                runCatching { MetricsReader.read() }.onSuccess { now ->
                    val down = (now.down - previous.down).coerceAtLeast(0)
                    val up = (now.up - previous.up).coerceAtLeast(0)
                    previous = now
                    AppState.update { it.copy(downBps = down, upBps = up, totalDown = now.down, totalUp = now.up) }
                }
            }
        }
    }

    private fun stopVpn() {
        metricsJob?.cancel(); metricsJob = null
        runCatching { XrayBridge.stop() }
        runCatching { tun?.close() }; tun = null
        AppState.update { UiState(status = "Отключено") }
        LogStore.add(this, "VPN остановлен")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() { stopVpn(); super.onRevoke() }
    override fun onDestroy() { scope.cancel(); runCatching { XrayBridge.stop() }; runCatching { tun?.close() }; super.onDestroy() }

    private fun notification(text: String) = NotificationCompat.Builder(this, "vpn")
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setContentTitle("Dadway VPN")
        .setContentText(text)
        .setOngoing(true)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .build()

    private fun updateNotification(text: String) = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(42, notification(text))
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(NotificationChannel("vpn", "VPN-соединение", NotificationManager.IMPORTANCE_LOW))
    }
}

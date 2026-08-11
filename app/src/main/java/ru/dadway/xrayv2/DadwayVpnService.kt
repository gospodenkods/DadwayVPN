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
        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "vpn_connection"
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
        startForeground(NOTIFICATION_ID, notification("Подключение…"))
        AppState.update { it.copy(status = "Подключение…") }
        scope.launch {
            try {
                val (activeServer, link) = ConnectionProfiles.connectionText(this@DadwayVpnService)
                LogStore.add(this@DadwayVpnService, "Выбран сервер: ${activeServer.name}")

                tun = Builder()
                    .setSession("Dadway VPN")
                    .setMtu(1500)
                    .addAddress("172.19.0.1", 30)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("1.1.1.1")
                    .addDisallowedApplication(packageName)
                    .establish() ?: error("Android не создал VPN-интерфейс")

                val base = XrayBridge.linksToConfig(link)
                val built = XrayConfigBuilder.build(
                    base = base,
                    tunFd = tun!!.fd,
                    filesDir = filesDir.absolutePath,
                    sourceLink = link
                )
                LogStore.add(this@DadwayVpnService, "Запуск VPN: узел=${activeServer.name}, протокол=${built.protocol}, сервер=${built.server}")
                XrayBridge.run(built.json)

                AppState.update { it.copy(running = true, status = "Подключено", server = activeServer.name) }
                updateNotification("Подключено: ${activeServer.name}")
                startMetrics()
            } catch (t: Throwable) {
                LogStore.add(this@DadwayVpnService, "Ошибка запуска: ${t.stackTraceToString()}")
                stopVpn("Ошибка: ${t.message ?: "не удалось подключиться"}")
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

    private fun stopVpn(finalStatus: String = "Отключено") {
        metricsJob?.cancel(); metricsJob = null
        runCatching { XrayBridge.stop() }
        runCatching { tun?.close() }; tun = null
        AppState.update { UiState(status = finalStatus) }
        LogStore.add(this, "VPN остановлен")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() { stopVpn(); super.onRevoke() }
    override fun onDestroy() { scope.cancel(); runCatching { XrayBridge.stop() }; runCatching { tun?.close() }; super.onDestroy() }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_vpn)
        .setContentTitle(if (AppState.current.running) "Dadway VPN подключён" else "Dadway VPN")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .addAction(
            R.drawable.ic_notification_vpn,
            "Отключить",
            PendingIntent.getService(
                this,
                1,
                Intent(this, DadwayVpnService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .build()

    private fun updateNotification(text: String) =
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification(text))

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(NotificationChannel(CHANNEL_ID, "Активное VPN-соединение", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Состояние подключения Dadway VPN и выбранный сервер"
                    setShowBadge(false)
                })
        }
    }
}

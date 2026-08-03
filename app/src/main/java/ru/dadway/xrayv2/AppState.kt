package ru.dadway.xrayv2

import java.util.concurrent.CopyOnWriteArrayList

data class UiState(
    val running: Boolean = false,
    val status: String = "Отключено",
    val server: String = "—",
    val externalIp: String = "—",
    val pingMs: Long? = null,
    val downBps: Long = 0,
    val upBps: Long = 0,
    val totalDown: Long = 0,
    val totalUp: Long = 0
)

object AppState {
    @Volatile var current = UiState()
        private set
    private val listeners = CopyOnWriteArrayList<(UiState) -> Unit>()
    fun update(transform: (UiState) -> UiState) { current = transform(current); listeners.forEach { it(current) } }
    fun observe(listener: (UiState) -> Unit) { listeners += listener; listener(current) }
    fun remove(listener: (UiState) -> Unit) { listeners -= listener }
}

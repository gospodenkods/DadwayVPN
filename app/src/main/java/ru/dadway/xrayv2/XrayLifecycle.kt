package ru.dadway.xrayv2

/**
 * Serializes access to libXray's process-wide core and DNS resolver.
 *
 * libXray cannot safely run overlapping Xray instances in one process. Keeping
 * the state and native calls behind one lock also guarantees that ResetDNS is
 * called only after stopXray has completed.
 */
internal class XrayLifecycle<Controller>(
    private val setDns: (Controller) -> Unit,
    private val resetDns: () -> Unit,
    private val startCore: (String) -> Unit,
    private val stopCore: () -> Unit,
) {
    private enum class State { STOPPED, STARTING, RUNNING, STOPPING }

    private val lock = Any()

    @Volatile
    private var state = State.STOPPED
    private var dnsConfigured = false

    fun start(configJson: String, controller: Controller) = synchronized(lock) {
        check(state == State.STOPPED) { "Xray уже запускается или запущен" }
        state = State.STARTING

        try {
            setDns(controller)
            dnsConfigured = true
            startCore(configJson)
            state = State.RUNNING
        } catch (error: Throwable) {
            if (dnsConfigured) {
                runCatching { resetDns() }.exceptionOrNull()?.let(error::addSuppressed)
            }
            dnsConfigured = false
            state = State.STOPPED
            throw error
        }
    }

    fun stop() = synchronized(lock) {
        if (state == State.STOPPED) return@synchronized
        state = State.STOPPING

        var failure: Throwable? = null
        try {
            stopCore()
        } catch (error: Throwable) {
            failure = error
        } finally {
            if (dnsConfigured) {
                try {
                    resetDns()
                } catch (error: Throwable) {
                    if (failure == null) failure = error else failure!!.addSuppressed(error)
                }
            }
            dnsConfigured = false
            state = State.STOPPED
        }

        failure?.let { throw it }
    }

    fun isRunning(): Boolean = state == State.RUNNING
}

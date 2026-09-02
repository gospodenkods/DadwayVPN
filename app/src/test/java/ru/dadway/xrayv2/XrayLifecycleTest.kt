package ru.dadway.xrayv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class XrayLifecycleTest {
    @Test
    fun configuresDnsBeforeStartAndResetsItAfterStop() {
        val calls = mutableListOf<String>()
        val lifecycle = XrayLifecycle<String>(
            setDns = { calls += "setDNS:$it" },
            resetDns = { calls += "resetDNS" },
            startCore = { calls += "start:$it" },
            stopCore = { calls += "stop" },
        )

        lifecycle.start("config", "controller")
        assertTrue(lifecycle.isRunning())
        lifecycle.stop()

        assertFalse(lifecycle.isRunning())
        assertEquals(listOf("setDNS:controller", "start:config", "stop", "resetDNS"), calls)
    }

    @Test
    fun concurrentStartRunsNativeCoreOnlyOnce() {
        val startEntered = CountDownLatch(1)
        val releaseStart = CountDownLatch(1)
        val nativeStarts = AtomicInteger()
        val secondFailure = AtomicReference<Throwable?>()
        val lifecycle = XrayLifecycle<Unit>(
            setDns = { },
            resetDns = { },
            startCore = {
                nativeStarts.incrementAndGet()
                startEntered.countDown()
                releaseStart.await(2, TimeUnit.SECONDS)
            },
            stopCore = { },
        )

        val first = thread { lifecycle.start("first", Unit) }
        assertTrue(startEntered.await(1, TimeUnit.SECONDS))
        val second = thread {
            secondFailure.set(runCatching { lifecycle.start("second", Unit) }.exceptionOrNull())
        }
        releaseStart.countDown()
        first.join(1_000)
        second.join(1_000)

        assertEquals(1, nativeStarts.get())
        assertTrue(secondFailure.get() is IllegalStateException)
        lifecycle.stop()
    }

    @Test
    fun failedStartResetsDnsAndAllowsNextStart() {
        var shouldFail = true
        var resets = 0
        val lifecycle = XrayLifecycle<Unit>(
            setDns = { },
            resetDns = { resets += 1 },
            startCore = {
                if (shouldFail) {
                    shouldFail = false
                    error("start failed")
                }
            },
            stopCore = { },
        )

        assertTrue(runCatching { lifecycle.start("first", Unit) }.isFailure)
        assertEquals(1, resets)
        lifecycle.start("second", Unit)
        assertTrue(lifecycle.isRunning())
        lifecycle.stop()
        assertEquals(2, resets)
    }
}

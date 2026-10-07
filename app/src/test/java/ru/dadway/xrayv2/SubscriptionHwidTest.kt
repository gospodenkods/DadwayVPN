package ru.dadway.xrayv2

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class SubscriptionHwidTest {
    private class Response(url: URL, private val code: Int, private val location: String? = null) : HttpURLConnection(url) {
        var closed = false
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getHeaderField(name: String): String? = if (name == "Location") location else null
        override fun getInputStream() = ByteArrayInputStream("vless://test".toByteArray())
    }

    @Test fun stableIdIsScopedToAppAndDevice() {
        val id = SubscriptionDeviceId.derive("ru.dadway.dadwayvpn", "abcdef1234567890")
        assertEquals(id, SubscriptionDeviceId.derive("ru.dadway.dadwayvpn", "abcdef1234567890"))
        assertTrue(id.matches(Regex("[0-9a-f]{64}")))
        assertNotEquals(id, SubscriptionDeviceId.derive("other.app", "abcdef1234567890"))
        assertNotEquals(id, SubscriptionDeviceId.derive("ru.dadway.dadwayvpn", "1111"))
        assertNull(SubscriptionDeviceId.usableAndroidId("9774d56d682e549c"))
        assertNull(SubscriptionDeviceId.usableAndroidId("00000000"))
        assertNull(SubscriptionDeviceId.usableAndroidId(null))
    }

    @Test fun refreshSendsHwidAndStripsFragment() {
        lateinit var response: Response
        assertEquals("vless://test", SubscriptionClient.fetchRaw("https://sub.example/list#site", "device") {
            response = Response(it, 200)
            response
        })
        assertEquals("device", response.getRequestProperty("X-HWID"))
        assertNull(response.url.ref)
        assertFalse(response.instanceFollowRedirects)
        assertTrue(response.closed)
    }

    @Test fun sameOriginRedirectRetainsHwidButOtherOriginDoesNot() {
        val responses = mutableListOf<Response>()
        SubscriptionClient.fetchRaw("https://sub.example/list", "device") {
            Response(it, if (responses.size < 2) 302 else 200,
                when (responses.size) { 0 -> "/final"; 1 -> "https://other.example/list"; else -> null }
            ).also(responses::add)
        }
        assertEquals("device", responses[0].getRequestProperty("X-HWID"))
        assertEquals("device", responses[1].getRequestProperty("X-HWID"))
        assertNull(responses[2].getRequestProperty("X-HWID"))
        assertTrue(responses.all { it.closed })
    }

    @Test fun changedPortDoesNotReceiveHwid() {
        val responses = mutableListOf<Response>()
        SubscriptionClient.fetchRaw("https://sub.example/list", "device") {
            Response(it, if (responses.isEmpty()) 307 else 200, "https://sub.example:8443/list")
                .also(responses::add)
        }
        assertNull(responses.last().getRequestProperty("X-HWID"))
    }

    @Test fun rejectsDowngradeAndClosesConnection() {
        lateinit var response: Response
        try {
            SubscriptionClient.fetchRaw("https://sub.example/list", "device") {
                Response(it, 302, "http://sub.example/list").also { response = it }
            }
            fail("HTTP downgrade should fail")
        } catch (_: IOException) { assertTrue(response.closed) }
    }

    @Test fun deniedSubscriptionsRemainDenied() {
        try {
            SubscriptionClient.fetchRaw("https://sub.example/list", "device") { Response(it, 403) }
            fail("Denied subscription should fail")
        } catch (error: SubscriptionAccessException) { assertEquals(403, error.statusCode) }
    }

    @Test fun redirectLoopIsBoundedAndEveryConnectionCloses() {
        val responses = mutableListOf<Response>()
        try {
            SubscriptionClient.fetchRaw("https://sub.example/list", "device") {
                Response(it, 302, "/list").also(responses::add)
            }
            fail("Redirect loop should fail")
        } catch (_: IOException) {
            assertEquals(6, responses.size)
            assertTrue(responses.all { it.closed })
        }
    }
}

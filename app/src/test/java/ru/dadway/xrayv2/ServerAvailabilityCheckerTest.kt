package ru.dadway.xrayv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerAvailabilityCheckerTest {
    @Test
    fun usesMedianLatencyForSelectedVpnEndpoint() {
        assertEquals(31L, ServerAvailabilityChecker.medianLatency(listOf(50L, 20L, 31L)))
    }

    @Test
    fun averagesMiddleSamplesWhenOneAttemptFails() {
        assertEquals(30L, ServerAvailabilityChecker.medianLatency(listOf(40L, 20L)))
    }

    @Test
    fun returnsNoLatencyWhenEndpointIsUnavailable() {
        assertNull(ServerAvailabilityChecker.medianLatency(emptyList()))
    }
}

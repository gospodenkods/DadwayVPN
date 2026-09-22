package ru.dadway.xrayv2

import org.junit.Assert.assertEquals
import org.junit.Test

class AppRoutingPolicyTest {
    @Test
    fun selectedPackages_removesOwnPackageBlanksAndDuplicates() {
        val settings = AppRoutingSettings(
            mode = AppRoutingMode.SELECTED,
            packages = setOf(
                "org.telegram.messenger",
                " ru.example.browser ",
                "ru.dadway.dadwayvpn",
                "",
            ),
        )

        assertEquals(
            listOf("org.telegram.messenger", "ru.example.browser"),
            AppRoutingPolicy.selectedPackages(settings, "ru.dadway.dadwayvpn"),
        )
    }

    @Test
    fun selectedPackages_isStableAndSorted() {
        val settings = AppRoutingSettings(
            mode = AppRoutingMode.SELECTED,
            packages = setOf("z.example", "a.example"),
        )

        assertEquals(
            listOf("a.example", "z.example"),
            AppRoutingPolicy.selectedPackages(settings, "ru.dadway.dadwayvpn"),
        )
    }
}

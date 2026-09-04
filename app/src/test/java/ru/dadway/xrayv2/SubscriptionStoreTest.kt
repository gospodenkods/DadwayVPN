package ru.dadway.xrayv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionStoreTest {
    @Test
    fun productionMigrationRemovesOnlyBundledSources() {
        val userPromo = SubscriptionSource(
            id = "2b51ee52-75c3-4fe2-a6a2-d154e892a84a",
            url = "https://devel.dadway.ru/sub/promo#https%3A%2F%2Fdadway.ru",
            enabled = false,
        )
        val result = SubscriptionStore.removeBundledSources(
            listOf(
                SubscriptionSource("default-promo", userPromo.url, true),
                SubscriptionSource("default-zpp", "https://devel.dadway.ru/sub/zpp", true),
                userPromo,
            )
        )

        assertEquals(listOf(userPromo), result)
        assertTrue(result.single().enabled.not())
    }

    @Test
    fun productionMigrationKeepsAllUserSubscriptions() {
        val sources = listOf(
            SubscriptionSource("first-user-id", "https://one.example/sub", true),
            SubscriptionSource("second-user-id", "https://two.example/sub", false),
        )

        assertEquals(sources, SubscriptionStore.removeBundledSources(sources))
    }
}

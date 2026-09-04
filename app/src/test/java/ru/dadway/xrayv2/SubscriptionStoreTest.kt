package ru.dadway.xrayv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionStoreTest {
    @Test
    fun versionFiveMigrationClearsEveryLegacySubscription() {
        val legacySources = listOf(
            SubscriptionSource("default-promo", "https://devel.dadway.ru/sub/promo", true),
            SubscriptionSource(
                "2b51ee52-75c3-4fe2-a6a2-d154e892a84a",
                "https://provider.example/subscription",
                false,
            ),
        )

        assertTrue(SubscriptionStore.sourcesAfterProductionReset(legacySources, storedVersion = 4).isEmpty())
    }

    @Test
    fun completedMigrationDoesNotClearNewSubscriptionsAgain() {
        val sources = listOf(
            SubscriptionSource("first-user-id", "https://one.example/sub", true),
            SubscriptionSource("second-user-id", "https://two.example/sub", false),
        )

        assertEquals(sources, SubscriptionStore.sourcesAfterProductionReset(sources, storedVersion = 5))
    }
}

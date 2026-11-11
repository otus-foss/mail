package com.otusfoss.mail.feature.notifications.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SuggestedDistributorTest {
    private val google = PushDistributor("google", "Google Play services", isGoogle = true)
    private val ntfy = PushDistributor("io.heckel.ntfy", "ntfy")

    @Test
    fun privateSettingsStartWithAnInstalledAppNotGoogle() {
        assertEquals(ntfy, listOf(google, ntfy).suggested(private = true))
    }

    @Test
    fun privateSettingsStayOnPeriodicChecksWhereOnlyGoogleIsOffered() {
        assertNull(listOf(google).suggested(private = true))
    }

    @Test
    fun convenientSettingsStartWithGoogleWhereItIsOffered() {
        assertEquals(google, listOf(ntfy, google).suggested(private = false))
    }

    @Test
    fun convenientSettingsFallBackToAnInstalledApp() {
        assertEquals(ntfy, listOf(ntfy).suggested(private = false))
    }

    @Test
    fun nothingIsSuggestedWithoutAService() {
        assertNull(emptyList<PushDistributor>().suggested(private = false))
    }
}

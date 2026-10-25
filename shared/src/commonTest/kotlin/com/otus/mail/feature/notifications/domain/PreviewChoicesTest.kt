package com.otus.mail.feature.notifications.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class PreviewChoicesTest {
    private val choices =
        PreviewChoices()
            .withLogin("work", NotificationPreviews.Hidden)
            .withAddress("Private@Example.com", NotificationPreviews.Device)
            .withAddress("boss@example.com", NotificationPreviews.Hidden)

    @Test
    fun anAddressWinsOverItsLoginWhichWinsOverAll() {
        assertEquals(NotificationPreviews.Device, choices.forMail("home", emptyList()))
        assertEquals(NotificationPreviews.Hidden, choices.forMail("work", listOf("someone@example.com")))
        assertEquals(NotificationPreviews.Device, choices.forMail("work", listOf("private@example.com")))
    }

    @Test
    fun theMorePrivateAddressWins() {
        assertEquals(
            NotificationPreviews.Hidden,
            choices.forMail("home", listOf("private@example.com", "boss@example.com")),
        )
    }

    @Test
    fun choicesSurviveStorageAndOldValuesCarryOver() {
        assertEquals(choices, PreviewChoices.decode(choices.encode()))
        assertEquals(NotificationPreviews.Device, PreviewChoices.decode(mapOf("previews" to "unlocked")).all)
        assertEquals(NotificationPreviews.Device, PreviewChoices.decode(mapOf("previews" to "always")).all)
        assertEquals(NotificationPreviews.Hidden, PreviewChoices.decode(mapOf("previews" to "never")).all)
    }
}

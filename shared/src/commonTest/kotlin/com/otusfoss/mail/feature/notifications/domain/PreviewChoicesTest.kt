package com.otusfoss.mail.feature.notifications.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class PreviewChoicesTest {
    private val choices =
        PreviewChoices()
            .withLogin("work", NotificationPreviews.Hidden)
            .withAddress("work", "Private@Example.com", NotificationPreviews.Device)
            .withAddress("home", "boss@example.com", NotificationPreviews.Hidden)
            .withAddress("home", "private@example.com", NotificationPreviews.Device)

    @Test
    fun anAddressWinsOverItsLoginWhichWinsOverAll() {
        assertEquals(NotificationPreviews.Device, choices.forMail("home", emptyList()))
        assertEquals(NotificationPreviews.Hidden, choices.forMail("work", listOf("someone@example.com")))
        assertEquals(NotificationPreviews.Device, choices.forMail("work", listOf("private@example.com")))
    }

    @Test
    fun anAddressChoiceOfAnotherLoginDoesNotCount() {
        val split =
            PreviewChoices()
                .withLogin("work", NotificationPreviews.Hidden)
                .withAddress("home", "me@home", NotificationPreviews.Device)
        assertEquals(NotificationPreviews.Hidden, split.forMail("work", listOf("me@home")))
        assertEquals(NotificationPreviews.Device, split.forMail("home", listOf("me@home")))
    }

    @Test
    fun theMorePrivateAddressWins() {
        assertEquals(
            NotificationPreviews.Hidden,
            choices.forMail("home", listOf("private@example.com", "boss@example.com")),
        )
    }

    @Test
    fun addressesWithoutAChoiceCountWithTheirLogin() {
        assertEquals(
            NotificationPreviews.Hidden,
            choices.forMail("work", listOf("private@example.com", "colleague@example.com")),
        )
    }

    @Test
    fun aWildcardAddressCoversItsDomainUnlessAnAddressHasItsOwnChoice() {
        val wildcard =
            choices
                .withAddress("home", "*@Team.example.org", NotificationPreviews.Hidden)
                .withAddress("home", "lead@team.example.org", NotificationPreviews.Device)
        assertEquals(NotificationPreviews.Hidden, wildcard.forMail("home", listOf("Anyone@team.example.org")))
        assertEquals(NotificationPreviews.Device, wildcard.forMail("home", listOf("lead@team.example.org")))
        assertEquals(NotificationPreviews.Device, wildcard.forMail("home", listOf("someone@example.org")))
        assertEquals(NotificationPreviews.Device, wildcard.forMail("other", listOf("anyone@team.example.org")))
    }

    @Test
    fun choicesSurviveStorageAndOldValuesCarryOver() {
        // Login ids hold the sign-in name and session URL, separated by a space.
        val spaced =
            choices.withAddress("me https://jmap.example.com/session", "me@example.com", NotificationPreviews.Hidden)
        assertEquals(spaced, PreviewChoices.decode(spaced.encode()))
        assertEquals(NotificationPreviews.Device, PreviewChoices.decode(mapOf("previews" to "unlocked")).all)
        assertEquals(NotificationPreviews.Device, PreviewChoices.decode(mapOf("previews" to "always")).all)
        assertEquals(NotificationPreviews.Hidden, PreviewChoices.decode(mapOf("previews" to "never")).all)
    }

    @Test
    fun oldAddressChoicesOnlyHideUntilTheLoginsThatOwnThemClaimThem() {
        val old =
            PreviewChoices.decode(
                mapOf(
                    "login:work" to "never",
                    "address:me@home" to "always",
                    "address:secret@home" to "never",
                ),
            )
        assertEquals(NotificationPreviews.Hidden, old.forMail("other", listOf("secret@home")))
        assertEquals(NotificationPreviews.Hidden, old.forMail("work", listOf("me@home")))

        val claimed = old.claim(mapOf("home" to listOf("Me@home", "secret@home"), "work" to listOf("me@work")))
        assertEquals(emptyMap<String, NotificationPreviews>(), claimed.unclaimed)
        assertEquals(NotificationPreviews.Device, claimed.forAddress("home", "me@home"))
        assertEquals(NotificationPreviews.Hidden, claimed.forMail("home", listOf("secret@home")))
        assertEquals(NotificationPreviews.Device, claimed.forMail("other", listOf("secret@home")))
        assertEquals(NotificationPreviews.Hidden, claimed.forMail("work", listOf("me@home")))
    }
}

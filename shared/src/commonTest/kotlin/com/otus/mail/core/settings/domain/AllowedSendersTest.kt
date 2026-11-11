package com.otus.mail.core.settings.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AllowedSendersTest {
    @Test
    fun aSenderIsAllowedOnceInLowercaseNewestFirst() {
        val behavior =
            ReadBehavior(allowedSenders = listOf("grace@example.org", "news@sentry.example"))
                .allowingSender(" News@Sentry.example ")
        assertEquals(listOf("news@sentry.example", "grace@example.org"), behavior.allowedSenders)
    }

    @Test
    fun removingASenderIgnoresCase() {
        val behavior =
            ReadBehavior(allowedSenders = listOf("grace@example.org", "news@sentry.example"))
                .withoutSender("Grace@Example.org")
        assertEquals(listOf("news@sentry.example"), behavior.allowedSenders)
    }

    @Test
    fun aBlankAddressChangesNothing() {
        val behavior = ReadBehavior(allowedSenders = listOf("grace@example.org"))
        assertEquals(behavior, behavior.allowingSender("  "))
    }
}

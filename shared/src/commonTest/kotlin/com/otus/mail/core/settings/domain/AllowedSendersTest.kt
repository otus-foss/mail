package com.otus.mail.core.settings.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AllowedSendersTest {
    @Test
    fun aSenderIsAllowedOnceInLowercaseNewestFirst() {
        val behavior =
            ReadBehavior(allowedSenders = listOf("grace@example.org", "news@sentry.io"))
                .allowingSender(" News@Sentry.io ")
        assertEquals(listOf("news@sentry.io", "grace@example.org"), behavior.allowedSenders)
    }

    @Test
    fun removingASenderIgnoresCase() {
        val behavior =
            ReadBehavior(allowedSenders = listOf("grace@example.org", "news@sentry.io"))
                .withoutSender("Grace@Example.org")
        assertEquals(listOf("news@sentry.io"), behavior.allowedSenders)
    }

    @Test
    fun aBlankAddressChangesNothing() {
        val behavior = ReadBehavior(allowedSenders = listOf("grace@example.org"))
        assertEquals(behavior, behavior.allowingSender("  "))
    }
}

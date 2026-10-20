package com.otus.mail.feature.inbox.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AllowedSendersTest {
    @Test
    fun aSenderIsAllowedOnceInLowercaseNewestFirst() {
        val behavior =
            MailBehavior(allowedSenders = listOf("grace@example.org", "news@sentry.io"))
                .allowingSender(" News@Sentry.io ")
        assertEquals(listOf("news@sentry.io", "grace@example.org"), behavior.allowedSenders)
    }

    @Test
    fun removingASenderIgnoresCase() {
        val behavior =
            MailBehavior(allowedSenders = listOf("grace@example.org", "news@sentry.io"))
                .withoutSender("Grace@Example.org")
        assertEquals(listOf("news@sentry.io"), behavior.allowedSenders)
    }

    @Test
    fun aBlankAddressChangesNothing() {
        val behavior = MailBehavior(allowedSenders = listOf("grace@example.org"))
        assertEquals(behavior, behavior.allowingSender("  "))
    }
}

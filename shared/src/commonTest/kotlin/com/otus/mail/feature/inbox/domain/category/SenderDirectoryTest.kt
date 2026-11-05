package com.otus.mail.feature.inbox.domain.category

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SenderDirectoryTest {
    @Test
    fun aDomainCoversItsSubdomainsButNotLookalikes() {
        assertEquals("github.com" to MailCategory.Updates, TestSenders.find("noreply@github.com"))
        assertEquals("github.com" to MailCategory.Updates, TestSenders.find("ci@Mail.GitHub.com."))
        assertNull(TestSenders.find("support@github.com.evil.example"))
        assertNull(TestSenders.find("support@notgithub.com"))
        assertNull(TestSenders.find("no-domain"))
    }

    @Test
    fun theListSkipsCommentsBlankLinesAndUnknownCategories() {
        val parsed =
            SenderDirectory.parse(
                """
                version 7
                # comment
                social   example.org   # trailing comment

                forums lists.example.org
                promotions
                """.trimIndent(),
            )
        assertEquals(7, parsed.version)
        assertEquals("example.org" to MailCategory.Social, parsed.find("a@example.org"))
        assertNull(parsed.find("a@lists.example.com"))
        assertEquals("example.org" to MailCategory.Social, parsed.find("a@lists.example.org"))
    }

    @Test
    fun aListWithoutAVersionIsVersionZero() {
        assertEquals(0, SenderDirectory.parse("social example.org").version)
        assertEquals(0, SenderDirectory.parse("version soon").version)
    }
}

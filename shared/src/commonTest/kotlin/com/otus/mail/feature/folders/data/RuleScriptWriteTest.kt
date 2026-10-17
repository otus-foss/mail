package com.otus.mail.feature.folders.data

import com.otus.mail.core.mail.MailboxObject
import com.otus.mail.feature.folders.domain.AddressField
import com.otus.mail.feature.folders.domain.AddressMatch
import com.otus.mail.feature.folders.domain.FolderRule
import com.otus.mail.feature.folders.domain.RuleCondition
import kotlin.test.Test
import kotlin.test.assertEquals

class RuleScriptWriteTest {
    private val rule = FolderRule(listOf(RuleCondition.Address(AddressField.From, AddressMatch.Domain("x.example"))))

    @Test
    fun pathsFollowParentsFromTheTop() {
        val mailboxes =
            listOf(
                MailboxObject("i", "Inbox", role = "inbox"),
                MailboxObject("w", "Work", parentId = "i"),
                MailboxObject("p", "Projects", parentId = "w"),
            )
        val folders = mapOf("p" to listOf(rule)).inFolderOrder(mailboxes)
        assertEquals(listOf(BlockFolder("p", "INBOX/Work/Projects", listOf(rule))), folders)
    }

    @Test
    fun aParentCycleStopsThePathAtTheFirstRepeat() {
        val mailboxes = listOf(MailboxObject("a", "A", parentId = "b"), MailboxObject("b", "B", parentId = "a"))
        val folders = mapOf("a" to listOf(rule), "b" to listOf(rule)).inFolderOrder(mailboxes)
        assertEquals(mapOf("a" to "B/A", "b" to "A/B"), folders.associate { it.id to it.path })
    }
}

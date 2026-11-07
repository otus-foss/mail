package com.otus.mail.feature.folders.presentation

import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.feature.folders.domain.AddressField
import com.otus.mail.feature.folders.domain.AddressMatch
import com.otus.mail.feature.folders.domain.FolderRule
import com.otus.mail.feature.folders.domain.RuleCondition
import kotlin.test.Test
import kotlin.test.assertEquals

class ParentRulesTest {
    private val clients = MailFolder("ada", "c", "Clients")
    private val acme = MailFolder("ada", "a", "Acme", parentId = "c")
    private val team = MailFolder("ada", "t", "Team", parentId = "a")
    private val jana = MailFolder("ada", "j", "Jana", parentId = "t")
    private val folders = listOf(clients, acme, team, jana)

    private fun from(address: String) =
        FolderRule(RuleCondition.Address(AddressField.From, AddressMatch.Exact(address)))

    private val rules = mapOf("c" to listOf(from("a@acme.example")), "a" to listOf(from("b@acme.example")))

    @Test
    fun parentsWithRulesComeTopLevelFirstAndEmptyOnesAreLeftOut() {
        val parents = parentRules(folders, jana) { rules[it.id].orEmpty() }
        assertEquals(listOf("Clients", "Acme"), parents.map { it.folder.name })
        assertEquals(listOf(from("a@acme.example")), parents.first().rules)
    }

    @Test
    fun aTopLevelFolderHasNoParentRules() {
        assertEquals(emptyList(), parentRules(folders, clients) { rules[it.id].orEmpty() })
    }
}

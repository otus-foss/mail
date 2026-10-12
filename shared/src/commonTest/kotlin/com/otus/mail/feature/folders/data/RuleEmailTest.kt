package com.otus.mail.feature.folders.data

import com.otus.mail.feature.folders.domain.MailRule
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.RuleField
import com.otus.mail.feature.folders.domain.RuleOperator
import com.otus.mail.feature.folders.domain.matches
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuleEmailTest {
    private val email =
        Json.decodeFromString(
            RuleEmail.serializer(),
            """{"id": "e1", "bodyStructure": {"type": "multipart/mixed", "subParts": [
                {"type": "multipart/alternative", "subParts": [{"type": "text/plain"}, {"type": "text/html"}]},
                {"type": "application/pdf", "name": "invoice.pdf"}]}}""",
        )

    private fun rule(
        field: RuleField,
        operator: RuleOperator,
        value: String,
    ) = MailRule("r1", "Rule", listOf(RuleCondition(field, operator, value)), "M7")

    @Test
    fun attachmentTypeRulesMatchEveryMimePartLikeTheServer() {
        val message = email.toRuleMessage()
        assertEquals(
            listOf("multipart/mixed", "multipart/alternative", "text/plain", "text/html", "application/pdf"),
            message.attachmentTypes,
        )
        assertEquals(listOf("invoice.pdf"), message.attachmentNames)
        assertTrue(rule(RuleField.AttachmentType, RuleOperator.Is, "text/plain").matches(message))
        assertFalse(rule(RuleField.AttachmentType, RuleOperator.IsNot, "text/html").matches(message))
        assertTrue(rule(RuleField.AttachmentName, RuleOperator.EndsWith, ".pdf").matches(message))
    }

    @Test
    fun aMessageWithoutABodyStructureHasNoParts() {
        val message = RuleEmail("e2").toRuleMessage()
        assertTrue(message.attachmentTypes.isEmpty() && message.attachmentNames.isEmpty())
    }
}

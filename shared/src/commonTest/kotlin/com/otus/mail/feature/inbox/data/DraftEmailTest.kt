package com.otus.mail.feature.inbox.data

import com.otus.mail.feature.inbox.domain.Draft
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DraftEmailTest {
    private val quote = "On 2026-10-11 12:30, Grace wrote:\n> Noon?\n> <ok>"

    @Test
    fun aPlainDraftHasOnlyATextPartWithTheQuoteAfterTheBody() {
        val email = Draft("a1", body = "Thursday works.", quote = quote).toEmail("dr")
        assertFalse("htmlBody" in email.keys)
        assertEquals("Thursday works.\n\n$quote", email.part("text"))
    }

    @Test
    fun aFormattedDraftAddsAnHtmlPartThatQuotesInABlockquote() {
        val draft = Draft("a1", body = "Thursday works.", html = "<p><b>Thursday</b> works.</p>", quote = quote)
        val email = draft.toEmail("dr")
        val html = email.part("html")
        assertTrue(html.startsWith("<p><b>Thursday</b> works.</p><br>On 2026-10-11 12:30, Grace wrote:<br>"))
        assertTrue(html.endsWith("<blockquote>Noon?<br>&lt;ok&gt;</blockquote>"))
        assertEquals("Thursday works.\n\n$quote", email.part("text"))
    }

    private fun kotlinx.serialization.json.JsonObject.part(id: String): String =
        getValue("bodyValues")
            .jsonObject
            .getValue(id)
            .jsonObject
            .getValue("value")
            .jsonPrimitive.content
}

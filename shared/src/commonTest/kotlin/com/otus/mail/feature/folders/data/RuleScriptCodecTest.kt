package com.otus.mail.feature.folders.data

import com.otus.mail.feature.folders.domain.MailRule
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.RuleField
import com.otus.mail.feature.folders.domain.RuleOperator
import com.otus.mail.feature.folders.domain.RuleSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuleScriptCodecTest {
    private val support = RuleSupport(setOf("fileinto", "mailboxid", "imap4flags", "include", "mime", "regex", "body"))
    private val invites =
        MailRule(
            id = "r1",
            name = "Invites from Ada",
            conditions =
                listOf(
                    RuleCondition(RuleField.From, RuleOperator.Is, "ada@example.org"),
                    RuleCondition(RuleField.AttachmentName, RuleOperator.EndsWith, ".ics"),
                ),
            folderId = "M7",
        )
    private val copies =
        MailRule(
            id = "r2",
            name = "Copies",
            conditions =
                listOf(
                    RuleCondition(RuleField.Cc, RuleOperator.Contains, "team"),
                    RuleCondition(RuleField.Subject, RuleOperator.MatchesRegex, "^\\[ops\\]"),
                ),
            folderId = "M8",
            matchAll = false,
            markRead = true,
        )

    @Test
    fun rulesBecomeSieveThatFilesByFolderIdAndStops() {
        val sieve = encodeRules(RuleScript(listOf(invites, copies), keep = "roundcube")).lines().drop(2)
        val expected =
            listOf(
                """require ["fileinto", "imap4flags", "include", "mailboxid", "mime", "regex"];""",
                "",
                "# Invites from Ada",
                """if allof (address :all :is "from" "ada@example.org", anyof (header :mime :anychild :param """ +
                    """"filename" :matches "Content-Disposition" "*.ics", header :mime :anychild :param "name" """ +
                    """:matches "Content-Type" "*.ics")) {""",
                """    fileinto :mailboxid "M7" "INBOX";""",
                "    stop;",
                "}",
                "",
                "# Copies",
                """if anyof (header :contains "cc" "team", header :regex "subject" "^\\[ops\\]") {""",
                """    fileinto :flags "\\Seen" :mailboxid "M8" "INBOX";""",
                "    stop;",
                "}",
                "",
                """include :personal :optional "roundcube";""",
                "",
            )
        assertEquals(expected, sieve.map { it.trimEnd('\r') })
    }

    @Test
    fun valuesAreQuotedAndWildcardsStayLiteral() {
        val tricky =
            MailRule(
                "r3",
                "Line\nbreak",
                listOf(
                    RuleCondition(RuleField.Subject, RuleOperator.StartsWith, "50% \"off\" *now?"),
                    RuleCondition(RuleField.Recipient, RuleOperator.DoesNotContain, "back\\slash"),
                ),
                "M9",
            )
        val sieve = encodeRules(RuleScript(listOf(tricky)))
        assertTrue("# Line break\r\n" in sieve, sieve)
        assertTrue("""header :matches "subject" "50% \"off\" \\*now\\?*"""" in sieve, sieve)
        assertTrue("""not header :contains ["to", "cc"] "back\\slash"""" in sieve, sieve)
    }

    @Test
    fun pausedRulesStayStoredButDoNotRun() {
        val paused = invites.copy(enabled = false)
        val sieve = encodeRules(RuleScript(listOf(paused)))
        assertFalse("fileinto" in sieve.lines().filterNot { it.startsWith("#") }.joinToString())
        assertEquals(listOf(paused), decodeRules(sieve, support).script.rules)
    }

    @Test
    fun rulesReadBackUnchangedWhateverTheLineEnds() {
        val script = RuleScript(listOf(invites, copies), keep = "roundcube")
        val stored = encodeRules(script)
        assertEquals(ReadRules(script, changedElsewhere = false), decodeRules(stored, support))
        assertEquals(ReadRules(script, changedElsewhere = false), decodeRules(stored.replace("\r\n", "\n"), support))
    }

    @Test
    fun editsMadeElsewhereAreDetected() {
        val stored = encodeRules(RuleScript(listOf(invites)))
        val edited = stored.replace("stop;", "keep;\r\n    stop;")
        assertEquals(ReadRules(RuleScript(listOf(invites)), changedElsewhere = true), decodeRules(edited, support))
        val foreign = "require \"fileinto\";\r\nfileinto \"Junk\";\r\n"
        assertEquals(ReadRules(RuleScript(emptyList()), changedElsewhere = true), decodeRules(foreign, support))
        val newer = stored.replace("\"field\":\"from\"", "\"field\":\"priority\"")
        assertTrue(decodeRules(newer, support).changedElsewhere, "Unknown conditions are never dropped silently")
    }
}

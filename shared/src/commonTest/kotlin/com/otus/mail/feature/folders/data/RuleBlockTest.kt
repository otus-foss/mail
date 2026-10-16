package com.otus.mail.feature.folders.data

import com.otus.mail.feature.folders.domain.AddressField
import com.otus.mail.feature.folders.domain.AddressMatch
import com.otus.mail.feature.folders.domain.FolderRule
import com.otus.mail.feature.folders.domain.HeaderOperator
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.RuleSupport
import com.otus.mail.feature.folders.domain.TextField
import com.otus.mail.feature.folders.domain.TextOperator
import com.otus.mail.feature.folders.domain.TimeRange
import com.otus.mail.feature.folders.domain.WEEKDAYS
import com.otus.mail.feature.folders.domain.WEEKEND
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuleBlockTest {
    private val support =
        RuleSupport(
            setOf(
                "fileinto",
                "mailbox",
                "mailboxid",
                "body",
                "date",
                "relational",
                "spamtest",
                "comparator-i;ascii-numeric",
            ),
        )
    private val agency = RuleCondition.Address(AddressField.From, AddressMatch.Domain("website-agency.example"))
    private val invoice = RuleCondition.Text(TextField.Subject, TextOperator.Contains, "Invoice")
    private val projects =
        BlockFolder(
            "M7",
            "Clients/Projects",
            listOf(
                FolderRule(RuleCondition.Address(AddressField.From, AddressMatch.Exact("jana@website-agency.example"))),
                FolderRule(listOf(agency, invoice, RuleCondition.Size(over = true, bytes = 1_048_576))),
            ),
        )
    private val newsletters =
        BlockFolder(
            "M8",
            "Newsletters",
            listOf(FolderRule(RuleCondition.MailingList("releases.github.example"))),
        )
    private val block = RuleBlock(listOf(projects, newsletters), zone = "+0200")

    @Test
    fun foldersBecomeOneAnyOfTestEachThatFilesByIdAndStops() {
        val lines = encodeBlock(block, support).split("\r\n")
        assertEquals(
            listOf(
                "# BEGIN otus-rules",
                "# Otus Mail writes this block. Change these rules in Otus Mail; edits here are replaced.",
            ),
            lines.take(2),
        )
        assertTrue(lines[2].startsWith("# otus-rules-v2 {"), lines[2])
        assertEquals(
            listOf(
                """require ["fileinto", "mailbox", "mailboxid"];""",
                """if anyof (address :all :is "from" "jana@website-agency.example", allof (address :domain :is """ +
                    """"from" "website-agency.example", header :contains "subject" "Invoice", size :over 1048576)) {""",
                """    fileinto :mailboxid "M7" "Clients/Projects";""",
                "    stop;",
                "}",
                """if header :contains "list-id" "<releases.github.example>" {""",
                """    fileinto :mailboxid "M8" "Newsletters";""",
                "    stop;",
                "}",
                "# END otus-rules",
                "",
            ),
            lines.drop(3),
        )
    }

    @Test
    fun everyKindOfConditionHasItsSieveTest() {
        val tests =
            mapOf(
                RuleCondition.Address(AddressField.Cc, AddressMatch.LocalPart("billing"), negated = true) to
                    """not address :localpart :is "cc" "billing"""",
                RuleCondition.Text(TextField.Subject, TextOperator.StartsWith, "[Acme] *") to
                    """header :matches "subject" "[Acme] \\**"""",
                RuleCondition.Text(TextField.Subject, TextOperator.Matches, "Invoice #*") to
                    """header :matches "subject" "Invoice #*"""",
                RuleCondition.Text(TextField.Body, TextOperator.Contains, "project code \"WS-24\"") to
                    """body :text :contains "project code \"WS-24\""""",
                RuleCondition.Header("X-Project", HeaderOperator.Is, "website") to
                    """header :is "X-Project" "website"""",
                RuleCondition.Size(over = false, bytes = 10) to "size :under 10",
                RuleCondition.When(WEEKEND) to """currentdate :zone "+0200" :is "weekday" ["0", "6"]""",
                RuleCondition.When(WEEKDAYS, TimeRange(LocalTime(9, 0), LocalTime(18, 0)), outside = true) to
                    """allof (currentdate :zone "+0200" :is "weekday" ["1", "2", "3", "4", "5"], not allof """ +
                    """(currentdate :zone "+0200" :value "ge" "time" "09:00:00", currentdate :zone "+0200" """ +
                    """:value "lt" "time" "18:00:00"))""",
                RuleCondition.Spam(5) to """spamtest :value "ge" :comparator "i;ascii-numeric" "5"""",
            )
        tests.forEach { (condition, test) ->
            val folder = BlockFolder("F", "F", listOf(FolderRule(condition)))
            val lines = encodeBlock(RuleBlock(listOf(folder), "+0200"), support)
            assertTrue("if $test {" in lines, "$condition\n$lines")
        }
    }

    @Test
    fun theRequireLineDeclaresWhatTheConditionsUse() {
        val body = RuleCondition.Text(TextField.Body, TextOperator.Contains, "x")
        val rules = listOf(FolderRule(listOf(RuleCondition.Spam(5), body)))
        val text = encodeBlock(RuleBlock(listOf(BlockFolder("F", "F", rules))), support)
        assertTrue(
            """require ["body", "comparator-i;ascii-numeric", "fileinto", "mailbox", "mailboxid", """ +
                """"relational", "spamtest"];""" in text,
            text,
        )
    }

    @Test
    fun withoutMailboxIdFoldersAreFiledByTheirPath() {
        val text = encodeBlock(RuleBlock(listOf(projects)), RuleSupport(setOf("fileinto")))
        assertTrue("""require ["fileinto"];""" in text, text)
        assertTrue("""    fileinto "Clients/Projects";""" in text, text)
        assertFalse(":mailboxid" in text)
    }

    @Test
    fun anEmptyBlockHasNoSieve() {
        val lines = encodeBlock(RuleBlock(emptyList()), support).trimEnd().lines()
        assertEquals(listOf("# BEGIN otus-rules", "# END otus-rules"), listOf(lines.first(), lines.last()))
        assertEquals(4, lines.size)
    }

    @Test
    fun aBlockReadsBackAsTheSameRules() {
        val text = encodeBlock(block, support)
        val read = decodeBlock(text, support)
        assertEquals(block, read.block)
        assertFalse(read.changedElsewhere)
        assertFalse(decodeBlock(text.replace("\r\n", "\n"), support).changedElsewhere, "Line ends are no edit")
    }

    @Test
    fun anEditOutsideTheJsonCountsAsChangedElsewhere() {
        val edited = encodeBlock(block, support).replace("size :over 1048576", "size :over 2M")
        assertTrue(decodeBlock(edited, support).changedElsewhere)
        val unreadable = encodeBlock(block, support).replace("# otus-rules-v2 {", "# otus-rules-v2 {oops")
        assertEquals(ReadBlock(RuleBlock(emptyList()), changedElsewhere = true), decodeBlock(unreadable, support))
    }

    @Test
    fun controlCharactersAndQuotesAreEscapedButKeptInTheJson() {
        val odd = FolderRule(RuleCondition.Text(TextField.Subject, TextOperator.Contains, "a\"b\\c\u0007\nd"))
        val text = encodeBlock(RuleBlock(listOf(BlockFolder("F", "F", listOf(odd)))), support)
        assertTrue("""header :contains "subject" "a\"b\\c d"""" in text, text)
        assertEquals(
            listOf(odd),
            decodeBlock(text, support)
                .block.folders
                .single()
                .rules,
        )
        assertEquals(text.count { it == '\n' }, text.split("\r\n").size - 1, "Every line ends in CRLF")
    }
}

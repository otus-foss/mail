package com.otus.mail.feature.folders.presentation

import com.otus.mail.feature.folders.domain.AddressField
import com.otus.mail.feature.folders.domain.AddressMatch
import com.otus.mail.feature.folders.domain.FolderRule
import com.otus.mail.feature.folders.domain.HeaderOperator
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.TextField
import com.otus.mail.feature.folders.domain.TextOperator
import com.otus.mail.feature.folders.domain.TimeRange
import com.otus.mail.feature.folders.domain.WEEKDAYS
import com.otus.mail.feature.folders.domain.WEEKEND
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every example on the "Every kind of rule" board. */
class RuleChipTextTest {
    private fun dim(text: String) = ChipSegment(text, SegmentStyle.Dim)

    private fun bright(text: String) = ChipSegment(text, SegmentStyle.Bright)

    private fun accent(text: String) = ChipSegment(text, SegmentStyle.Accent)

    private fun icon(icon: RuleIcon) = ChipSlot.Icon(icon)

    private fun assertChip(
        condition: RuleCondition,
        label: String,
        slot: ChipSlot,
        vararg segments: ChipSegment,
    ) = assertEquals(RuleChipText(label, slot, segments.toList()), condition.chipText())

    private fun address(
        field: AddressField,
        match: AddressMatch,
        negated: Boolean = false,
    ) = RuleCondition.Address(field, match, negated)

    @Test
    fun senderAndRecipient() {
        val jana = "jana@website-agency.example"
        assertChip(
            address(AddressField.From, AddressMatch.Exact(jana)),
            "From",
            ChipSlot.Avatar(jana, jana),
            bright(jana),
        )
        assertChip(
            address(AddressField.From, AddressMatch.Domain("website-agency.example")),
            "From",
            ChipSlot.Avatar("website-agency.example", null),
            bright("@website-agency.example"),
        )
        assertChip(
            address(AddressField.From, AddressMatch.LocalPart("billing")),
            "From",
            icon(RuleIcon.AlternateEmail),
            bright("billing@"),
            dim(" any domain"),
        )
        val projects = "projects@rubylabs.design"
        assertChip(
            address(AddressField.To, AddressMatch.Exact(projects)),
            "To",
            ChipSlot.Avatar(projects, projects),
            bright(projects),
        )
        val team = "team@rubylabs.design"
        assertChip(address(AddressField.Cc, AddressMatch.Exact(team)), "Cc", ChipSlot.Avatar(team, team), bright(team))
    }

    @Test
    fun subjectHeadersAndBody() {
        val subject = icon(RuleIcon.ShortText)
        assertChip(
            RuleCondition.Text(TextField.Subject, TextOperator.Contains, "Website"),
            "Subject",
            subject,
            bright("“Website”"),
        )
        assertChip(
            RuleCondition.Text(TextField.Subject, TextOperator.StartsWith, "[Acme]"),
            "Subject",
            subject,
            dim("starts with "),
            bright("“[Acme]”"),
        )
        assertChip(
            RuleCondition.Text(TextField.Subject, TextOperator.Matches, "Invoice #*"),
            "Subject",
            subject,
            dim("matches "),
            bright("“Invoice #"),
            accent("*"),
            bright("”"),
        )
        assertChip(
            RuleCondition.Header("X-Project", HeaderOperator.Is, "website"),
            "Header",
            icon(RuleIcon.Code),
            bright("X-Project"),
            dim(" is "),
            bright("website"),
        )
        assertChip(
            RuleCondition.MailingList("releases.github.example"),
            "List",
            icon(RuleIcon.Forum),
            bright("releases.github.example"),
        )
        assertChip(
            RuleCondition.Text(TextField.Body, TextOperator.Contains, "project code WS-24"),
            "Body",
            icon(RuleIcon.Notes),
            bright("“project code WS-24”"),
        )
    }

    @Test
    fun sizeTimeAndSpam() {
        assertChip(
            RuleCondition.Size(over = true, bytes = 10L * 1024 * 1024),
            "Size",
            icon(RuleIcon.AttachFile),
            dim("over "),
            bright("10 MB"),
        )
        assertChip(
            RuleCondition.When(WEEKDAYS, TimeRange(LocalTime(9, 0), LocalTime(18, 0)), outside = true),
            "When",
            icon(RuleIcon.Schedule),
            dim("outside "),
            bright("9:00–18:00"),
            dim(" · weekdays"),
        )
        assertChip(RuleCondition.When(WEEKEND), "When", icon(RuleIcon.CalendarMonth), bright("Weekends"))
        assertChip(RuleCondition.Spam(5), "Spam", icon(RuleIcon.Report), bright("Likely spam"), dim(" · score 5+"))
    }

    @Test
    fun combining() {
        assertChip(
            RuleCondition.Text(TextField.Subject, TextOperator.Contains, "Unsubscribe", negated = true),
            "Subject",
            icon(RuleIcon.ShortText),
            dim("doesn't contain "),
            bright("“Unsubscribe”"),
        )
        val group =
            FolderRule(
                listOf(
                    address(AddressField.From, AddressMatch.Domain("website-agency.example")),
                    RuleCondition.Text(TextField.Subject, TextOperator.Contains, "Invoice"),
                    RuleCondition.Size(over = true, bytes = 1024L * 1024),
                ),
            )
        assertEquals(
            listOf(
                listOf(bright("@website-agency.example")),
                listOf(bright("“Invoice”")),
                listOf(dim("over "), bright("1 MB")),
            ),
            group.chipTexts().map { it.segments },
        )
        assertEquals(listOf("From", "Subject", "Size"), group.chipTexts().map { it.label })
    }

    @Test
    fun negationWords() {
        val jana = "jana@x.example"
        assertEquals(
            listOf(dim("isn't from "), bright(jana)),
            address(AddressField.From, AddressMatch.Exact(jana), negated = true).chipText().segments,
        )
        assertEquals(
            listOf(dim("isn't "), bright(jana)),
            address(AddressField.To, AddressMatch.Exact(jana), negated = true).chipText().segments,
        )
        assertEquals(
            listOf(bright("X-Project"), dim(" isn't "), bright("website")),
            RuleCondition.Header("X-Project", HeaderOperator.Is, "website", negated = true).chipText().segments,
        )
        assertEquals(
            "“Invoice #*”",
            RuleCondition
                .Text(TextField.Subject, TextOperator.Matches, "Invoice #*")
                .chipText()
                .value
                .drop(8),
        )
    }

    @Test
    fun sizesRoundToOneDecimal() {
        assertEquals("1.5 MB", sizeText(1_572_864))
        assertEquals("200 KB", sizeText(204_800))
        assertEquals("500 bytes", sizeText(500))
    }
}

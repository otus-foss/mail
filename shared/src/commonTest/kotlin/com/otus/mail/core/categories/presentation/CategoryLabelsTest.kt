package com.otus.mail.core.categories.presentation

import com.otus.mail.core.categories.domain.CategoryPlacement
import com.otus.mail.core.categories.domain.CategoryReason
import com.otus.mail.core.categories.domain.MailCategory
import com.otus.mail.core.categories.domain.ReasonKind
import kotlin.test.Test
import kotlin.test.assertEquals

class CategoryLabelsTest {
    @Test
    fun theExplanationNamesTheCategoryAndEachReason() {
        val placement =
            CategoryPlacement(
                MailCategory.Newsletters,
                listOf(
                    CategoryReason(ReasonKind.ListHeaders, "List-Unsubscribe, Precedence: bulk"),
                    CategoryReason(ReasonKind.Keywords, "% off"),
                ),
            )
        assertEquals(
            "In Newsletters & promotions. It has mailing list headers: List-Unsubscribe, Precedence: bulk. " +
                "Its subject or text says “% off”.",
            placement.explanation(),
        )
    }

    @Test
    fun eachReasonReadsAsASentence() {
        val sentences =
            mapOf(
                CategoryReason(ReasonKind.SenderChoice, "a@b.example") to
                    "You chose this category for mail from a@b.example.",
                CategoryReason(ReasonKind.HiddenCategory, "notifications") to
                    "Notifications is hidden, so Primary shows it.",
                CategoryReason(ReasonKind.StructuredData, "Order") to "It has schema.org Order markup.",
                CategoryReason(ReasonKind.KnownSender, "github.com") to
                    "github.com is on Mail's list of known senders.",
                CategoryReason(ReasonKind.Automated, "no-reply@x.example") to
                    "It comes from no-reply@x.example, an address that sends automated mail.",
                CategoryReason(ReasonKind.Automated, "Feedback-ID") to "It has automated mail headers: Feedback-ID.",
                CategoryReason(ReasonKind.NoSignal) to "Nothing marks it as automated mail.",
            )
        sentences.forEach { (reason, sentence) -> assertEquals(sentence, reason.sentence()) }
    }

    @Test
    fun timeSensitiveMailSaysPrimaryShowsItToo() {
        val chosen =
            CategoryPlacement(
                MailCategory.Transactions,
                listOf(CategoryReason(ReasonKind.SenderChoice, "a@b.example")),
                timeSensitive = true,
            )
        assertEquals(
            "In Transactions. You chose this category for mail from a@b.example. Primary shows it too.",
            chosen.explanation(),
        )
    }
}

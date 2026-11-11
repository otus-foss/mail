package com.otusfoss.mail.feature.inbox.domain.category

import com.otusfoss.mail.core.categories.domain.CategoryReason
import com.otusfoss.mail.core.categories.domain.MailCategory
import com.otusfoss.mail.core.categories.domain.ReasonKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Mail that looks like a category's at a glance but is not, and codes that are easy to miss. */
class CategoryFalseAlarmsTest {
    @Test
    fun aYearBesideTheWordCodeIsNoSignInCode() {
        val verdict = classify(PersonalMail.copy(subject = "Code freeze 2026 starts Monday"))
        assertEquals(MailCategory.Primary, verdict.category)
        assertFalse(verdict.timeSensitive)
    }

    @Test
    fun aPersonsNoteAboutTwoFactorSignInStaysInPrimary() {
        val note = PersonalMail.copy(subject = "Quick question", text = "Did you turn on two-factor for our account?")
        assertEquals(listOf(CategoryReason(ReasonKind.NoSignal)), classify(note).reasons)
    }

    @Test
    fun aNewsletterWithAYearInItsSubjectIsNotTimeSensitive() {
        val yearly = PasswordNewsletter.copy(subject = "Your 2026 password reset checklist")
        assertFalse(classify(yearly).timeSensitive)
    }

    @Test
    fun codesBesideTheWordCodeAreFound() {
        listOf(
            "482913 is your Instagram code",
            "Your code: 731942",
            "G-482913 is your Google verification code",
        ).forEach { subject ->
            assertTrue(classify(CodeInSubject.copy(subject = subject)).timeSensitive, subject)
        }
        assertFalse(classify(CodeInSubject.copy(subject = "Code review for PR 4821")).timeSensitive)
    }

    @Test
    fun aListedSenderCountsOnlyAfterADmarcPass() {
        val spoofed = classify(KnownShopOrder.copy(senderVerified = false))
        assertEquals(MailCategory.Primary, spoofed.category)
        assertTrue(spoofed.reasons.none { it.kind == ReasonKind.KnownSender })
    }

    @Test
    fun aTransactionPhraseOutweighsSalesWords() {
        val pass =
            Newsletter.copy(
                senderAddress = "trips@airline.example",
                subject = "Boarding pass inside, and 20% off",
            )
        assertEquals(MailCategory.Transactions, classify(pass).category)
    }
}

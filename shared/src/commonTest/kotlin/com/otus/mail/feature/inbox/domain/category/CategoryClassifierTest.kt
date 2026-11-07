package com.otus.mail.feature.inbox.domain.category

import com.otus.mail.core.categories.domain.CategoryReason
import com.otus.mail.core.categories.domain.MailCategory
import com.otus.mail.core.categories.domain.ReasonKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CategoryClassifierTest {
    @Test
    fun eachSampleLandsInItsCategory() {
        val expected =
            mapOf(
                PersonalMail to MailCategory.Primary,
                MarkedUpReceipt to MailCategory.Transactions,
                FlightBooking to MailCategory.Transactions,
                ShippingNotice to MailCategory.Transactions,
                NumberedInvoice to MailCategory.Transactions,
                KnownShopOrder to MailCategory.Transactions,
                KnownShopSale to MailCategory.Newsletters,
                SaleMail to MailCategory.Newsletters,
                Newsletter to MailCategory.Newsletters,
                SocialNotification to MailCategory.Notifications,
                CiFailure to MailCategory.Notifications,
                AccountNotice to MailCategory.Notifications,
                SecurityCode to MailCategory.Notifications,
            )
        expected.forEach { (signals, category) ->
            assertEquals(category, classify(signals).category, signals.subject)
        }
    }

    @Test
    fun aNewsletterThatReadsLikeAReceiptStaysANewsletter() {
        val verdict = classify(ReceiptLookingNewsletter)
        assertEquals(MailCategory.Newsletters, verdict.category)
        assertEquals(
            listOf(CategoryReason(ReasonKind.ListHeaders, "List-Unsubscribe, List-Id, Precedence: bulk")),
            verdict.reasons,
        )
    }

    @Test
    fun salesWordsWinOverOrderWordsInListMail() {
        val verdict = classify(SaleWithOrderWords)
        assertEquals(MailCategory.Newsletters, verdict.category)
        assertEquals(CategoryReason(ReasonKind.Keywords, "% off"), verdict.reasons.last())
    }

    @Test
    fun aPersonOnADiscussionListStaysInPrimary() {
        val verdict = classify(DiscussionListReply)
        assertEquals(MailCategory.Primary, verdict.category)
        assertEquals(
            listOf(CategoryReason(ReasonKind.DiscussionList, "Compilers discussion <compilers.lists.example>")),
            verdict.reasons,
        )
    }

    @Test
    fun personalMailWithShopWordsStaysInPrimary() {
        listOf(PersonalMail, PersonalWithTriggerWords, LookalikeDomain).forEach { signals ->
            val verdict = classify(signals)
            assertEquals(MailCategory.Primary, verdict.category, signals.subject)
            assertEquals(listOf(CategoryReason(ReasonKind.NoSignal)), verdict.reasons)
        }
    }

    @Test
    fun anOutOfOfficeReplyStaysInPrimary() {
        val verdict = classify(OutOfOffice)
        assertEquals(MailCategory.Primary, verdict.category)
        assertEquals(listOf(CategoryReason(ReasonKind.AutoReply, "auto-replied")), verdict.reasons)
    }

    @Test
    fun codesAndPasswordResetsAreTimeSensitive() {
        listOf(SecurityCode, CodeInSubject, ShopPasswordReset).forEach { signals ->
            val verdict = classify(signals)
            assertEquals(MailCategory.Notifications, verdict.category, signals.subject)
            assertTrue(verdict.timeSensitive, signals.subject)
        }
        val code = classify(SecurityCode).reasons.single()
        assertEquals(CategoryReason(ReasonKind.TimeSensitive, "verification code"), code)
        assertEquals(CategoryReason(ReasonKind.TimeSensitive, "login code"), classify(CodeInSubject).reasons.single())
    }

    @Test
    fun aCodeOnlyInTheTextIsTimeSensitiveToo() {
        val generic = SecurityCode.copy(subject = "Action required", text = "Your one-time code is 482913.")
        assertTrue(classify(generic).timeSensitive)
    }

    @Test
    fun codesAndResetsFromBulkSendersStayTimeSensitive() {
        listOf(BulkPasswordReset, BulkCodeInSubject).forEach { signals ->
            val verdict = classify(signals)
            assertTrue(verdict.timeSensitive, signals.subject)
            assertEquals(MailCategory.Notifications, verdict.category, signals.subject)
        }
        val textOnly = BulkPasswordReset.copy(subject = "Account news", text = "Reset your password any time.")
        assertFalse(classify(textOnly).timeSensitive, "List mail needs the words in its subject")
    }

    @Test
    fun aForgeHeaderMakesANotificationOnAnyServer() {
        val verdict = classify(SelfHostedGitLab)
        assertEquals(MailCategory.Notifications, verdict.category)
        assertEquals(listOf(CategoryReason(ReasonKind.Automated, "X-GitLab-Project")), verdict.reasons)
        val withoutForge = SelfHostedGitLab.copy(headers = SelfHostedGitLab.headers.copy(forge = null))
        assertEquals(MailCategory.Newsletters, classify(withoutForge).category, "List mail from gitlab@ alone is bulk")
    }

    @Test
    fun listMailAboutPasswordsIsNotTimeSensitive() {
        val verdict = classify(PasswordNewsletter)
        assertFalse(verdict.timeSensitive)
        assertEquals(MailCategory.Newsletters, verdict.category)
    }

    @Test
    fun markupComesBeforeEverythingElse() {
        val listed = MarkedUpReceipt.copy(subject = "50% off", headers = BulkHeaders(listUnsubscribe = true))
        val verdict = classify(listed)
        assertEquals(MailCategory.Transactions, verdict.category)
        assertEquals(listOf(CategoryReason(ReasonKind.StructuredData, "Order")), verdict.reasons)
        val offer = PersonalMail.copy(markup = setOf("DiscountOffer"))
        assertEquals(MailCategory.Newsletters, classify(offer).category)
    }

    @Test
    fun aKnownShopsListMailWithAnOrderIsATransaction() {
        val listedOrder = KnownShopOrder.copy(headers = BulkHeaders(listUnsubscribe = true))
        assertEquals(MailCategory.Transactions, classify(listedOrder).category)
        assertEquals(
            listOf(
                CategoryReason(ReasonKind.KnownSender, "amazon.com"),
                CategoryReason(ReasonKind.ListHeaders, "List-Unsubscribe, Precedence: bulk"),
            ),
            classify(KnownShopSale).reasons,
        )
    }

    @Test
    fun theSenderListCoversSubdomains() {
        val verdict = classify(SocialNotification.copy(senderAddress = "messages-noreply@bounce.linkedin.com"))
        assertEquals(MailCategory.Notifications, verdict.category)
        assertEquals(listOf(CategoryReason(ReasonKind.KnownSender, "linkedin.com")), verdict.reasons)
    }

    @Test
    fun automationReasonsNameTheHeadersOrTheAddress() {
        assertEquals(
            listOf(
                CategoryReason(ReasonKind.Automated, "no-reply@carrier.example"),
                CategoryReason(ReasonKind.Keywords, "out for delivery"),
            ),
            classify(ShippingNotice).reasons,
        )
        assertEquals(
            listOf(CategoryReason(ReasonKind.Automated, "Auto-Submitted: auto-generated")),
            classify(AccountNotice).reasons,
        )
        val sent = AccountNotice.copy(headers = BulkHeaders(feedbackId = "1:2", platforms = listOf("SendGrid")))
        assertEquals(CategoryReason(ReasonKind.Automated, "Feedback-ID, SendGrid"), classify(sent).reasons.single())
    }

    @Test
    fun anInvoiceWithoutANumberFromAPersonStaysInPrimary() {
        val freelancer = PersonalMail.copy(subject = "Invoice for the logo work")
        assertEquals(MailCategory.Primary, classify(freelancer).category)
    }

    @Test
    fun verdictsNameTheRulesAndListVersions() {
        assertEquals("$CLASSIFIER_VERSION.3", classify(PersonalMail).version)
        assertEquals("$CLASSIFIER_VERSION.0", CategoryClassifier(SenderDirectory.Empty).classify(PersonalMail).version)
    }

    @Test
    fun withoutTheSenderListKnownSendersFallBackToTheirHeaders() {
        val classifier = CategoryClassifier(SenderDirectory.Empty)
        val ci = classifier.classify(CiFailure)
        assertEquals(MailCategory.Notifications, ci.category)
        assertEquals(CategoryReason(ReasonKind.Automated, "notifications@github.com"), ci.reasons.last())
        assertEquals(MailCategory.Primary, classifier.classify(KnownShopOrder).category)
    }
}

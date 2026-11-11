package com.otusfoss.mail.feature.inbox.domain.category

import com.otusfoss.mail.core.categories.domain.CategoryReason
import com.otusfoss.mail.core.categories.domain.CategoryVerdict
import com.otusfoss.mail.core.categories.domain.MailCategory
import com.otusfoss.mail.core.categories.domain.ReasonKind

/**
 * Sorts one message into a [MailCategory] on the device, from what [CategorySignals] holds. It is
 * deterministic and keeps its reasons, so the app can say why a message is where it is. Each step
 * decides only when its evidence is clear, and anything left is Primary:
 *
 * 1. schema.org markup in a downloaded body: an Order, Invoice, ParcelDelivery, or Reservation.
 * 2. A subject or text asking for quick action, such as a sign-in code or a password reset. Mail
 *    a machine sent counts with the words in its subject or text. List mail counts only with them
 *    in the subject, from a no-reply address or with a code in it, and other mail only with a code
 *    beside the word "code". It goes to Notifications and always stays in Primary too.
 * 3. A sender domain on the curated [senders] list, once DMARC passed for it. A listed shop's list
 *    mail without an order in its subject is bulk mail.
 * 4. A code forge's own header, such as `X-GitHub-Reason`, from any server: a notification.
 * 5. `List-Post`: a discussion list, where people write, stays in Primary.
 * 6. List headers: an order or delivery in the subject, without sales words, is a transaction,
 *    mail from a `notifications@` address a notification, anything else a newsletter.
 * 7. `Auto-Submitted: auto-replied` from a person stays in Primary.
 * 8. Other automation, such as `Auto-Submitted`, a bulk platform's header, or a no-reply address:
 *    an order or delivery in the subject or text is a transaction, anything else a notification.
 *
 * The user's own choice for a sender comes before all of this; see `InboxCategories.placement`.
 */
class CategoryClassifier(
    private val senders: SenderDirectory,
) {
    /** The rules' and the sender list's versions; a verdict of another version is made again. */
    val version: String
        get() = "$CLASSIFIER_VERSION.${senders.version}"

    fun classify(signals: CategorySignals): CategoryVerdict {
        val verdict =
            markup(signals)
                ?: timeSensitive(signals)
                ?: knownSender(signals)
                ?: forge(signals.headers)
                ?: discussion(signals.headers)
                ?: list(signals)
                ?: autoReply(signals.headers)
                ?: automated(signals)
                ?: CategoryVerdict(MailCategory.Primary, listOf(CategoryReason(ReasonKind.NoSignal)))
        return verdict.copy(version = version)
    }

    private fun markup(signals: CategorySignals): CategoryVerdict? {
        val transaction = TRANSACTION_TYPES.firstOrNull { it in signals.markup }
        val promotion = PROMOTION_TYPES.firstOrNull { it in signals.markup }
        return when {
            transaction != null -> verdict(MailCategory.Transactions, ReasonKind.StructuredData, transaction)
            promotion != null -> verdict(MailCategory.Newsletters, ReasonKind.StructuredData, promotion)
            else -> null
        }
    }

    private fun timeSensitive(signals: CategorySignals): CategoryVerdict? {
        val found = urgentWords(signals) ?: return null
        return verdict(MailCategory.Notifications, ReasonKind.TimeSensitive, found).copy(timeSensitive = true)
    }

    /**
     * The words that make [signals] time-sensitive. A bulk sender's mail counts only with them in
     * the subject and a machine behind it, so a newsletter about passwords is not urgent. Mail that
     * nothing marks as automated counts only with a code in its subject, so a colleague's "Code
     * freeze 2026" or a friend's note about two-factor sign-in stays in Primary alone.
     */
    private fun urgentWords(signals: CategorySignals): String? {
        val subject = signals.subject
        val words = firstPhrase(subject, TIME_SENSITIVE_PHRASES) ?: CODE_WORD.takeIf { CODE_BESIDE_DIGITS in subject }
        val code = DIGIT_CODE in subject
        return when {
            signals.headers.fromList -> words?.takeIf { code || automatedAddress(signals.senderAddress) }
            machineSent(signals) -> words ?: firstPhrase(signals.text.take(MAX_TEXT_CHARS), TIME_SENSITIVE_PHRASES)
            else -> words?.takeIf { code }
        }
    }

    /** Headers, a no-reply address, or a verified listed sender say a machine sent [signals]. */
    private fun machineSent(signals: CategorySignals): Boolean =
        signals.headers.automationFields.isNotEmpty() ||
            signals.headers.forge != null ||
            automatedAddress(signals.senderAddress) ||
            verifiedListing(signals) != null

    /** The listed domain and category of [signals]' sender, once DMARC passed for it. */
    private fun verifiedListing(signals: CategorySignals): Pair<String, MailCategory>? =
        signals.takeIf { it.senderVerified }?.let { senders.find(it.senderAddress) }

    private fun knownSender(signals: CategorySignals): CategoryVerdict? {
        val (domain, category) = verifiedListing(signals) ?: return null
        val known = CategoryReason(ReasonKind.KnownSender, domain)
        val headers = signals.headers
        return if (category == MailCategory.Transactions && headers.fromList && transaction(signals.subject) == null) {
            CategoryVerdict(MailCategory.Newsletters, listOf(known, listReason(headers)))
        } else {
            CategoryVerdict(category, listOf(known))
        }
    }

    private fun list(signals: CategorySignals): CategoryVerdict? {
        val headers = signals.headers
        if (!headers.fromList) return null
        val promotion = firstPhrase(signals.subject, PROMOTION_WORDS)
        // A phrase such as "boarding pass" stands on its own; sales words only outweigh a bare word.
        val ordered =
            firstPhrase(signals.subject, TRANSACTION_PHRASES)
                ?: transaction(signals.subject)?.takeIf { promotion == null }
        val listed = listReason(headers)
        val sender = signals.senderAddress
        return when {
            ordered != null -> CategoryVerdict(MailCategory.Transactions, listOf(keywords(ordered), listed))
            promotion != null -> CategoryVerdict(MailCategory.Newsletters, listOf(listed, keywords(promotion)))
            notifier(sender) -> CategoryVerdict(MailCategory.Notifications, listOf(listed, notice(sender)))
            else -> CategoryVerdict(MailCategory.Newsletters, listOf(listed))
        }
    }

    private fun automated(signals: CategorySignals): CategoryVerdict? {
        val fields = signals.headers.automationFields
        val detail =
            fields.joinToString(", ").ifEmpty { null }
                ?: signals.senderAddress.takeIf(::automatedAddress)
                ?: return null
        val automation = CategoryReason(ReasonKind.Automated, detail)
        val transaction =
            transaction(signals.subject) ?: firstPhrase(signals.text.take(MAX_TEXT_CHARS), TRANSACTION_PHRASES)
        return if (transaction != null) {
            CategoryVerdict(MailCategory.Transactions, listOf(automation, keywords(transaction)))
        } else {
            CategoryVerdict(MailCategory.Notifications, listOf(automation))
        }
    }

    private companion object {
        /** How much of a message's text the word steps read. */
        const val MAX_TEXT_CHARS = 4_000
        const val CODE_WORD = "code"
    }
}

/** The classifier's own rules; bump it with any rule change so every message is classified again. */
const val CLASSIFIER_VERSION = 2

private const val AUTO_REPLIED = "auto-replied"

/** The words in [subject] that name a transaction: a phrase on its own, or a word beside a number or amount. */
private fun transaction(subject: String): String? =
    firstPhrase(subject, TRANSACTION_PHRASES)
        ?: firstPhrase(subject, TRANSACTION_WORDS)?.takeIf { REFERENCE.containsMatchIn(subject) }

private fun forge(headers: BulkHeaders): CategoryVerdict? =
    headers.forge?.let { verdict(MailCategory.Notifications, ReasonKind.Automated, it) }

private fun discussion(headers: BulkHeaders): CategoryVerdict? {
    val post = headers.listPost?.takeIf { it.isNotBlank() } ?: return null
    return verdict(MailCategory.Primary, ReasonKind.DiscussionList, headers.listId ?: post)
}

private fun autoReply(headers: BulkHeaders): CategoryVerdict? {
    if (headers.autoSubmittedValue != AUTO_REPLIED) return null
    return verdict(MailCategory.Primary, ReasonKind.AutoReply, AUTO_REPLIED)
}

private fun listReason(headers: BulkHeaders): CategoryReason =
    CategoryReason(ReasonKind.ListHeaders, headers.listFields.joinToString(", "))

private fun notice(sender: String) = CategoryReason(ReasonKind.Automated, sender)

private fun keywords(found: String) = CategoryReason(ReasonKind.Keywords, found)

private fun verdict(
    category: MailCategory,
    kind: ReasonKind,
    detail: String,
) = CategoryVerdict(category, listOf(CategoryReason(kind, detail)))

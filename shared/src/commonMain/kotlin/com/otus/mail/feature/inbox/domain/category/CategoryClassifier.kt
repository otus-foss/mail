package com.otus.mail.feature.inbox.domain.category

/**
 * Sorts one message into a [MailCategory] on the device, from what [CategorySignals] holds. It is
 * deterministic and keeps its reasons, so the app can say why a message is where it is. Each step
 * decides only when its evidence is clear, and anything left is Primary:
 *
 * 1. schema.org markup in a downloaded body: an Order, Invoice, ParcelDelivery, or Reservation.
 * 2. A subject asking for quick action, such as a sign-in code, from mail that is not a list. It goes
 *    to Updates and stays in Primary too.
 * 3. A sender domain on the curated [senders] list. A listed shop's list mail without an order in
 *    its subject is a promotion.
 * 4. `List-Post`: a discussion list, where people write, stays in Primary.
 * 5. List headers: an order or delivery in the subject is a transaction, sales words a promotion,
 *    mail from a `notifications@` address an update, anything else a newsletter.
 * 6. `Auto-Submitted: auto-replied` from a person stays in Primary.
 * 7. Other automation, such as `Auto-Submitted`, a bulk platform's header, or a no-reply address:
 *    an order or delivery in the subject or text is a transaction, anything else an update.
 *
 * The user's own choice for a sender comes before all of this; see [InboxCategories.placement].
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
            promotion != null -> verdict(MailCategory.Promotions, ReasonKind.StructuredData, promotion)
            else -> null
        }
    }

    private fun timeSensitive(signals: CategorySignals): CategoryVerdict? {
        val found = urgentWords(signals).takeUnless { signals.headers.fromList } ?: return null
        return verdict(MailCategory.Updates, ReasonKind.TimeSensitive, found).copy(timeSensitive = true)
    }

    private fun urgentWords(signals: CategorySignals): String? =
        firstPhrase(signals.subject, TIME_SENSITIVE_PHRASES)
            ?: CODE_WORD.takeIf { codeInSubject(signals.subject) }
            ?: firstPhrase(signals.text.take(MAX_TEXT_CHARS), TIME_SENSITIVE_PHRASES)

    private fun knownSender(signals: CategorySignals): CategoryVerdict? {
        val (domain, category) = senders.find(signals.senderAddress) ?: return null
        val known = CategoryReason(ReasonKind.KnownSender, domain)
        val headers = signals.headers
        return if (category == MailCategory.Transactions && headers.fromList && transaction(signals.subject) == null) {
            CategoryVerdict(MailCategory.Promotions, listOf(known, listReason(headers)))
        } else {
            CategoryVerdict(category, listOf(known))
        }
    }

    private fun discussion(headers: BulkHeaders): CategoryVerdict? {
        val post = headers.listPost?.takeIf { it.isNotBlank() } ?: return null
        return verdict(MailCategory.Primary, ReasonKind.DiscussionList, headers.listId ?: post)
    }

    private fun list(signals: CategorySignals): CategoryVerdict? {
        val headers = signals.headers
        if (!headers.fromList) return null
        val promotion = firstPhrase(signals.subject, PROMOTION_WORDS)
        val ordered = transaction(signals.subject)?.takeIf { promotion == null }
        val listed = listReason(headers)
        return when {
            ordered != null -> CategoryVerdict(MailCategory.Transactions, listOf(keywords(ordered), listed))
            promotion != null -> CategoryVerdict(MailCategory.Promotions, listOf(listed, keywords(promotion)))
            notifier(signals.senderAddress) -> CategoryVerdict(MailCategory.Updates, listOf(listed, notice(signals)))
            else -> CategoryVerdict(MailCategory.Newsletters, listOf(listed))
        }
    }

    private fun autoReply(headers: BulkHeaders): CategoryVerdict? {
        if (headers.autoSubmittedValue != AUTO_REPLIED) return null
        return verdict(MailCategory.Primary, ReasonKind.AutoReply, AUTO_REPLIED)
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
            CategoryVerdict(MailCategory.Updates, listOf(automation))
        }
    }

    private companion object {
        /** How much of a message's text the word steps read. */
        const val MAX_TEXT_CHARS = 4_000
        const val CODE_WORD = "code"
        const val AUTO_REPLIED = "auto-replied"
    }
}

/** The classifier's own rules; bump it with any rule change so every message is classified again. */
const val CLASSIFIER_VERSION = 1

/** The words in [subject] that name a transaction: a phrase on its own, or a word beside a number or amount. */
private fun transaction(subject: String): String? =
    firstPhrase(subject, TRANSACTION_PHRASES)
        ?: firstPhrase(subject, TRANSACTION_WORDS)?.takeIf { REFERENCE.containsMatchIn(subject) }

private fun codeInSubject(subject: String): Boolean =
    firstPhrase(subject, listOf("code")) != null && DIGIT_CODE.containsMatchIn(subject)

private fun listReason(headers: BulkHeaders): CategoryReason =
    CategoryReason(ReasonKind.ListHeaders, headers.listFields.joinToString(", "))

private fun notice(signals: CategorySignals) = CategoryReason(ReasonKind.Automated, signals.senderAddress)

private fun keywords(found: String) = CategoryReason(ReasonKind.Keywords, found)

private fun verdict(
    category: MailCategory,
    kind: ReasonKind,
    detail: String,
) = CategoryVerdict(category, listOf(CategoryReason(kind, detail)))

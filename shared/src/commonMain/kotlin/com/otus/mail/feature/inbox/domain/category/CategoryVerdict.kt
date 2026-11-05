package com.otus.mail.feature.inbox.domain.category

/**
 * The classifier's answer for one message: its [category], the [reasons] that decided it, most
 * telling first, and whether it is [timeSensitive], such as a sign-in code, which Primary shows as
 * well. [version] names the classifier and sender list that made it, so a newer one classifies the
 * message again.
 */
data class CategoryVerdict(
    val category: MailCategory,
    val reasons: List<CategoryReason>,
    val timeSensitive: Boolean = false,
    val version: String = "",
)

/**
 * One fact behind a verdict. [detail] holds what the fact was about, such as the schema.org type,
 * the header names, the sender domain, or the words found; the app turns it into a sentence.
 */
data class CategoryReason(
    val kind: ReasonKind,
    val detail: String = "",
)

/** The kinds of facts a verdict rests on, in the order the classifier looks for them. */
enum class ReasonKind(
    val storageValue: String,
) {
    /** The user chose the category for mail from this sender. */
    SenderChoice("sender"),

    /** The category the message is in is hidden, so Primary shows it. */
    HiddenCategory("hidden"),

    /** schema.org markup in the body, such as an Order. */
    StructuredData("markup"),

    /** The subject asks for quick action: a code, a sign-in link, a password reset. */
    TimeSensitive("urgent"),

    /** The sender's domain is on the curated sender list. */
    KnownSender("known"),

    /** `List-Post`: a discussion list where people write to each other. */
    DiscussionList("discussion"),

    /** `List-Unsubscribe`, `List-Id`, or `Precedence: bulk`/`list`. */
    ListHeaders("list"),

    /** `Auto-Submitted: auto-replied`, such as an out-of-office reply from a person. */
    AutoReply("autoreply"),

    /** `Auto-Submitted`, a bulk mail platform's header, or a no-reply address. */
    Automated("automated"),

    /** Words in the subject, which only count beside one of the signals above. */
    Keywords("words"),

    /** Nothing marks the message as automated. */
    NoSignal("none"),
    ;

    companion object {
        fun fromStorageValue(value: String?): ReasonKind? = entries.firstOrNull { it.storageValue == value }
    }
}

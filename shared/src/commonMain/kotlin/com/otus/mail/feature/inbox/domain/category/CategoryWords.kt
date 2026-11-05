package com.otus.mail.feature.inbox.domain.category

// The words the classifier looks for. They only decide between categories once a header or the
// sender already marks the mail as automated, and a personal message with them stays in Primary.
// English only for now; see docs/categories.md.

/** Subjects that ask the user to act within minutes: codes, sign-in links, and password resets. */
internal val TIME_SENSITIVE_PHRASES =
    listOf(
        "verification code",
        "security code",
        "confirmation code",
        "one-time code",
        "one time code",
        "one-time password",
        "login code",
        "sign-in code",
        "sign in code",
        "two-factor",
        "2fa",
        "password reset",
        "reset your password",
        "sign-in link",
        "login link",
        "magic link",
        "verify your email",
        "confirm your email",
        "new sign-in",
        "new login",
    )

/** Subjects that name a transaction on their own. */
internal val TRANSACTION_PHRASES =
    listOf(
        "order confirmation",
        "booking confirmation",
        "reservation confirmed",
        "has shipped",
        "was shipped",
        "out for delivery",
        "was delivered",
        "tracking number",
        "boarding pass",
        "e-ticket",
        "itinerary",
        "payment received",
        "payment confirmation",
        "refund",
    )

/** Subject words that name a transaction when the subject also carries a number or an amount. */
internal val TRANSACTION_WORDS =
    listOf("receipt", "invoice", "order", "booking", "reservation", "payment", "bill", "statement", "ticket")

/** Subject words of marketing mail. */
internal val PROMOTION_WORDS =
    listOf(
        "% off",
        "sale",
        "discount",
        "deal",
        "deals",
        "coupon",
        "promo",
        "offer",
        "free shipping",
        "limited time",
        "last chance",
        "black friday",
        "cyber monday",
        "save",
        "exclusive",
        "new arrivals",
        "shop now",
        "ends tonight",
    )

/** Parts of an address before the @ that only machines send from. */
internal val AUTOMATED_LOCAL_PARTS =
    setOf(
        "noreply",
        "no-reply",
        "donotreply",
        "do-not-reply",
        "notifications",
        "notification",
        "notify",
        "alerts",
        "alert",
        "mailer-daemon",
        "automated",
        "orders",
        "receipts",
        "billing",
        "invoices",
        "payments",
        "shipping",
        "tracking",
        "bookings",
        "reservations",
    )

/** Parts of an address before the @ that send notices, not newsletters, even with list headers. */
internal val NOTIFIER_LOCAL_PARTS = setOf("notifications", "notification", "notify", "alerts", "alert")

/** An order or invoice number, a long number, or an amount of money. */
internal val REFERENCE = Regex("""#\s?[A-Za-z]{0,6}\d|\d{4,}|[$€£¥]\s?\d|\d[.,]\d{2}\s?(?:€|eur|usd|chf|gbp)""")

/** A code of four to eight digits, as sign-in mail puts in its subject. */
internal val DIGIT_CODE = Regex("""(?<!\d)\d{4,8}(?!\d)""")

/** The first of [phrases] that [text] holds as whole words, ignoring case; null when none is. */
internal fun firstPhrase(
    text: String,
    phrases: List<String>,
): String? {
    val lower = text.lowercase()
    return phrases.firstOrNull { phrase -> lower.hasWords(phrase) }
}

private fun String.hasWords(phrase: String): Boolean =
    generateSequence(indexOf(phrase)) { indexOf(phrase, it + 1).takeIf { next -> next >= 0 } }
        .takeWhile { it >= 0 }
        .any { start -> startsWord(phrase, start) && !letterAt(start + phrase.length) }

/** A phrase such as "% off" may follow a digit; one that starts with a letter must start a word. */
private fun String.startsWord(
    phrase: String,
    start: Int,
): Boolean = !phrase.first().isLetterOrDigit() || !letterAt(start - 1)

private fun String.letterAt(index: Int): Boolean = index in indices && this[index].isLetterOrDigit()

/** Whether [address] is one only machines send from, such as `no-reply@` or `orders@`. */
internal fun automatedAddress(address: String): Boolean {
    val local = address.substringBefore('@').lowercase()
    return local in AUTOMATED_LOCAL_PARTS ||
        local.split('.', '_', '+', '-').any { it in AUTOMATED_LOCAL_PARTS } ||
        listOf("noreply", "no-reply", "donotreply").any { it in local }
}

/** Whether [address] sends notices, such as `notifications@`, which list mail from it makes an update. */
internal fun notifier(address: String): Boolean =
    address
        .substringBefore('@')
        .lowercase()
        .split('.', '_', '+', '-')
        .any { it in NOTIFIER_LOCAL_PARTS }

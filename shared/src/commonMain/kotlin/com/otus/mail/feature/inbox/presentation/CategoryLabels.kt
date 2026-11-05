package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.category.CategoryPlacement
import com.otus.mail.feature.inbox.domain.category.CategoryReason
import com.otus.mail.feature.inbox.domain.category.MailCategory
import com.otus.mail.feature.inbox.domain.category.ReasonKind

/** The category as its pill, the settings, and the menu name it. */
val MailCategory.label: String
    get() =
        when (this) {
            MailCategory.Primary -> "Primary"
            MailCategory.Transactions -> "Transactions"
            MailCategory.Notifications -> "Notifications"
            MailCategory.Newsletters -> "Newsletters & promotions"
        }

/** What the category holds, under its name in settings. */
val MailCategory.description: String
    get() =
        when (this) {
            MailCategory.Primary -> "People, and anything Mail is unsure of"
            MailCategory.Transactions -> "Receipts, orders, bookings, bills, and deliveries"
            MailCategory.Notifications -> "Account alerts, CI, and social networks"
            MailCategory.Newsletters -> "Newsletters, marketing, and other bulk mail"
        }

/** One reason as a sentence, for "Why is this here?". */
fun CategoryReason.sentence(): String =
    when (kind) {
        ReasonKind.SenderChoice -> "You chose this category for mail from $detail."
        ReasonKind.HiddenCategory -> "${hiddenLabel(detail)} is hidden, so Primary shows it."
        ReasonKind.StructuredData -> "It has schema.org $detail markup."
        ReasonKind.TimeSensitive -> "It looks time-sensitive (“$detail”), so Primary shows it too."
        ReasonKind.KnownSender -> "$detail is on Mail's list of known senders."
        ReasonKind.DiscussionList -> "It came from the discussion list $detail, where people write to each other."
        ReasonKind.ListHeaders -> "It has mailing list headers: $detail."
        ReasonKind.AutoReply -> "It is an automatic reply from a person."
        ReasonKind.Automated -> automatedSentence(detail)
        ReasonKind.Keywords -> "Its subject or text says “$detail”."
        ReasonKind.NoSignal -> "Nothing marks it as automated mail."
    }

/** Why a message is where it is: where it shows, then each reason. */
fun CategoryPlacement.explanation(): String =
    buildList {
        add("In ${category.label}.")
        addAll(reasons.map { it.sentence() })
        if (timeSensitive && reasons.none { it.kind == ReasonKind.TimeSensitive }) add("Primary shows it too.")
    }.joinToString(" ")

private fun automatedSentence(detail: String): String =
    if ('@' in detail) {
        "It comes from $detail, an address that sends automated mail."
    } else {
        "It has automated mail headers: $detail."
    }

private fun hiddenLabel(detail: String): String = MailCategory.fromStorageValue(detail)?.label ?: detail

package com.otus.mail.feature.folders.presentation

import com.otus.mail.feature.folders.domain.SortBy
import com.otus.mail.feature.folders.domain.SortCondition

/** The condition as it completes "Mail that", such as "is from billing@acme.example". */
val SortCondition.phrase: String
    get() {
        val value = condition.value
        return when (kind) {
            SortBy.Sender -> "is from $value"
            SortBy.Domain -> "is from anyone at ${value.removePrefix("@")}"
            SortBy.Recipient -> "is sent to $value"
            SortBy.Subject -> "has “$value” in the subject"
        }
    }

/** The mail the condition catches, as it follows "New mail", such as "from billing@acme.example". */
val SortCondition.catches: String
    get() {
        val value = condition.value
        return when (kind) {
            SortBy.Sender -> "from $value"
            SortBy.Domain -> "from anyone at ${value.removePrefix("@")}"
            SortBy.Recipient -> "sent to $value"
            SortBy.Subject -> "with “$value” in the subject"
        }
    }

/** What the condition matches on, shown before [shown], such as "From". */
val SortCondition.keyword: String
    get() =
        when (kind) {
            SortBy.Sender, SortBy.Domain -> "From"
            SortBy.Recipient -> "To"
            SortBy.Subject -> "Subject"
        }

/** The value the condition matches, as it follows [keyword], such as “Invoice”. */
val SortCondition.shown: String
    get() = if (kind == SortBy.Subject) "“${condition.value}”" else condition.value

/** Who else the condition catches besides this message's sender. */
val SortCondition.reach: String
    get() =
        when (kind) {
            SortBy.Sender -> "Just this sender"
            SortBy.Domain -> "Anyone at this company"
            SortBy.Recipient -> "Everything sent to this address"
            SortBy.Subject -> "Any mail with this word in the subject"
        }

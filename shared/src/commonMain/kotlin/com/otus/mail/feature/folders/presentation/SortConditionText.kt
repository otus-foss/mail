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

/** Who else the condition catches besides this message's sender. */
val SortCondition.reach: String
    get() =
        when (kind) {
            SortBy.Sender -> "Just this sender"
            SortBy.Domain -> "Every sender at this company"
            SortBy.Recipient -> "Everything to this address"
            SortBy.Subject -> "From any sender"
        }

package com.otus.mail.feature.folders.presentation

import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_alternate_email
import com.otus.designsystem.resources.symbol_filter_list
import com.otus.designsystem.resources.symbol_inbox
import com.otus.designsystem.resources.symbol_person
import com.otus.mail.feature.folders.domain.SortBy
import com.otus.mail.feature.folders.domain.SortCondition
import org.jetbrains.compose.resources.DrawableResource

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

/** Who else the condition catches besides this message's sender. */
val SortCondition.reach: String
    get() =
        when (kind) {
            SortBy.Sender -> "Just this sender"
            SortBy.Domain -> "Every sender at this company"
            SortBy.Recipient -> "Everything to this address"
            SortBy.Subject -> "From any sender"
        }

/** What the condition matches by: a person, an address domain, a mailbox, or a filter on the subject. */
val SortCondition.icon: DrawableResource
    get() =
        when (kind) {
            SortBy.Sender -> Res.drawable.symbol_person
            SortBy.Domain -> Res.drawable.symbol_alternate_email
            SortBy.Recipient -> Res.drawable.symbol_inbox
            SortBy.Subject -> Res.drawable.symbol_filter_list
        }

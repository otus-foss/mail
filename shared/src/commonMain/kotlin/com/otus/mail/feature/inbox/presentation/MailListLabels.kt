package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.DateStyle
import com.otus.mail.feature.inbox.domain.ListOrder
import com.otus.mail.feature.inbox.domain.MailListLayout
import com.otus.mail.feature.inbox.domain.PreviewLines

val ListOrder.label: String
    get() =
        when (this) {
            ListOrder.SubjectFirst -> "Subject first"
            ListOrder.SenderFirst -> "Sender first"
        }

val PreviewLines.label: String
    get() =
        when (this) {
            PreviewLines.Off -> "Off"
            PreviewLines.One -> "1 line"
            PreviewLines.Two -> "2 lines"
        }

val DateStyle.label: String
    get() =
        when (this) {
            DateStyle.Relative -> "Relative"
            DateStyle.RelativeMonths -> "Relative with months"
            DateStyle.Full -> "Full date"
            DateStyle.FullMonths -> "Full with months"
        }

/** The Mail list row's summary on the appearance page. */
val MailListLayout.summary: String
    get() = if (showSender) order.label else "Preview instead of sender"

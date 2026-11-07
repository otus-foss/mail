package com.otus.mail.core.settings.presentation

import com.otus.mail.core.settings.domain.DateStyle
import com.otus.mail.core.settings.domain.ListOrder
import com.otus.mail.core.settings.domain.MailListLayout
import com.otus.mail.core.settings.domain.PreviewLines

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

/** The Mail list row's summary on the Appearance page. */
val MailListLayout.summary: String
    get() = if (showSender) order.label else "Preview instead of sender"

/** Why Order and Preview are disabled while rows leave out the sender; search shows it too. */
const val SENDER_UNAVAILABLE = "Turn on Show sender to change this"

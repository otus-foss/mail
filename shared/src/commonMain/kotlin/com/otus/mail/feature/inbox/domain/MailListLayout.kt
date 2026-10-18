package com.otus.mail.feature.inbox.domain

/** Which of a row's first two lines names the subject. */
enum class ListOrder(
    val storageValue: String,
) {
    SubjectFirst("subject"),
    SenderFirst("sender"),
}

/** How many lines of message text a row shows below its subject and sender. */
enum class PreviewLines(
    val storageValue: String,
    val count: Int,
) {
    Off("off", 0),
    One("one", 1),
    Two("two", 2),
}

/** How a row names the day a message arrived. */
enum class DateStyle(
    val storageValue: String,
) {
    /** The time today, then "Yesterday", the weekday within a week, and the date before that. */
    Relative("relative"),

    /** Always the date, with the time added for today. */
    Full("full"),
}

/**
 * How the mail list's rows read. Without [showSender], the second line shows the message text
 * instead, so [order] and [preview] do not apply. [folderIcons] also allows the folder color nudge.
 */
data class MailListLayout(
    val order: ListOrder = ListOrder.SubjectFirst,
    val preview: PreviewLines = PreviewLines.Off,
    val showSender: Boolean = true,
    val folderIcons: Boolean = true,
    val dateStyle: DateStyle = DateStyle.Relative,
) {
    companion object {
        /** One `name=value` line per setting, so a new setting cannot break an older saved value. */
        fun encode(layout: MailListLayout): String =
            listOf(
                ORDER to layout.order.storageValue,
                PREVIEW to layout.preview.storageValue,
                SHOW_SENDER to layout.showSender.toString(),
                FOLDER_ICONS to layout.folderIcons.toString(),
                DATE_STYLE to layout.dateStyle.storageValue,
            ).joinToString("\n") { (name, value) -> "$name=$value" }

        /** Missing or unknown values read as the defaults. */
        fun decode(saved: String): MailListLayout {
            val values = saved.lines().associate { it.substringBefore("=") to it.substringAfter("=", "") }
            val defaults = MailListLayout()
            return MailListLayout(
                order = ListOrder.entries.firstOrNull { it.storageValue == values[ORDER] } ?: defaults.order,
                preview = PreviewLines.entries.firstOrNull { it.storageValue == values[PREVIEW] } ?: defaults.preview,
                showSender = values[SHOW_SENDER]?.toBooleanStrictOrNull() ?: defaults.showSender,
                folderIcons = values[FOLDER_ICONS]?.toBooleanStrictOrNull() ?: defaults.folderIcons,
                dateStyle =
                    DateStyle.entries.firstOrNull { it.storageValue == values[DATE_STYLE] } ?: defaults.dateStyle,
            )
        }

        private const val ORDER = "order"
        private const val PREVIEW = "preview"
        private const val SHOW_SENDER = "showSender"
        private const val FOLDER_ICONS = "folderIcons"
        private const val DATE_STYLE = "dateStyle"
    }
}

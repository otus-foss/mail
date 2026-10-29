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

/** How a row names the day a message arrived, and whether its dates name the month, as "Oct 8". */
enum class DateStyle(
    val storageValue: String,
    val relative: Boolean,
    val monthNames: Boolean,
) {
    /** The time today, then "Yesterday", the weekday within a week, and the date before that. */
    Relative("relative", relative = true, monthNames = false),

    /** [Relative], with the month named in older dates. */
    RelativeMonths("relative-months", relative = true, monthNames = true),

    /** Always the date, with the time added for today. */
    Full("full", relative = false, monthNames = false),

    /** [Full], with the month named. */
    FullMonths("full-months", relative = false, monthNames = true),
}

/**
 * How the mail list's rows read. Without [showSender], the second line shows the message text
 * instead, so [order] and [preview] do not apply. [folderIcons] also allows the folder color nudge.
 * Without [showThisYear], this year's dates leave out the year.
 */
data class MailListLayout(
    val order: ListOrder = ListOrder.SubjectFirst,
    val preview: PreviewLines = PreviewLines.Off,
    val showSender: Boolean = true,
    val folderIcons: Boolean = true,
    val dateStyle: DateStyle = DateStyle.Relative,
    val showThisYear: Boolean = true,
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
                SHOW_THIS_YEAR to layout.showThisYear.toString(),
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
                showThisYear = values[SHOW_THIS_YEAR]?.toBooleanStrictOrNull() ?: defaults.showThisYear,
            )
        }

        private const val ORDER = "order"
        private const val PREVIEW = "preview"
        private const val SHOW_SENDER = "showSender"
        private const val FOLDER_ICONS = "folderIcons"
        private const val DATE_STYLE = "dateStyle"
        private const val SHOW_THIS_YEAR = "showThisYear"
    }
}

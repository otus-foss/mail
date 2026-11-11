package com.otusfoss.mail.core.settings.domain

/** A group of sidebar rows the user can move or hide. */
enum class SidebarSection(
    val storageValue: String,
) {
    /** The Inbox, then the mailboxes and folders the user pinned. It keeps its older stored value. */
    Mailboxes("mailboxes"),

    /** The user's own folders, shared by every account. */
    Folders("folders"),
}

/** How the sidebar lists the pinned mailboxes below the Inbox. */
enum class MailboxStyle(
    val storageValue: String,
) {
    /** Tiles in three columns. It keeps the stored value of the pills it replaced, so saved choices still read. */
    Grid("chips"),
    Rows("rows"),
}

/** How many of the grid's three columns a mailbox spans while the sidebar shows pinned mailboxes as a grid. */
enum class PinSize(
    val storageValue: String,
) {
    Small("small"),
    Wide("wide"),
    Full("full"),
}

/** Which count the sidebar shows beside mailboxes and folders. */
enum class SidebarCount(
    val storageValue: String,
) {
    Unread("unread"),
    Total("total"),
    None("none"),
}

/**
 * How the sidebar is arranged, the same for every account. [sections] lists every section in
 * order; [hidden] ones are left out. [pinned] lists what the Pinned section shows below the Inbox,
 * in order. In the grid, each pin spans its [sizes] entry, or by default the whole width for the
 * Inbox and one column for the rest. A pin keeps its size after it is unpinned, so pinning it
 * again restores it. Turning
 * [folderColors] off hides folder colors everywhere without forgetting them.
 */
data class SidebarLayout(
    val sections: List<SidebarSection> = SidebarSection.entries,
    val hidden: Set<SidebarSection> = emptySet(),
    val pinned: List<SidebarPin> = SidebarPin.defaults,
    val sizes: Map<SidebarPin, PinSize> = emptyMap(),
    val mailboxes: MailboxStyle = MailboxStyle.Grid,
    val counts: SidebarCount = SidebarCount.Unread,
    val folderColors: Boolean = true,
) {
    val shown: List<SidebarSection>
        get() = sections - hidden

    fun sizeOf(pin: SidebarPin): PinSize = sizes[pin] ?: if (pin == SidebarPin.Inbox) PinSize.Full else PinSize.Small

    /** Pins [shown] in this order; saved pins not among them, such as a folder that failed to load, follow. */
    fun withPinOrder(shown: List<SidebarPin>): SidebarLayout = copy(pinned = shown + (pinned - shown.toSet()))

    fun moved(
        from: Int,
        to: Int,
    ): SidebarLayout {
        val rest = sections.filterIndexed { index, _ -> index != from }
        return copy(sections = rest.take(to) + sections[from] + rest.drop(to))
    }

    /** Hiding the last shown section keeps it, so the sidebar always has a way to the mail. */
    fun withShown(
        section: SidebarSection,
        shown: Boolean,
    ): SidebarLayout =
        when {
            shown -> copy(hidden = hidden - section)
            this.shown == listOf(section) -> this
            else -> copy(hidden = hidden + section)
        }

    companion object {
        /** One `name=values` line per setting, so a new setting cannot break an older saved value. */
        fun encode(layout: SidebarLayout): String =
            listOf(
                SECTIONS to layout.sections.joinToString(",") { it.storageValue },
                HIDDEN to layout.hidden.joinToString(",") { it.storageValue },
                PINNED to SidebarPin.encode(layout.pinned),
                OFFERED to SidebarPin.encode(SidebarPin.introduced),
                SIZES to
                    layout.sizes
                        .entries
                        .joinToString(",") { (pin, size) -> "${size.storageValue}:${pin.storageValue}" },
                MAILBOXES to layout.mailboxes.storageValue,
                COUNTS to layout.counts.storageValue,
                FOLDER_COLORS to layout.folderColors.toString(),
            ).joinToString("\n") { (name, value) -> "$name=$value" }

        /**
         * Unknown values read as the defaults, and sections missing from the saved order go last.
         * A layout saved before pins existed pins the mailboxes the sidebar always showed, and one
         * saved before a pin was introduced gets that pin.
         */
        fun decode(saved: String): SidebarLayout {
            val values = saved.lines().associate { it.substringBefore("=") to it.substringAfter("=", "") }

            fun sections(name: String) =
                values[name]
                    .orEmpty()
                    .split(",")
                    .mapNotNull { value -> SidebarSection.entries.firstOrNull { it.storageValue == value } }
                    .distinct()
            val defaults = SidebarLayout()
            return SidebarLayout(
                sections = sections(SECTIONS).let { it + (SidebarSection.entries - it.toSet()) },
                hidden = sections(HIDDEN).toSet(),
                pinned =
                    values[PINNED]?.let(SidebarPin::decode)?.let { pins ->
                        val offered = values[OFFERED]?.let(SidebarPin::decode).orEmpty()
                        val added = SidebarPin.introduced - offered.toSet() - pins.toSet()
                        // The Inbox goes back to the top, where it always was before it became a pin.
                        added.filter { it == SidebarPin.Inbox } + pins + (added - SidebarPin.Inbox)
                    } ?: defaults.pinned,
                sizes = sizes(values[SIZES].orEmpty()),
                mailboxes =
                    MailboxStyle.entries.firstOrNull { it.storageValue == values[MAILBOXES] } ?: defaults.mailboxes,
                counts = SidebarCount.entries.firstOrNull { it.storageValue == values[COUNTS] } ?: defaults.counts,
                folderColors = values[FOLDER_COLORS]?.toBooleanStrictOrNull() ?: defaults.folderColors,
            )
        }

        /** Each `size:pin` entry, leaving out ones this version cannot read. */
        private fun sizes(saved: String): Map<SidebarPin, PinSize> =
            saved
                .split(",")
                .mapNotNull { entry ->
                    val size = pinSize(entry.substringBefore(":")) ?: return@mapNotNull null
                    SidebarPin.fromStorageValue(entry.substringAfter(":", ""))?.let { it to size }
                }.toMap()

        private fun pinSize(saved: String?): PinSize? = PinSize.entries.firstOrNull { it.storageValue == saved }

        private const val SECTIONS = "sections"
        private const val HIDDEN = "hidden"
        private const val PINNED = "pinned"
        private const val OFFERED = "offered"
        private const val SIZES = "sizes"
        private const val MAILBOXES = "mailboxes"
        private const val COUNTS = "counts"
        private const val FOLDER_COLORS = "folderColors"
    }
}

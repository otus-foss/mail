package com.otus.mail.feature.inbox.presentation.components

import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.designsystem.resources.symbol_filter_list
import com.otus.designsystem.resources.symbol_format_list_bulleted
import com.otus.designsystem.resources.symbol_forum
import com.otus.designsystem.resources.symbol_grid_on
import com.otus.designsystem.resources.symbol_home
import com.otus.designsystem.resources.symbol_inbox
import com.otus.designsystem.resources.symbol_mark_email_read
import com.otus.designsystem.resources.symbol_mark_email_unread
import com.otus.designsystem.resources.symbol_menu
import com.otus.designsystem.resources.symbol_schedule_send
import com.otus.designsystem.resources.symbol_signature
import com.otus.designsystem.resources.symbol_tune
import com.otus.designsystem.resources.symbol_view_agenda
import com.otus.mail.core.mailbox.pathText
import com.otus.mail.feature.inbox.domain.ActionBarMode
import com.otus.mail.feature.inbox.domain.DateStyle
import com.otus.mail.feature.inbox.domain.FilterLayout
import com.otus.mail.feature.inbox.domain.FilterSection
import com.otus.mail.feature.inbox.domain.ListOrder
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.MailListLayout
import com.otus.mail.feature.inbox.domain.MailboxStyle
import com.otus.mail.feature.inbox.domain.PreviewLines
import com.otus.mail.feature.inbox.domain.SidebarCount
import com.otus.mail.feature.inbox.domain.SidebarLayout
import com.otus.mail.feature.inbox.domain.SidebarSection
import com.otus.mail.feature.inbox.domain.UNDO_SEND_CHOICES
import com.otus.mail.feature.inbox.presentation.FolderPillOption
import com.otus.mail.feature.inbox.presentation.InboxPills
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.PINNED_UNAVAILABLE
import com.otus.mail.feature.inbox.presentation.SENDER_UNAVAILABLE
import com.otus.mail.feature.inbox.presentation.SidebarPinOption
import com.otus.mail.feature.inbox.presentation.icon
import com.otus.mail.feature.inbox.presentation.inboxSummary
import com.otus.mail.feature.inbox.presentation.label
import com.otus.mail.feature.inbox.presentation.pinnedSummary
import com.otus.mail.feature.inbox.presentation.sectionSummary
import com.otus.mail.feature.inbox.presentation.summary
import com.otus.mail.feature.inbox.presentation.title
import com.otus.settings.presentation.SettingsIndexScope

// What settings search finds on Mail's pages. Each function mirrors the screen it names, and a
// setting that its page shows disabled says why, so search finds it with the reason.

/** The Mail row of the Appearance page: the Mail list page. */
fun SettingsIndexScope.mailLookIndex(
    behavior: MailBehavior,
    openMailList: (String?) -> Unit,
) {
    page(
        "Mail list",
        openMailList,
        Res.drawable.symbol_format_list_bulleted,
        value = behavior.list.summary,
    ) {
        keywords("Rows", "Density")
        mailListIndex(behavior.list)
    }
}

/** Mirrors MailListSettingsScreen. */
private fun SettingsIndexScope.mailListIndex(list: MailListLayout) {
    section("Rows") {
        toggle("Show sender", list.showSender)
        unavailable(SENDER_UNAVAILABLE.takeUnless { list.showSender }) {
            setting("Order", list.order.label, ListOrder.entries.map { it.label })
            val lines = PreviewLines.entries.map { it.label }
            setting("Preview", list.preview.label, lines, keywords = listOf("Snippet", "Lines"))
        }
        toggle("Folder icons", list.folderIcons)
    }
    section("Dates") {
        setting("Style", list.dateStyle.label, DateStyle.entries.map { it.label }, keywords = listOf("Relative"))
        toggle("Show this year", list.showThisYear)
    }
}

/** The pages Mail behavior's rows open, each on the setting search found there. */
class BehaviorIndexPages(
    val sidebar: (String?) -> Unit,
    val pinned: (String?) -> Unit,
    val filterPanel: (String?) -> Unit,
    val actionBar: (String?) -> Unit,
    val signatures: (String?) -> Unit,
)

/**
 * Mirrors the Mail behavior page; [rtl] names the swipes as the screen does, and [startPages] are
 * the mailboxes Start page offers.
 */
fun SettingsIndexScope.mailBehaviorIndex(
    state: MailBehaviorState,
    folderPills: List<FolderPillOption>,
    startPages: List<SidebarPinOption>,
    rtl: Boolean,
    pages: BehaviorIndexPages,
) {
    val behavior = state.behavior
    section("Inbox") {
        setting(
            "Inbox shows",
            inboxSummary(behavior.inbox),
            icon = Res.drawable.symbol_inbox,
            keywords = listOf("Archive", "Spam", "Trash", "All mail"),
        )
        setting(
            "Pills",
            shownCount(InboxPills(behavior.pills, folderPills).shown.size),
            icon = Res.drawable.symbol_filter_list,
            keywords = listOf("Chips", "Tabs"),
        )
        toggle("Read later", behavior.readLater, Res.drawable.symbol_bookmark, keywords = listOf("Save for later"))
    }
    panelsIndex(behavior, startPages, pages)
    actionsIndex(behavior, rtl, pages.actionBar)
    section("Reading") {
        toggle("Mark as read when opened", behavior.markReadOnOpen, Res.drawable.symbol_mark_email_read)
        toggle("Show quoted text", behavior.showQuotes, Res.drawable.symbol_forum)
        setting("Quick view", shownCount(behavior.readQuickView.size), icon = Res.drawable.symbol_grid_on)
    }
    section("Writing") {
        setting(
            "Undo send",
            behavior.undoSendSeconds.undoLabel,
            UNDO_SEND_CHOICES.map { it.undoLabel },
            Res.drawable.symbol_schedule_send,
            keywords = listOf("Delay", "Cancel sending"),
        )
        page(
            "Signatures",
            pages.signatures,
            Res.drawable.symbol_signature,
            summary = "One for each address you send from",
        ) { toggle("Add to replies", behavior.signatureInReplies) }
    }
}

/** The Panels section of the Mail behavior page: Start page among [startPages], and the panels' pages. */
private fun SettingsIndexScope.panelsIndex(
    behavior: MailBehavior,
    startPages: List<SidebarPinOption>,
    pages: BehaviorIndexPages,
) {
    section("Panels") {
        setting(
            "Start page",
            startPageLabel(behavior.startPage, startPages),
            startPages.map { it.path.pathText() },
            Res.drawable.symbol_home,
            keywords = listOf("Open on", "Launch", "Home"),
        )
        page(
            "Sidebar",
            pages.sidebar,
            Res.drawable.symbol_menu,
            value = behavior.sidebar.sectionSummary,
        ) {
            keywords("Navigation", "Drawer")
            sidebarIndex(behavior.sidebar, pages.pinned)
        }
        page(
            "Filter panel",
            pages.filterPanel,
            Res.drawable.symbol_tune,
            value = filterPanelSummary(behavior.filters.sections.size),
        ) {
            keywords("Control panel", "Filters")
            filterPanelIndex(behavior.filters, behavior.quickView.size)
        }
    }
}

/** The Actions section of the Mail behavior page, with the Action bar page that [openActionBar] opens. */
private fun SettingsIndexScope.actionsIndex(
    behavior: MailBehavior,
    rtl: Boolean,
    openActionBar: (String?) -> Unit,
) {
    section("Actions") {
        val swipes = (listOf(null) + behavior.swipeChoices).map { it.swipeLabel }
        SwipeSide.entries.forEach {
            setting(it.title(rtl), it.of(behavior).swipeLabel, swipes, it.icon(rtl), keywords = listOf("Gestures"))
        }
        page("Action bar", openActionBar, Res.drawable.symbol_view_agenda) {
            keywords("Toolbar", "Buttons")
            setting("Layout", behavior.actionBarMode.label, ActionBarMode.entries.map { it.label })
            toggle("Minimize", behavior.minimizedBar)
        }
    }
}

/** Mirrors SidebarSettingsScreen and the Pinned page that [openPinned] opens. */
private fun SettingsIndexScope.sidebarIndex(
    layout: SidebarLayout,
    openPinned: (String?) -> Unit,
) {
    section("Sections") {
        layout.sections.forEach { toggle(it.title, it !in layout.hidden, it.icon) }
    }
    section("Pinned") {
        unavailable(PINNED_UNAVAILABLE.takeIf { SidebarSection.Mailboxes in layout.hidden }) {
            page("Mailboxes and folders", openPinned, value = pinnedSummary(layout.mailboxes, layout.pinned.size)) {
                keywords("Tiles", "Shortcuts")
                setting("Show as", layout.mailboxes.label, MailboxStyle.entries.map { it.label })
            }
        }
    }
    section("Layout") {
        setting("Counts", layout.counts.label, SidebarCount.entries.map { it.label }, keywords = listOf("Badges"))
        toggle("Folder colors", layout.folderColors)
    }
}

/** Mirrors FilterPanelSettingsScreen; [quickViewCount] is how many modules its quick view shows. */
private fun SettingsIndexScope.filterPanelIndex(
    filters: FilterLayout,
    quickViewCount: Int,
) {
    section("Panel") {
        setting("Sections", shownCount(filters.sections.size), icon = Res.drawable.symbol_tune)
        unavailable(QUICK_FILTERS_UNAVAILABLE.takeUnless { FilterSection.Quick in filters.sections }) {
            setting("Quick filters", shownCount(filters.quick.size), icon = Res.drawable.symbol_mark_email_unread)
        }
    }
    section("Pull down") {
        setting("Quick view", shownCount(quickViewCount), icon = Res.drawable.symbol_grid_on)
    }
}

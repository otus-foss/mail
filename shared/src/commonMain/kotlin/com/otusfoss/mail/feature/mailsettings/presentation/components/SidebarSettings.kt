package com.otusfoss.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_view_agenda
import com.otusfoss.core.designsystem.shared.feedback.components.InfoText
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceOptions
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceRow
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceSheet
import com.otusfoss.core.settings.presentation.components.ChoiceRowSpec
import com.otusfoss.core.settings.presentation.components.PageRowSpec
import com.otusfoss.core.settings.presentation.components.SettingRowSpec
import com.otusfoss.core.settings.presentation.components.SettingsSectionSpec
import com.otusfoss.core.settings.presentation.components.ToggleRowSpec
import com.otusfoss.core.settings.presentation.components.waitingOn
import com.otusfoss.mail.core.settings.domain.MailboxStyle
import com.otusfoss.mail.core.settings.domain.SidebarCount
import com.otusfoss.mail.core.settings.domain.SidebarLayout
import com.otusfoss.mail.core.settings.domain.SidebarSection
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import com.otusfoss.mail.core.settings.presentation.PINNED_UNAVAILABLE
import com.otusfoss.mail.core.settings.presentation.SidebarPinOption
import com.otusfoss.mail.core.settings.presentation.icon
import com.otusfoss.mail.core.settings.presentation.label
import com.otusfoss.mail.core.settings.presentation.pinnedSummary
import com.otusfoss.mail.core.settings.presentation.title

/** Everything the Sidebar page holds: its sections, then [sidebarLayoutSettings]. */
internal fun sidebarSettings(
    state: MailBehaviorState,
    pins: List<SidebarPinOption>,
    openPinned: (setting: String?) -> Unit,
): List<SettingsSectionSpec> =
    listOf(SettingsSectionSpec("Sections", sidebarSectionToggles(state))) +
        sidebarLayoutSettings(state, pins, openPinned)

/** A switch for each of the sidebar's sections in its order, which shows or hides the section. */
internal fun sidebarSectionToggles(state: MailBehaviorState): List<ToggleRowSpec> {
    val layout = state.behavior.sidebar.layout
    return layout.sections.map { section ->
        ToggleRowSpec(
            section.title,
            section !in layout.hidden,
            { shown -> state.changeSidebar { it.withShown(section, shown) } },
            section.icon,
        )
    }
}

/**
 * The Sidebar page below its sections. The Pinned row opens [openPinned], the page that arranges
 * the mailboxes from [pins]; it waits while the Pinned section is off, since nothing would show
 * them. The layout rows choose which counts appear, and a switch hides folder colors without
 * forgetting them.
 */
internal fun sidebarLayoutSettings(
    state: MailBehaviorState,
    pins: List<SidebarPinOption>,
    openPinned: (setting: String?) -> Unit,
): List<SettingsSectionSpec> =
    listOf(
        SettingsSectionSpec("Pinned", listOf(pinnedRow(state, pins, openPinned))),
        SettingsSectionSpec("Layout", layoutRows(state)),
    )

private fun pinnedRow(
    state: MailBehaviorState,
    pins: List<SidebarPinOption>,
    openPinned: (setting: String?) -> Unit,
): SettingRowSpec {
    val layout = state.behavior.sidebar.layout
    return PageRowSpec(
        "Mailboxes and folders",
        openPinned,
        value = pinnedSummary(layout.mailboxes, layout.pinned.count { pin -> pins.any { it.pin == pin } }),
        keywords = listOf("Tiles", "Shortcuts"),
    ) { pinnedSettings(state) }.waitingOn(PINNED_UNAVAILABLE.takeIf { SidebarSection.Mailboxes in layout.hidden })
}

private fun layoutRows(state: MailBehaviorState): List<SettingRowSpec> {
    val layout = state.behavior.sidebar.layout
    return listOf(
        ChoiceRowSpec(
            "Counts",
            layout.counts.label,
            { CountsSheet(state, it) },
            SidebarCount.entries.map { it.label },
            keywords = listOf("Badges"),
        ),
        ToggleRowSpec(
            "Folder colors",
            layout.folderColors,
            { shown -> state.changeSidebar { it.copy(folderColors = shown) } },
            info = InfoText("Folder colors", "Only on this device."),
        ),
    )
}

/** The Pinned page's rows above its editor: whether pinned mailboxes show as a grid or as rows. */
internal fun pinnedSettings(state: MailBehaviorState): List<SettingsSectionSpec> =
    listOf(
        SettingsSectionSpec(
            title = null,
            rows =
                listOf(
                    ChoiceRowSpec(
                        "Show as",
                        state.behavior.sidebar.layout.mailboxes.label,
                        { PinnedStyleSheet(state, it) },
                        MailboxStyle.entries.map { it.label },
                        Res.drawable.symbol_view_agenda,
                    ),
                ),
        ),
    )

/** Changes the sidebar's layout with [update]; each change is saved at once. */
internal fun MailBehaviorState.changeSidebar(update: (SidebarLayout) -> SidebarLayout) {
    updateSidebar { it.copy(layout = update(it.layout)) }
}

@Composable
private fun CountsSheet(
    state: MailBehaviorState,
    onDismiss: () -> Unit,
) {
    ChoiceSheet(
        title = "Counts",
        choices = ChoiceOptions(SidebarCount.entries, state.behavior.sidebar.layout.counts, SidebarCount.Unread),
        row = { ChoiceRow(it.label) },
        onSelect = { count -> state.changeSidebar { it.copy(counts = count) } },
        onDismiss = onDismiss,
    )
}

@Composable
private fun PinnedStyleSheet(
    state: MailBehaviorState,
    onDismiss: () -> Unit,
) {
    ChoiceSheet(
        title = "Show pinned as",
        choices = ChoiceOptions(MailboxStyle.entries, state.behavior.sidebar.layout.mailboxes, MailboxStyle.Grid),
        row = { ChoiceRow(it.label, it.detail) },
        onSelect = { style -> state.changeSidebar { it.copy(mailboxes = style) } },
        onDismiss = onDismiss,
    )
}

private val MailboxStyle.detail: String
    get() =
        when (this) {
            MailboxStyle.Grid -> "Tiles you can resize"
            MailboxStyle.Rows -> "One row each, in order"
        }

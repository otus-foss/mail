package com.otusfoss.mail.feature.mailsettings.presentation.components

import com.otusfoss.core.dateformat.DateFormatPreference
import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.core.settings.presentation.SettingsItem
import com.otusfoss.core.settings.presentation.components.row
import com.otusfoss.core.settings.presentation.components.sections
import com.otusfoss.core.settings.presentation.search
import com.otusfoss.core.settings.presentation.settingsIndex
import com.otusfoss.mail.core.messages.domain.DevicePrivacy
import com.otusfoss.mail.core.settings.domain.FilterLayout
import com.otusfoss.mail.core.settings.domain.FilterSection
import com.otusfoss.mail.core.settings.domain.ListBehavior
import com.otusfoss.mail.core.settings.domain.MailBehavior
import com.otusfoss.mail.core.settings.domain.MailListLayout
import com.otusfoss.mail.core.settings.domain.SidebarBehavior
import com.otusfoss.mail.core.settings.domain.SidebarLayout
import com.otusfoss.mail.core.settings.domain.SidebarSection
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MailSettingsIndexTest {
    private val opened = mutableListOf<String>()

    private fun index(behavior: MailBehavior = MailBehavior()): List<SettingsItem> {
        val state = MailBehaviorState(initial = behavior)
        val pages =
            BehaviorPages(
                sidebar = { opened += "Sidebar: $it" },
                pinned = { opened += "Pinned: $it" },
                filterPanel = { opened += "Filter panel: $it" },
                actionBar = { opened += "Action bar: $it" },
                signatures = { opened += "Signatures: $it" },
            )
        return settingsIndex {
            page("Appearance", { opened += "Appearance: $it" }) {
                val dates = DateFormatter(DateFormatPreference.DayMonthYearDots)
                row(mailListRow(state, dates) { opened += "Mail list: $it" })
            }
            page("Mail behavior", { opened += "Mail behavior: $it" }) {
                sections(mailBehaviorSettings(state, emptyList(), emptyList(), rtl = false, pages))
            }
            page("Privacy & security", { opened += "Privacy: $it" }) {
                val device = DevicePrivacy(screen = true, keyboard = true)
                sections(privacySettings(state, device) { opened += "Allowed: $it" })
            }
        }
    }

    private fun List<SettingsItem>.path(title: String): List<String>? =
        search(title).first { it.title == title }.page?.path

    @Test
    fun theSidebarAndSignaturesLiveUnderMailBehavior() {
        val found = index()
        assertEquals(listOf("Mail behavior"), found.path("Sidebar"))
        assertEquals(listOf("Mail behavior"), found.path("Start page"))
        assertEquals(listOf("Mail behavior", "Sidebar", "Mailboxes and folders"), found.path("Show as"))
        assertEquals(listOf("Mail behavior", "Signatures"), found.path("Add to replies"))
        assertEquals(listOf("Appearance", "Mail list"), found.path("Show sender"))
    }

    @Test
    fun bothQuickViewsAreFoundOnTheirOwnPages() {
        val paths = index().search("quick view").map { it.page?.path }
        assertEquals(listOf(listOf("Mail behavior", "Filter panel"), listOf("Mail behavior")), paths)
    }

    @Test
    fun aSettingWaitingOnAnotherSaysWhy() {
        val behavior =
            MailBehavior(
                list =
                    ListBehavior(
                        filters = FilterLayout(sections = FilterSection.entries - FilterSection.Quick),
                        layout = MailListLayout(showSender = false),
                    ),
                sidebar = SidebarBehavior(SidebarLayout(hidden = setOf(SidebarSection.Mailboxes))),
            )
        val found = index(behavior)
        assertEquals(QUICK_FILTERS_UNAVAILABLE, found.unavailable("Quick filters"))
        assertEquals("Turn on Show sender to change this", found.unavailable("Order"))
        assertEquals("Turn on the Pinned section to arrange it", found.unavailable("Mailboxes and folders"))
        assertEquals("Turn on Website icons to choose", found.unavailable("Icons from"))
        assertNull(index().unavailable("Quick filters"))
        assertNull(index().unavailable("Order"))
    }

    @Test
    fun keywordsFindSettingsByOtherNames() {
        val found = index()
        assertEquals("Screen security", found.search("screenshots").single().title)
        assertEquals("Sidebar", found.search("drawer").single().title)
        assertEquals("Photo details", found.search("location").single().title)
    }

    @Test
    fun aResultOpensItsOwnPageOnTheSetting() {
        // A setting whose name matches comes first, before ones that only hold each word somewhere.
        index().search("show as").first().onOpen()
        index().search("undo send").first().onOpen()
        assertEquals(listOf("Pinned: Show as", "Mail behavior: Undo send"), opened)
    }

    private fun List<SettingsItem>.unavailable(title: String): String? =
        search(title).first { it.title == title }.unavailable
}

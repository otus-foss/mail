package com.otus.mail.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Moves between pages; [openSidebar] opens navigation from a top-level page. [find] opens a page
 * from settings search, and back from that page returns to the search's results.
 */
internal class MailNavigation(
    private val show: (MailPage) -> Unit,
    val openSidebar: () -> Unit,
) {
    /** The page settings search opened last, and the setting it found there, until the user moves on. */
    var found: FoundSetting? by mutableStateOf(null)
        private set

    fun go(page: MailPage) {
        found = null
        show(page)
    }

    /** Opens [page] from settings search with [setting] found on it, or at its top for null. */
    fun find(
        page: MailPage,
        setting: String?,
    ) {
        show(page)
        found = FoundSetting(page, setting)
    }

    /** Leaves [page] for its parent, or for the settings search that opened it. */
    fun back(page: MailPage) {
        go(if (found?.page == page) MailPage.Settings else page.parent ?: page)
    }

    /** Opens [page] from settings search on the setting it is given. */
    fun finder(page: MailPage): (String?) -> Unit = { find(page, it) }
}

/** A [page] that settings search opened, and the [setting] it found there. */
internal data class FoundSetting(
    val page: MailPage,
    val setting: String?,
)

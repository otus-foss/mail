package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Moves between pages; [openSidebar] opens navigation from a top-level page. [find] opens a page
 * from settings search, and back from that page returns to the search's results.
 */
internal class MailNavigation(
    private val show: (MailPage) -> Unit,
    found: MutableState<FoundSetting?>,
    val openSidebar: () -> Unit,
) {
    /** The page settings search opened last, and the setting it found there, until the user moves on. */
    var found: FoundSetting? by found
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

/** [MailNavigation] whose found setting survives recreation, as the page it belongs to does. */
@Composable
internal fun rememberMailNavigation(
    show: (MailPage) -> Unit,
    openSidebar: () -> Unit,
): MailNavigation {
    val found = rememberSaveable(stateSaver = FoundSetting.Saver) { mutableStateOf<FoundSetting?>(null) }
    return remember(found) { MailNavigation(show, found, openSidebar) }
}

/** A [page] that settings search opened, and the [setting] it found there. */
internal data class FoundSetting(
    val page: MailPage,
    val setting: String?,
) {
    companion object {
        /** Saves the setting first, empty for none, then the page as [MailPage.Saver] does. */
        val Saver: Saver<FoundSetting?, List<String>> =
            Saver(
                save = { found ->
                    found?.let { listOf(it.setting.orEmpty()) + with(MailPage.Saver) { save(it.page) }.orEmpty() }
                },
                restore = { saved ->
                    MailPage.Saver.restore(saved.drop(1))?.let { FoundSetting(it, saved.first().ifEmpty { null }) }
                },
            )
    }
}

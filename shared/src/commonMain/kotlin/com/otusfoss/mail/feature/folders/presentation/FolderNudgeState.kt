package com.otusfoss.mail.feature.folders.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otusfoss.core.designsystem.theme.colors.FolderColor
import com.otusfoss.mail.core.mailbox.AccountFolders
import com.otusfoss.mail.core.mailbox.FolderKey
import com.otusfoss.mail.core.mailbox.MailFolder
import com.otusfoss.mail.core.mailbox.key
import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.feature.folders.domain.FolderNudges

/** The colors the nudge offers, in order; it leaves out Red. */
val NUDGE_COLORS: List<FolderColor> =
    listOf(
        FolderColor.Blue,
        FolderColor.Teal,
        FolderColor.Green,
        FolderColor.Yellow,
        FolderColor.Orange,
        FolderColor.Pink,
        FolderColor.Purple,
    )

/** A folder the nudge offers a color for, and how many of the list's conversations it holds. */
data class NudgeCandidate(
    val folder: MailFolder,
    val conversations: Int,
)

/**
 * Which folder the color nudge offers a color for. Each folder gets the nudge at most once, and
 * one nudge per launch at most: the first folder shown stays until the user colors or closes it,
 * and then no other follows until the next launch. [enabled] is false while either the Folder
 * colors or the Folder icons switch is off. Shown and closed folders are written to [store].
 */
class FolderNudgeState(
    private val store: DeviceStore<String, List<String>>? = null,
    private val colors: FolderColorsState,
    private val accounts: () -> List<AccountFolders>,
    private val enabled: () -> Boolean = { true },
) {
    var nudges by mutableStateOf(saved() ?: FolderNudges())
        private set

    private var showing by mutableStateOf<FolderKey?>(null)
    private var closed by mutableStateOf(false)

    /**
     * The folder to nudge, given each folder's [visible] conversation count: among the user's own
     * folders without a color and without an earlier nudge, the one with the most; null for none.
     */
    fun pick(visible: Map<FolderKey, Int>): NudgeCandidate? {
        if (!enabled() || closed) return null
        val open = accounts().flatMap { it.folders }.filter(::uncolored)
        val shown = showing
        return if (shown != null) {
            open.firstOrNull { it.key == shown }?.let { NudgeCandidate(it, visible[shown] ?: 0) }
        } else {
            open
                .filter { it.key !in nudges }
                .map { NudgeCandidate(it, visible[it.key] ?: 0) }
                .filter { it.conversations > 0 }
                .maxByOrNull { it.conversations }
        }
    }

    /** Records that the nudge appeared for [folder], so it never offers that folder again. */
    fun markShown(folder: FolderKey) {
        showing = folder
        record(folder)
    }

    /** Closes the nudge for [folder] and for the rest of this launch. */
    fun dismiss(folder: FolderKey) {
        closed = true
        record(folder)
    }

    private fun uncolored(folder: MailFolder): Boolean = colors.colorable(folder) && colors.colors.of(folder) == null

    /** Starts from the stored nudges, so it never writes back an account removed since the last read. */
    private fun record(folder: FolderKey) {
        val next = (saved() ?: nudges).with(folder)
        store?.write(next.byLogin())
        nudges = next
    }

    private fun saved(): FolderNudges? = store?.read()?.let(FolderNudges::of)
}

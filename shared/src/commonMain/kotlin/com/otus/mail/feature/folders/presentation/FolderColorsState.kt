package com.otus.mail.feature.folders.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.designsystem.theme.colors.FolderColor
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.FolderColorStore
import com.otus.mail.feature.folders.domain.FolderColors
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.SharedFolder
import com.otus.mail.feature.folders.domain.colorable
import com.otus.mail.feature.folders.domain.readColors
import com.otus.mail.feature.folders.domain.writeColors

/**
 * The saved folder colors and the color each folder shows. [accounts] supplies the loaded folders,
 * which say which folder is Read later; [shown] is the Folder colors switch. Changes are written to
 * [store] before they are shown.
 */
class FolderColorsState(
    private val store: FolderColorStore? = null,
    private val accounts: () -> List<AccountFolders> = { emptyList() },
    private val shown: () -> Boolean = { true },
) {
    var colors by mutableStateOf(store?.readColors() ?: FolderColors())
        private set

    /** The color name [folder] shows; null for the default icon color. */
    fun colorOf(folder: MailFolder): String? = if (tinted(folder)) colors.of(folder) else null

    /** The color name the shared folder's row shows; null for the default icon color. */
    fun groupColorOf(shared: SharedFolder): String? =
        shared.folders
            .firstOrNull()
            ?.takeIf(::tinted)
            ?.let { colors.of(shared) }

    /** Whether the user may choose a color for [folder]; true even while the switch is off. */
    fun colorable(folder: MailFolder): Boolean = accountOf(folder)?.colorable(folder) ?: folder.isCustom

    /**
     * Gives [folders] the color [name], or the default for null. A shared folder passes all its
     * accounts' folders, so they share one color.
     */
    fun setColor(
        folders: List<MailFolder>,
        name: String?,
    ) = update { it.with(folders.filter(::colorable), name) }

    /** Forgets [folder]'s color once the server has deleted it. */
    fun forget(folder: MailFolder) = update { it.without(folder) }

    /** The color a new folder starts with: the next one no folder has, or null while colors are hidden. */
    fun newFolderColor(): String? = if (shown()) nextFolderColor(colors.names.values).name else null

    /** Reads the store again, as after an account is removed. */
    fun reload() {
        colors = store?.readColors() ?: colors
    }

    private fun tinted(folder: MailFolder): Boolean = shown() && colorable(folder)

    private fun accountOf(folder: MailFolder): AccountFolders? =
        accounts().firstOrNull { it.accountId == folder.accountId }

    /** Starts from the stored colors, so it never writes back an account removed since the last read. */
    private fun update(change: (FolderColors) -> FolderColors) {
        val next = change(store?.readColors() ?: colors)
        store?.writeColors(next)
        colors = next
    }
}

/**
 * The first color in [FolderColor] order that is used least often among the [used] color names,
 * leaving out [FolderColor.Default], so new folders get every color once before any color repeats.
 */
fun nextFolderColor(used: Collection<String>): FolderColor {
    val counts = used.groupingBy { it }.eachCount()
    return FolderColor.entries.filter { it != FolderColor.Default }.minBy { counts[it.name] ?: 0 }
}

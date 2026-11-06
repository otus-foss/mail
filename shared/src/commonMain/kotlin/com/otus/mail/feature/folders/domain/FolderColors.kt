package com.otus.mail.feature.folders.domain

import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.FolderKey
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.SharedFolder
import com.otus.mail.core.mailbox.key
import com.otus.mail.core.storage.StoreSpec
import com.otus.mail.core.storage.TextCodec

/**
 * Whether the user may color [folder]: only their own folders. Folders with a role and the Read
 * later folder keep the plain icon. Shared mailboxes get no color, because their ids can repeat
 * the login's own, which key the stored colors.
 */
fun AccountFolders.colorable(folder: MailFolder): Boolean =
    folder.isCustom && folder.id != readLaterId && folder.sharedIn == null

/**
 * The colors the user gave their folders, as color names that the design system resolves. A folder
 * without one has the default icon color. JMAP mailboxes have no color property (RFC 8621,
 * section 2), so colors stay on this device.
 */
data class FolderColors(
    val names: Map<FolderKey, String> = emptyMap(),
) {
    fun of(folder: MailFolder): String? = names[folder.key]

    /**
     * A shared folder shows its first account's color. Other accounts keep their own, so unlinking
     * the folder later gives each account back what it had.
     */
    fun of(shared: SharedFolder): String? = shared.folders.firstOrNull()?.let(::of)

    /** Gives every folder in [folders] the color [name]; null returns them to the default. */
    fun with(
        folders: List<MailFolder>,
        name: String?,
    ): FolderColors {
        val keys = folders.map { it.key }
        return FolderColors(if (name == null) names - keys.toSet() else names + keys.associateWith { name })
    }

    fun without(folder: MailFolder): FolderColors = FolderColors(names - folder.key)
}

/**
 * App-private storage for [FolderColors]: color names by [FolderKey.storageValue]. Entries it cannot
 * parse, and blank names, read as the default.
 */
object FolderColorStore : StoreSpec<FolderKey, String>(
    "folder_colors",
    TextCodec({ it.storageValue }, FolderKey::fromStorageValue),
    TextCodec.NonBlank,
)

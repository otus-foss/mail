package com.otus.mail.feature.folders.domain

import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.MailFolder
import com.otus.storage.StringMapStore

/**
 * One account's folder: the login [accountId] and the server's [mailboxId]. Colors are keyed by it,
 * never by name or path, so a rename or move keeps the color and a folder made again with a new id
 * starts without one.
 */
data class FolderKey(
    val accountId: String,
    val mailboxId: String,
) {
    /** The mailbox id first: JMAP ids hold only letters, digits, `-` and `_` (RFC 8620, section 1.2). */
    val storageValue: String
        get() = "$mailboxId$SEPARATOR$accountId"

    companion object {
        /** Null for a value [storageValue] cannot have made. */
        fun fromStorageValue(value: String): FolderKey? {
            val mailboxId = value.substringBefore(SEPARATOR, missingDelimiterValue = "")
            val accountId = value.substringAfter(SEPARATOR, missingDelimiterValue = "")
            return if (mailboxId.isEmpty() || accountId.isEmpty()) null else FolderKey(accountId, mailboxId)
        }

        private const val SEPARATOR = ':'
    }
}

val MailFolder.key: FolderKey
    get() = FolderKey(accountId, id)

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

    fun withoutAccount(accountId: String): FolderColors = FolderColors(names.filterKeys { it.accountId != accountId })
}

/**
 * App-private storage for [FolderColors]: color names by [FolderKey.storageValue]. Implementations
 * skip entries that [read] cannot parse, so unknown or blank values read as the default.
 */
interface FolderColorStore : StringMapStore

fun FolderColorStore.readColors(): FolderColors =
    FolderColors(
        read()
            .filterValues { it.isNotBlank() }
            .mapNotNull { (key, name) -> FolderKey.fromStorageValue(key)?.let { it to name } }
            .toMap(),
    )

fun FolderColorStore.writeColors(colors: FolderColors) = write(colors.names.mapKeys { (key, _) -> key.storageValue })

/** Forgets a removed account's colors. */
fun FolderColorStore.forgetAccount(accountId: String) = writeColors(readColors().withoutAccount(accountId))

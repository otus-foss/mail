package com.otus.mail.core.mailbox

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

package com.otusfoss.mail.feature.folders.data

import com.otusfoss.core.jmap.SetMethod
import com.otusfoss.mail.core.mailbox.MailFolder
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.mailbox.folderTree
import com.otusfoss.mail.feature.folders.domain.FolderRule
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * `SieveScript/set` for the new blob: an update of [target], or "Otus Mail" created when there is
 * none. A script that is not running yet is activated in the same call, which stops the one that
 * ran before (RFC 9661, section 2.2).
 */
internal fun scriptSet(
    accountId: String,
    target: SieveScriptObject?,
    blobId: String,
    state: String,
): SetMethod {
    val blob = "blobId" to JsonPrimitive(blobId)
    val activate =
        buildMap<String, JsonElement> {
            if (target?.isActive != true) put("onSuccessActivateScript", JsonPrimitive(target?.id ?: "#$CREATION_ID"))
        }
    return if (target == null) {
        val script = JsonObject(mapOf("name" to JsonPrimitive(RULES_SCRIPT), blob))
        SetMethod(
            SieveScriptType,
            accountId,
            create = mapOf(CREATION_ID to script),
            ifInState = state,
            extra = activate,
        )
    } else {
        SetMethod(
            SieveScriptType,
            accountId,
            update = mapOf(target.id to JsonObject(mapOf(blob))),
            ifInState = state,
            extra = activate,
        )
    }
}

private const val CREATION_ID = "rules"

/**
 * The rules as block folders in the account's folder order, each with its full path. Rules for a
 * folder the account no longer has come last and fall back to the inbox, as mail filed into a
 * deleted folder would.
 */
internal fun Map<String, List<FolderRule>>.inFolderOrder(folders: List<MailFolder>): List<BlockFolder> {
    val paths = mailboxPaths(folders)
    val ordered = folderTree(folders).map { it.folder.id }
    val known = ordered.toSet()
    return (ordered + keys.filterNot { it in known })
        .mapNotNull { id -> get(id)?.takeIf { it.isNotEmpty() }?.let { BlockFolder(id, paths[id] ?: SIEVE_INBOX, it) } }
}

/** Sieve names the inbox `INBOX` in any case (RFC 5228, section 4.1). */
private const val SIEVE_INBOX = "INBOX"

/** Each mailbox's names from the top down, joined by [FOLDER_PATH_SEPARATOR]; a parent cycle stops the walk. */
private fun mailboxPaths(mailboxes: List<MailFolder>): Map<String, String> {
    val byId = mailboxes.associateBy { it.id }
    return mailboxes.associate { mailbox ->
        val seen = mutableSetOf<String>()
        val path =
            if (mailbox.role == MailboxRole.Inbox) {
                SIEVE_INBOX
            } else {
                generateSequence(mailbox) { byId[it.parentId] }
                    .takeWhile { seen.add(it.id) }
                    .toList()
                    .reversed()
                    .joinToString(FOLDER_PATH_SEPARATOR) { if (it.role == MailboxRole.Inbox) SIEVE_INBOX else it.name }
            }
        mailbox.id to path
    }
}

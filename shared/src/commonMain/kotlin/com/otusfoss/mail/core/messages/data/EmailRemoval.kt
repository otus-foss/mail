package com.otusfoss.mail.core.messages.data

import com.otusfoss.core.jmap.GetMethod
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.JmapException
import com.otusfoss.core.jmap.JmapIds
import com.otusfoss.core.jmap.QueryMethod
import com.otusfoss.core.jmap.SetMethod
import com.otusfoss.core.jmap.login.LoginStoreException
import com.otusfoss.mail.core.cache.CacheKind
import com.otusfoss.mail.core.cache.CacheStorageException
import com.otusfoss.mail.core.cache.MailCache
import com.otusfoss.mail.core.cache.withAccount
import com.otusfoss.mail.core.mail.EMAIL_FOLDERS_PROPERTIES
import com.otusfoss.mail.core.mail.EmailFoldersType
import com.otusfoss.mail.core.mail.EmailType
import com.otusfoss.mail.core.mail.MailAccount
import com.otusfoss.mail.core.mail.MailboxObject
import com.otusfoss.mail.core.mail.allObjects
import com.otusfoss.mail.core.mail.inMailbox
import com.otusfoss.mail.core.mail.mailAccount
import com.otusfoss.mail.core.mail.mailboxPatch
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.Deletion
import com.otusfoss.mail.core.messages.domain.MailMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Deletes emails for good: each email only in its folder is destroyed, any other one is taken out
 * of it, in `Email/set` calls of at most the session's `maxObjectsInSet` objects (RFC 8620,
 * section 2). Each batch rereads membership and conditions its write on that Email state, so a
 * concurrent move cannot destroy a copy filed elsewhere. What left the folder leaves [cache] too.
 * It needs the server: a deletion is decided by what the server holds now, so it never waits offline.
 */
internal class EmailRemoval(
    private val connections: JmapConnections,
    private val cache: MailCache,
) {
    /** Removes the emails [find] returns from their folders, batch by batch; complete only when every one left. */
    suspend fun remove(
        loginId: String,
        find: suspend MailAccount.() -> List<FolderEmail>,
    ): Deletion {
        val removed = mutableSetOf<String>()
        // A failed batch keeps the ones before it, so the caller knows what left.
        return try {
            val account = connections.mailAccount(loginId) ?: return Deletion(emptySet(), complete = false)
            val emails = account.find()
            val batchSize = minOf(account.session.setBatch, account.session.getBatch)
            emails.chunked(batchSize).forEach { removed += account.removeBatch(it) }
            Deletion(removed, complete = removed.size == emails.size)
        } catch (expected: JmapException) {
            Deletion(removed, complete = false)
        } catch (expected: LoginStoreException) {
            Deletion(removed, complete = false)
        }
    }

    /** Empties [folderId] only when it is a Trash or Spam folder (or when no mailboxes are cached yet). */
    suspend fun empty(
        loginId: String,
        folderId: String,
    ): Deletion {
        val role = cachedEmptyRole(loginId, folderId) ?: return Deletion(emptySet(), complete = false)
        return remove(loginId) { folderEmails(folderId, role) }
    }

    /**
     * Role of [folderId] when [cache] holds [loginId]'s mailboxes, or [MailboxRole.Trash] when the
     * cache has none yet. Null when [cache] knows [loginId]'s mailboxes and [folderId] is not a bin.
     */
    private suspend fun cachedEmptyRole(
        loginId: String,
        folderId: String,
    ): MailboxRole? {
        val mailboxes =
            cache.withAccount(loginId) { accountCache ->
                try {
                    accountCache.allObjects(loginId, CacheKind.Mailbox, MailboxObject.serializer())
                } catch (expected: CacheStorageException) {
                    emptyMap()
                }
            }
        if (mailboxes.isEmpty()) return MailboxRole.Trash
        return MailboxRole.of(mailboxes[folderId]?.role)?.takeIf { it.isBin }
    }

    private suspend fun MailAccount.removeBatch(emails: List<FolderEmail>): Set<String> {
        val folders = emails.filter { it.folderRole?.isBin == true }.associate { it.emailId to it.folderId }
        if (folders.isEmpty()) return emptySet()
        val fresh =
            connection.call(
                GetMethod(EmailFoldersType, id, JmapIds.Of(folders.keys.toList()), EMAIL_FOLDERS_PROPERTIES),
            )
        val (present, absent) =
            fresh.list
                .mapNotNull { email ->
                    folders[email.id]?.let { folder -> FolderEmail(email.id, folder, email.mailboxIds.keys) }
                }.partition { it.folderId in it.mailboxIds }
        val alreadyRemoved = absent.map { it.emailId }.toSet() + fresh.notFound
        return if (present.isEmpty()) {
            alreadyRemoved
        } else {
            val (elsewhere, onlyHere) = present.partition { it.elsewhere }
            val takenOut = elsewhere.associate { it.emailId to mailboxPatch(it.folderId, false) }
            val result =
                connection.call(
                    SetMethod(
                        EmailType,
                        id,
                        update = takenOut,
                        destroy = onlyHere.map { it.emailId },
                        ifInState = fresh.state,
                    ),
                )
            forget(login.id, result.destroyed.toSet(), takenOut.filterKeys { it in result.updated })
            alreadyRemoved + result.destroyed + result.updated.keys
        }
    }

    /** Shows the batch's outcome in the cache; a cache failure does not undo what the server did. */
    private suspend fun forget(
        loginId: String,
        destroyed: Set<String>,
        takenOut: Map<String, JsonObject>,
    ) {
        cache.withAccount(loginId) { accountCache ->
            try {
                accountCache.patchEmails(loginId, takenOut)
                if (destroyed.isNotEmpty()) {
                    accountCache.write(loginId) {
                        remove(CacheKind.Email, destroyed)
                        remove(CacheKind.Member, destroyed)
                    }
                }
            } catch (expected: CacheStorageException) {
                // The next sync reads the server's answer.
            }
        }
    }
}

/**
 * Every email in [folderId], page by page. Paging finishes before anything changes, so deleting
 * cannot shift the pages. A page with no new ids ends it, even if the server repeats one.
 */
internal suspend fun MailAccount.folderEmails(
    folderId: String,
    folderRole: MailboxRole,
): List<FolderEmail> {
    val page = connection.session().getBatch
    val found = linkedMapOf<String, FolderEmail>()
    while (true) {
        val emails =
            connection.request {
                val query =
                    call(
                        QueryMethod(
                            EmailType,
                            id,
                            inMailbox(folderId),
                            limit = page,
                            extra = mapOf("position" to JsonPrimitive(found.size)),
                        ),
                    )
                call(GetMethod(EmailFoldersType, id, JmapIds.Result(query.resultOf("/ids")), EMAIL_FOLDERS_PROPERTIES))
            }
        val fresh = emails.list.filter { it.id !in found }
        if (fresh.isEmpty()) return found.values.toList()
        fresh.forEach { found[it.id] = FolderEmail(it.id, folderId, it.mailboxIds.keys, folderRole) }
    }
}

/**
 * Each email of this row's conversation in the folder it is listed in, or the row's own email
 * when it stands alone.
 */
internal fun MailMessage.listedMembers(): List<FolderEmail> =
    conversation
        ?.members
        ?.filter { it.emailId.isNotEmpty() }
        ?.map { member ->
            val folderId = member.mailboxId.ifEmpty { location.mailboxId }
            val role = member.mailboxRole ?: location.mailboxRole.takeIf { folderId == location.mailboxId }
            FolderEmail(member.emailId, folderId, member.mailboxIds, role)
        }
        ?: listOf(FolderEmail(emailId, location.mailboxId, location.mailboxIds, location.mailboxRole))

/** An email in [folderId] that is [elsewhere] when it is in other mailboxes too. */
internal class FolderEmail(
    val emailId: String,
    val folderId: String,
    val mailboxIds: Set<String>,
    val folderRole: MailboxRole? = null,
) {
    val elsewhere: Boolean = (mailboxIds - folderId).isNotEmpty()
}

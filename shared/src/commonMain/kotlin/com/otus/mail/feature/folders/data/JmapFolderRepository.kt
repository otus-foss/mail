package com.otus.mail.feature.folders.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.SetMethod
import com.otus.jmap.jmapDecoding
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginStore
import com.otus.mail.core.mail.MAILBOX_PROPERTIES
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.MailCapability
import com.otus.mail.core.mail.MailFailure
import com.otus.mail.core.mail.MailboxType
import com.otus.mail.core.mail.mailAccount
import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.FolderFailure
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.Mailboxes
import com.otus.mail.core.mailbox.SharedMailboxes
import com.otus.mail.core.mailbox.toFolder
import com.otus.mail.feature.folders.domain.FolderOutcome
import com.otus.mail.feature.folders.domain.FolderPlacement
import com.otus.mail.feature.folders.domain.FolderRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Folders through [Mailboxes] and `Mailbox/set` (RFC 8621, section 2). Logins load in parallel
 * and fail alone. Each login also loads the mailboxes other users share with it, from every session
 * account with mail and `isPersonal: false` (RFC 8620, section 2); a shared account that fails is
 * left out. A change shows once the folders load again, which reads it as a `Mailbox` change.
 */
class JmapFolderRepository(
    private val connections: JmapConnections,
    private val mailboxes: Mailboxes,
) : FolderRepository {
    override suspend fun accounts(): List<AccountFolders> =
        coroutineScope { folderStorage { connections.savedLogins() }.map { async { load(it) } }.awaitAll() }

    override suspend fun cachedAccounts(): List<AccountFolders> =
        folderStorage { connections.savedLogins() }.map { login ->
            AccountFolders(login.id, login.name, mailboxes.cached(login.id))
        }

    private suspend fun load(login: JmapLogin): AccountFolders =
        try {
            val account = connections.mailAccount(login)
            coroutineScope {
                val shared = account.sharedAccountIds().map { async { account.loadShared(it) } }
                AccountFolders(
                    login.id,
                    login.name,
                    jmapDecoding { mailboxes.sync(account) },
                    limits(account.mailCapability(account.id)),
                    mailboxOwner = account.ownerName(account.id),
                    shared = shared.awaitAll().filterNotNull(),
                )
            }
        } catch (error: JmapException) {
            AccountFolders(login.id, login.name, failure = error.toFolderFailure())
        }

    override suspend fun create(
        accountId: String,
        name: String,
        parentId: String?,
    ): FolderOutcome {
        // Subscribed, so IMAP clients that list only subscribed folders show it too.
        val folder =
            mapOf(
                "name" to JsonPrimitive(name.trim()),
                "parentId" to parentId.json(),
                "isSubscribed" to JsonPrimitive(true),
            )
        return change(accountId, null, CREATION_ID) {
            SetMethod(MailboxType, it, create = mapOf(CREATION_ID to JsonObject(folder)))
        }
    }

    override suspend fun rename(
        folder: MailFolder,
        name: String,
    ): FolderOutcome = update(folder, mapOf(folder.id to mapOf("name" to JsonPrimitive(name.trim()))))

    override suspend fun place(placement: FolderPlacement): FolderOutcome {
        val folder = placement.folder
        val orders = placement.sortOrders.mapValues { (_, order) -> mapOf("sortOrder" to JsonPrimitive(order)) }
        val parent = mapOf("parentId" to placement.parentId.json()).takeIf { placement.changesParent }.orEmpty()
        val own = orders[folder.id].orEmpty() + parent
        return update(folder, if (own.isEmpty()) orders else orders + (folder.id to own))
    }

    override suspend fun delete(folder: MailFolder): FolderOutcome =
        change(folder.accountId, folder.sharedIn, folder.id) {
            // Refusing to delete contained mail keeps a mistaken delete from losing messages.
            SetMethod(
                MailboxType,
                it,
                destroy = listOf(folder.id),
                extra = mapOf("onDestroyRemoveEmails" to JsonPrimitive(false)),
            )
        }

    /** Sends [patches], keyed by folder id, for [folder] and its siblings in one `Mailbox/set`. */
    private suspend fun update(
        folder: MailFolder,
        patches: Map<String, Map<String, JsonElement>>,
    ): FolderOutcome =
        change(folder.accountId, folder.sharedIn, folder.id) {
            SetMethod(MailboxType, it, update = patches.mapValues { (_, patch) -> JsonObject(patch) })
        }

    /**
     * Sends one `Mailbox/set` to [jmapAccountId], or to the login's own mail account when it is
     * null; [target] is the creation id or the changed folder's id.
     */
    private suspend fun change(
        loginId: String,
        jmapAccountId: String?,
        target: String,
        method: (accountId: String) -> SetMethod,
    ): FolderOutcome =
        try {
            val account =
                folderStorage { connections.mailAccount(loginId) }
                    ?: return FolderOutcome.Failed(FolderFailure.NotFound)
            val result = account.connection.call(method(jmapAccountId ?: account.id))
            val error = result.firstError
            when {
                error != null -> FolderOutcome.Failed(error.toFolderFailure())
                target == CREATION_ID -> result.createdId(target)?.let { FolderOutcome.Done(it) }
                else -> FolderOutcome.Done(target)
            } ?: FolderOutcome.Failed(FolderFailure.Mail(MailFailure.ServerProblem))
        } catch (error: JmapException) {
            FolderOutcome.Failed(error.toFolderFailure())
        }

    private companion object {
        const val CREATION_ID = "folder"
    }
}

/** Accounts other users share with this login that hold mail (RFC 8620, section 2). */
private fun MailAccount.sharedAccountIds(): List<String> =
    session.accounts
        .filter { (jmapId, shared) -> jmapId != id && !shared.isPersonal && JmapCapability.MAIL in shared.capabilities }
        .keys
        .toList()

private fun MailAccount.mailCapability(jmapAccountId: String): MailCapability? =
    session.accountCapability(jmapAccountId, JmapCapability.MAIL, MailCapability.serializer())

private fun String?.json(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull

/** The mailboxes [jmapAccountId] shares, read whole each time: the cache keeps only the login's own. */
private suspend fun MailAccount.loadShared(jmapAccountId: String): SharedMailboxes? =
    try {
        val mailboxes = connection.call(GetMethod(MailboxType, jmapAccountId, properties = MAILBOX_PROPERTIES))
        SharedMailboxes(
            jmapAccountId,
            ownerName(jmapAccountId),
            jmapDecoding { mailboxes.list.map { it.toFolder(login.id, sharedIn = jmapAccountId) } },
            limits(mailCapability(jmapAccountId)),
        )
    } catch (expected: JmapException) {
        null
    }

private fun MailAccount.ownerName(jmapAccountId: String): String =
    session.accounts[jmapAccountId]
        ?.name
        .orEmpty()

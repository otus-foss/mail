package com.otus.mail.feature.folders.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.SetMethod
import com.otus.jmap.jmapDecoding
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginStore
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.mail.MAILBOX_PROPERTIES
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.MailAccounts
import com.otus.mail.core.mail.MailboxType
import com.otus.mail.core.mail.mailboxes
import com.otus.mail.core.mail.replaceMailboxes
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.FolderFailure
import com.otus.mail.feature.folders.domain.FolderOutcome
import com.otus.mail.feature.folders.domain.FolderPlacement
import com.otus.mail.feature.folders.domain.FolderRepository
import com.otus.mail.feature.folders.domain.FolderStorageException
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.SharedMailboxes
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Folders through `Mailbox/get` and `Mailbox/set` (RFC 8621, section 2). Logins load in parallel
 * and fail alone. Each login also loads the mailboxes other users share with it, from every session
 * account with mail and `isPersonal: false` (RFC 8620, section 2); a shared account that fails is
 * left out. Each login's own mailboxes replace the ones in [cache], which the inbox shares.
 */
class JmapFolderRepository(
    connections: JmapConnections,
    logins: LoginStore,
    private val cache: MailCache = InMemoryMailCache(),
) : FolderRepository {
    private val accounts = MailAccounts(connections, logins, ::FolderStorageException)

    override suspend fun accounts(): List<AccountFolders> =
        coroutineScope { accounts.logins().map { async { load(it) } }.awaitAll() }

    override suspend fun cachedAccounts(): List<AccountFolders> =
        accounts.logins().map { login ->
            AccountFolders(login.id, login.name, cache.mailboxes(login.id).map { it.toFolder(login.id) })
        }

    private suspend fun load(login: JmapLogin): AccountFolders =
        try {
            val account = accounts.open(login)
            coroutineScope {
                val shared =
                    account.sharedAccountIds().map {
                        async {
                            account.loadShared(
                                it,
                            ) { id -> folders(account, id) }
                        }
                    }
                AccountFolders(
                    login.id,
                    login.name,
                    folders(account, account.id),
                    limits(account.mailCapability(account.id)),
                    mailboxOwner =
                        account.session.accounts[account.id]
                            ?.name
                            .orEmpty(),
                    shared = shared.awaitAll().filterNotNull(),
                )
            }
        } catch (error: JmapException) {
            AccountFolders(login.id, login.name, failure = error.toFolderFailure())
        }

    private suspend fun folders(
        account: MailAccount,
        jmapAccountId: String,
    ): List<MailFolder> {
        val mailboxes =
            account.connection.call(GetMethod(MailboxType, jmapAccountId, properties = MAILBOX_PROPERTIES))
        val sharedIn = jmapAccountId.takeIf { it != account.id }
        val folders = jmapDecoding { mailboxes.list.map { it.toFolder(account.login.id, sharedIn) } }
        // Only the login's own mailboxes: the cache keeps one set per login, which the inbox reads too.
        if (sharedIn == null) cache.write(account.login.id) { replaceMailboxes(mailboxes.list) }
        return folders
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
            val account = accounts.open(loginId) ?: return FolderOutcome.Failed(FolderFailure.NotFound)
            val result = account.connection.call(method(jmapAccountId ?: account.id))
            val error = result.firstError
            when {
                error != null -> FolderOutcome.Failed(error.toFolderFailure())
                target == CREATION_ID -> result.createdId(target)?.let { FolderOutcome.Done(it) }
                else -> FolderOutcome.Done(target)
            } ?: FolderOutcome.Failed(FolderFailure.ServerProblem)
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

private fun MailAccount.mailCapability(jmapAccountId: String): JsonObject? =
    session.accounts[jmapAccountId]?.capabilityObjects?.get(JmapCapability.MAIL)

private fun String?.json(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull

private suspend fun MailAccount.loadShared(
    jmapAccountId: String,
    folders: suspend (jmapAccountId: String) -> List<MailFolder>,
): SharedMailboxes? =
    try {
        SharedMailboxes(
            jmapAccountId,
            session.accounts[jmapAccountId]
                ?.name
                .orEmpty(),
            folders(jmapAccountId),
            limits(mailCapability(jmapAccountId)),
        )
    } catch (expected: JmapException) {
        null
    }

package com.otus.mail.feature.folders.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.JmapJson
import com.otus.jmap.SetError
import com.otus.jmap.SetMethod
import com.otus.jmap.jmapDecoding
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginStore
import com.otus.mail.core.mail.MAILBOX_PROPERTIES
import com.otus.mail.core.mail.MailCapability
import com.otus.mail.core.mail.MailboxObject
import com.otus.mail.core.mail.MailboxType
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.FolderFailure
import com.otus.mail.feature.folders.domain.FolderLimits
import com.otus.mail.feature.folders.domain.FolderOutcome
import com.otus.mail.feature.folders.domain.FolderRepository
import com.otus.mail.feature.folders.domain.FolderRights
import com.otus.mail.feature.folders.domain.MailFolder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Folders through `Mailbox/get` and `Mailbox/set` (RFC 8621, section 2). Logins load in parallel and fail alone. */
class JmapFolderRepository(
    connections: JmapConnections,
    logins: LoginStore,
) : FolderRepository {
    private val accounts = MailAccounts(connections, logins)

    override suspend fun accounts(): List<AccountFolders> =
        coroutineScope { accounts.logins().map { async { load(it) } }.awaitAll() }

    private suspend fun load(login: JmapLogin): AccountFolders =
        try {
            val account = accounts.open(login)
            val mailboxes =
                account.connection.call(GetMethod(MailboxType, account.accountId, properties = MAILBOX_PROPERTIES))
            AccountFolders(
                login.id,
                login.name,
                jmapDecoding { mailboxes.list.map { it.toFolder(login.id) } },
                limits(
                    account.session.accounts[account.accountId]
                        ?.capabilityObjects
                        ?.get(JmapCapability.MAIL),
                ),
            )
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
                "parentId" to (parentId?.let(::JsonPrimitive) ?: JsonNull),
                "isSubscribed" to JsonPrimitive(true),
            )
        return change(accountId, CREATION_ID) {
            SetMethod(
                MailboxType,
                it,
                create =
                    mapOf(CREATION_ID to JsonObject(folder)),
            )
        }
    }

    override suspend fun rename(
        folder: MailFolder,
        name: String,
    ): FolderOutcome = update(folder, "name", JsonPrimitive(name.trim()))

    override suspend fun move(
        folder: MailFolder,
        parentId: String?,
    ): FolderOutcome = update(folder, "parentId", parentId?.let(::JsonPrimitive) ?: JsonNull)

    override suspend fun delete(folder: MailFolder): FolderOutcome =
        change(folder.accountId, folder.id) {
            // Refusing to delete contained mail keeps a mistaken delete from losing messages.
            SetMethod(
                MailboxType,
                it,
                destroy = listOf(folder.id),
                extra = mapOf("onDestroyRemoveEmails" to JsonPrimitive(false)),
            )
        }

    private suspend fun update(
        folder: MailFolder,
        property: String,
        value: JsonElement,
    ): FolderOutcome =
        change(folder.accountId, folder.id) {
            SetMethod(MailboxType, it, update = mapOf(folder.id to JsonObject(mapOf(property to value))))
        }

    /** Sends one `Mailbox/set`; [target] is the creation id or the changed folder's id. */
    private suspend fun change(
        loginId: String,
        target: String,
        method: (accountId: String) -> SetMethod,
    ): FolderOutcome =
        try {
            val account = accounts.open(loginId) ?: return FolderOutcome.Failed(FolderFailure.NotFound)
            val result = account.connection.call(method(account.accountId))
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

private fun MailboxObject.toFolder(loginId: String) =
    MailFolder(
        accountId = loginId,
        id = id,
        name = name,
        parentId = parentId,
        role = role?.lowercase(),
        sortOrder = sortOrder,
        unread = unreadEmails,
        total = totalEmails,
        rights =
            FolderRights(
                mayAddItems = myRights.mayAddItems,
                mayCreateChild = myRights.mayCreateChild,
                mayRename = myRights.mayRename,
                mayDelete = myRights.mayDelete,
                mayRemoveItems = myRights.mayRemoveItems,
                maySetSeen = myRights.maySetSeen,
                maySetKeywords = myRights.maySetKeywords,
            ),
    )

/** A malformed capability keeps the defaults; the server still enforces its real limits. */
private fun limits(capability: JsonObject?): FolderLimits {
    val mail =
        capability?.let { runCatching { JmapJson.decodeFromJsonElement(MailCapability.serializer(), it) }.getOrNull() }
            ?: return FolderLimits()
    return FolderLimits(
        mayCreateTopLevel = mail.mayCreateTopLevelMailbox,
        maxDepth = mail.maxMailboxDepth?.coerceIn(1, Int.MAX_VALUE.toLong())?.toInt(),
        maxNameBytes = mail.maxSizeMailboxName?.coerceIn(1, Int.MAX_VALUE.toLong())?.toInt(),
    )
}

private fun SetError.toFolderFailure(): FolderFailure =
    when (type) {
        "alreadyExists" -> FolderFailure.AlreadyExists
        "forbidden" -> FolderFailure.NotPermitted
        "invalidProperties", "willDestroy" -> FolderFailure.Invalid
        "mailboxHasChild" -> FolderFailure.HasSubfolders
        "mailboxHasEmail" -> FolderFailure.HasMail
        "notFound" -> FolderFailure.NotFound
        else -> FolderFailure.ServerProblem
    }

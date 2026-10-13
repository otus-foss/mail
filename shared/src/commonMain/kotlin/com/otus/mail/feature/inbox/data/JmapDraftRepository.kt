package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.QueryMethod
import com.otus.jmap.SetError
import com.otus.jmap.SetMethod
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.IdentityType
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.MailboxType
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.DraftException
import com.otus.mail.feature.inbox.domain.DraftFailure
import com.otus.mail.feature.inbox.domain.DraftRepository
import com.otus.mail.feature.inbox.domain.MailSender
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Drafts as Email objects in the mailbox with role `drafts` (RFC 8621, sections 2 and 4.6), and
 * sender addresses from `Identity/get` (section 6). Email content cannot change, so a save creates
 * a new Email and then destroys the previous copy in a second `Email/set`.
 */
class JmapDraftRepository(
    private val connections: JmapConnections,
    private val logins: LoginStore,
) : DraftRepository {
    override suspend fun identities(accountId: String): List<MailSender> =
        try {
            mailAccount(connections, logins, accountId)
                ?.takeIf { it.mayUse(JmapCapability.SUBMISSION) }
                ?.let { account ->
                    account.connection
                        .call(GetMethod(IdentityType, account.id, properties = listOf("id", "name", "email")))
                        .list
                        .map { MailSender(it.name.ifBlank { it.email }, it.email, accountId) }
                }.orEmpty()
        } catch (expected: JmapException) {
            emptyList()
        } catch (expected: LoginStoreException) {
            emptyList()
        }

    override suspend fun save(draft: Draft): Result<String> =
        attempt(draft.accountId) { account ->
            val drafts = draftsFolder(account) ?: throw DraftException(DraftFailure.NoDraftsFolder)
            val create = SetMethod(EmailType, account.id, create = mapOf(CREATION_ID to draft.toEmail(drafts)))
            val result = account.connection.call(create)
            result.notCreated[CREATION_ID]?.let { throw DraftException(it.toDraftFailure()) }
            val id = result.createdId(CREATION_ID) ?: throw DraftException(DraftFailure.ServerProblem)
            // Each operation of one call succeeds or fails alone (RFC 8620, section 5.3), so the old
            // copy is destroyed only once the server has stored the new one.
            draft.emailId?.let { account.destroyReplaced(it) }
            id
        }

    override suspend fun discard(
        accountId: String,
        emailId: String,
    ): Result<Unit> =
        attempt(accountId) { account ->
            val result = account.connection.call(SetMethod(EmailType, account.id, destroy = listOf(emailId)))
            result.notDestroyed[emailId]
                ?.takeUnless { it.type == "notFound" }
                ?.let { throw DraftException(it.toDraftFailure()) }
        }

    /**
     * Destroys [emailId], the copy a stored save replaced. A copy the server keeps, or a failed
     * request, stays in Drafts as a duplicate: the new copy already holds the draft, so the save
     * still succeeds. A copy that is already gone needs no destroying.
     */
    private suspend fun MailAccount.destroyReplaced(emailId: String) {
        try {
            connection.call(SetMethod(EmailType, id, destroy = listOf(emailId)))
        } catch (expected: JmapException) {
            // A leftover copy is only a duplicate, so the stored save is not reported as failed.
        }
    }

    private suspend fun draftsFolder(account: MailAccount): String? =
        account.connection
            .call(QueryMethod(MailboxType, account.id, buildJsonObject { put("role", DRAFTS_ROLE) }))
            .ids
            .firstOrNull()

    private suspend fun <T> attempt(
        loginId: String,
        block: suspend (MailAccount) -> T,
    ): Result<T> =
        try {
            val account = mailAccount(connections, logins, loginId) ?: throw DraftException(DraftFailure.ServerProblem)
            Result.success(block(account))
        } catch (error: DraftException) {
            Result.failure(error)
        } catch (error: JmapException) {
            Result.failure(DraftException(error.toDraftFailure(), error))
        } catch (error: LoginStoreException) {
            Result.failure(DraftException(DraftFailure.ServerProblem, error))
        }

    private companion object {
        const val CREATION_ID = "draft"
        const val DRAFTS_ROLE = "drafts"
    }
}

/** Identities and submission must live in the account that holds the drafts. */
private fun MailAccount.mayUse(capability: String): Boolean =
    capability in session.capabilities && session.accounts[id]?.capabilities?.contains(capability) == true

private fun SetError.toDraftFailure(): DraftFailure =
    if (type == "forbidden") DraftFailure.NotPermitted else DraftFailure.ServerProblem

private fun JmapException.toDraftFailure(): DraftFailure =
    when (this) {
        is JmapException.Forbidden -> {
            DraftFailure.NotPermitted
        }

        is JmapException.MethodError -> {
            if (type ==
                "forbidden"
            ) {
                DraftFailure.NotPermitted
            } else {
                DraftFailure.ServerProblem
            }
        }

        is JmapException.Unreachable -> {
            DraftFailure.Unreachable
        }

        else -> {
            DraftFailure.ServerProblem
        }
    }

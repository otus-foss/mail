package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.SetError
import com.otus.jmap.SetMethod
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.mail.DRAFTS_ROLE
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.IdentityType
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.mailCallOr
import com.otus.mail.core.mail.roleQuery
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.DraftException
import com.otus.mail.feature.inbox.domain.DraftFailure
import com.otus.mail.feature.inbox.domain.DraftRepository
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.SendIdentity

/**
 * Drafts as Email objects in the mailbox with role `drafts` (RFC 8621, sections 2 and 4.6), and
 * sender addresses from `Identity/get` (section 6). Email content cannot change, so a save creates
 * a new Email and then destroys the previous copy in a second `Email/set`.
 */
class JmapDraftRepository(
    private val connections: JmapConnections,
    private val logins: LoginStore,
) : DraftRepository {
    override suspend fun identities(accountId: String): List<SendIdentity> =
        mailCallOr(emptyList()) {
            val account =
                mailAccount(connections, logins, accountId)
                    ?.takeIf { it.mayUse(JmapCapability.SUBMISSION) } ?: return@mailCallOr emptyList()
            val request = GetMethod(IdentityType, account.id, properties = IDENTITY_PROPERTIES)
            val identities = account.connection.call(request)
            identities.list.map {
                SendIdentity(
                    it.id,
                    MailSender(it.name.ifBlank { it.email }, it.email, accountId),
                    it.textSignature,
                    it.htmlSignature,
                )
            }
        }

    override suspend fun save(draft: Draft): Result<String> =
        attempt(draft.accountId) { account ->
            val drafts = draftsFolder(account) ?: throw DraftException(DraftFailure.NoDraftsFolder)
            val create =
                SetMethod(
                    EmailType,
                    account.id,
                    create =
                        mapOf(CREATION_ID to draft.toEmail(drafts, account.inlineImages(draft.htmlContent()))),
                )
            val result = account.connection.call(create)
            val failure = result.notCreated[CREATION_ID]
            if (failure != null) throw DraftException(failure.toDraftFailure())
            val id = result.createdId(CREATION_ID) ?: throw DraftException(DraftFailure.ServerProblem)
            // Each operation of one call succeeds or fails alone (RFC 8620, section 5.3), so the old
            // copy is destroyed only once the server has stored the new one.
            val previous = draft.emailId
            if (previous != null) account.destroyReplaced(previous)
            id
        }

    override suspend fun discard(
        accountId: String,
        emailId: String,
    ): Result<Unit> =
        attempt(accountId) { account ->
            val result = account.connection.call(SetMethod(EmailType, account.id, destroy = listOf(emailId)))
            val failure = result.notDestroyed[emailId]
            if (failure != null && failure.type != "notFound") throw DraftException(failure.toDraftFailure())
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

    private suspend fun draftsFolder(account: MailAccount): String? {
        val folders = account.connection.call(account.roleQuery(DRAFTS_ROLE))
        return folders.ids.firstOrNull()
    }

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
        val IDENTITY_PROPERTIES = listOf("id", "name", "email", "textSignature", "htmlSignature")
        const val CREATION_ID = "draft"
    }
}

/** Identities and submission must live in the account that holds the drafts. */
internal fun MailAccount.mayUse(capability: String): Boolean =
    capability in session.capabilities && session.accounts[id]?.capabilities?.contains(capability) == true

private fun SetError.toDraftFailure(): DraftFailure = draftFailure(type)

private fun JmapException.toDraftFailure(): DraftFailure =
    when (this) {
        is JmapException.Forbidden -> DraftFailure.NotPermitted
        is JmapException.MethodError -> draftFailure(type)
        is JmapException.Unreachable -> DraftFailure.Unreachable
        else -> DraftFailure.ServerProblem
    }

private fun draftFailure(type: String): DraftFailure =
    if (type == "forbidden") DraftFailure.NotPermitted else DraftFailure.ServerProblem

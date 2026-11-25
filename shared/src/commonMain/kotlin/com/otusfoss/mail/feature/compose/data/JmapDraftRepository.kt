package com.otusfoss.mail.feature.compose.data

import com.otusfoss.core.jmap.GetMethod
import com.otusfoss.core.jmap.JmapCapability
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.JmapException
import com.otusfoss.core.jmap.JmapIds
import com.otusfoss.core.jmap.JmapJson
import com.otusfoss.core.jmap.SetError
import com.otusfoss.core.jmap.SetMethod
import com.otusfoss.core.jmap.jmapCallOr
import com.otusfoss.core.jmap.login.LoginStoreException
import com.otusfoss.mail.core.cache.CacheStorageException
import com.otusfoss.mail.core.cache.MailCache
import com.otusfoss.mail.core.cache.withAccount
import com.otusfoss.mail.core.mail.EmailType
import com.otusfoss.mail.core.mail.IdentityType
import com.otusfoss.mail.core.mail.Keywords
import com.otusfoss.mail.core.mail.MailAccount
import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.mail.mailAccount
import com.otusfoss.mail.core.mail.toMailFailure
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.DraftException
import com.otusfoss.mail.core.messages.domain.DraftFailure
import com.otusfoss.mail.core.messages.domain.DraftRepository
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.SendIdentity
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Drafts as Email objects in the mailbox with role `drafts` (RFC 8621, sections 2 and 4.6), and
 * sender addresses from `Identity/get` (section 6). Email content cannot change, so a save creates
 * a new Email and then destroys the previous copy in a second `Email/set`. Each copy gets its own
 * Message-ID from [newMessageId], so a copy whose save lost its answer is found and destroyed by
 * the next save.
 */
internal class JmapDraftRepository(
    private val connections: JmapConnections,
    private val copies: DraftCopies,
    private val cache: MailCache,
    private val newMessageId: () -> String,
) : DraftRepository {
    /**
     * The server's identities, which the device keeps, so a message can be written and signed
     * offline. When the server does not answer, the kept ones come back.
     */
    override suspend fun identities(accountId: String): List<SendIdentity> =
        cache.withAccount(accountId) { kept ->
            val loaded = jmapCallOr(null) { loadIdentities(accountId) }
            try {
                if (loaded == null) kept.identities(accountId) else loaded.also { kept.keepIdentities(accountId, it) }
            } catch (expected: CacheStorageException) {
                loaded.orEmpty()
            }
        }

    private suspend fun loadIdentities(accountId: String): List<SendIdentity> =
        connections
            .mailAccount(accountId)
            ?.takeIf { it.mayUse(JmapCapability.SUBMISSION) }
            ?.let { account ->
                account.connection
                    .call(GetMethod(IdentityType, account.id, properties = IDENTITY_PROPERTIES))
                    .list
                    .map {
                        SendIdentity(
                            it.id,
                            MailSender(it.name.ifBlank { it.email }, it.email, accountId),
                            it.textSignature,
                            it.htmlSignature,
                        )
                    }
            }.orEmpty()

    override suspend fun save(draft: Draft): Result<String> =
        attempt(draft.accountId) { account ->
            val drafts = copies.draftsFolder(account) ?: throw DraftException(DraftFailure.NoDraftsFolder)
            val messageId = newMessageId()
            val html = account.inlineImages(draft.htmlContent(), copies.images)
            val email = draft.copy(messageId = messageId).toEmail(drafts, html)
            val create = SetMethod(EmailType, account.id, create = mapOf(CREATION_ID to email))
            val result =
                try {
                    account.connection.call(create)
                } catch (refusal: JmapException.MethodError) {
                    throw refusal
                } catch (error: JmapException) {
                    // Without an answer the server may have stored the copy, so a later save removes it.
                    copies.unanswered.remember(account.login.id, messageId)
                    throw error
                }
            val failure = result.notCreated[CREATION_ID]
            if (failure != null) throw DraftException(failure.toDraftFailure())
            val id = result.createdId(CREATION_ID) ?: throw DraftException(DraftFailure.Mail(MailFailure.ServerProblem))
            // Each operation of one call succeeds or fails alone (RFC 8620, section 5.3), so the old
            // copy is destroyed only once the server has stored the new one.
            val previous = draft.emailId
            if (previous != null) account.destroyReplaced(previous)
            copies.unanswered.destroy(account, drafts, kept = id)
            id
        }

    override suspend fun isDraft(
        accountId: String,
        emailId: String,
    ): Result<Boolean> =
        attempt(accountId) { account ->
            account.connection
                .call(GetMethod(EmailType, account.id, JmapIds.Of(listOf(emailId)), KEYWORD_PROPERTIES))
                .list
                .any { Keywords.DRAFT in it.keywords }
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

    private suspend fun <T> attempt(
        loginId: String,
        block: suspend (MailAccount) -> T,
    ): Result<T> =
        try {
            val account =
                connections.mailAccount(loginId)
                    ?: throw DraftException(DraftFailure.Mail(MailFailure.ServerProblem))
            Result.success(block(account))
        } catch (error: DraftException) {
            Result.failure(error)
        } catch (error: JmapException) {
            Result.failure(DraftException(error.toDraftFailure(), error))
        } catch (error: LoginStoreException) {
            Result.failure(DraftException(DraftFailure.Mail(MailFailure.ServerProblem), error))
        } catch (error: SerializationException) {
            // Content that cannot be encoded fails the save rather than the app.
            Result.failure(DraftException(DraftFailure.Mail(MailFailure.ServerProblem), error))
        }

    private companion object {
        val IDENTITY_PROPERTIES = listOf("id", "name", "email", "textSignature", "htmlSignature")
        const val CREATION_ID = "draft"
    }
}

/** The identities kept for [loginId]; none when none were kept or they no longer read. */
private suspend fun MailCache.identities(loginId: String): List<SendIdentity> =
    value(loginId, IDENTITIES_VALUE)
        ?.let { text ->
            try {
                JmapJson.decodeFromString(IDENTITIES, text)
            } catch (expected: SerializationException) {
                null
            }
        }.orEmpty()

private suspend fun MailCache.keepIdentities(
    loginId: String,
    identities: List<SendIdentity>,
) {
    val text = JmapJson.encodeToString(IDENTITIES, identities)
    if (value(loginId, IDENTITIES_VALUE) != text) write(loginId) { putValue(IDENTITIES_VALUE, text) }
}

private val IDENTITIES = ListSerializer(SendIdentity.serializer())

private const val IDENTITIES_VALUE = "identities"

/** A Message-ID of its own for a saved copy of a draft. */
@OptIn(ExperimentalUuidApi::class)
internal fun draftMessageId(): String = "draft-${Uuid.random()}@otus.mail"

/** Identities and submission must live in the account that holds the drafts. */
internal fun MailAccount.mayUse(capability: String): Boolean =
    capability in session.capabilities && session.accounts[id]?.capabilities?.contains(capability) == true

private fun SetError.toDraftFailure(): DraftFailure = draftFailure(type)

private fun JmapException.toDraftFailure(): DraftFailure =
    if (this is JmapException.MethodError) {
        draftFailure(type)
    } else {
        DraftFailure.Mail(toMailFailure())
    }

private fun draftFailure(type: String): DraftFailure =
    DraftFailure.Mail(if (type == "forbidden") MailFailure.NotPermitted else MailFailure.ServerProblem)

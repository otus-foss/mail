package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.GetResult
import com.otus.jmap.JmapCall
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.QueryResult
import com.otus.jmap.SetMethod
import com.otus.jmap.SetResult
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.mail.EmailSubmissionType
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.IdentityObject
import com.otus.mail.core.mail.IdentityType
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.mailCallOr
import com.otus.mail.feature.inbox.domain.AnsweredMessage
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.DraftRestoreException
import com.otus.mail.feature.inbox.domain.DraftSender
import com.otus.mail.feature.inbox.domain.ScheduledSends
import com.otus.mail.feature.inbox.domain.SendException
import com.otus.mail.feature.inbox.domain.SendFailure
import com.otus.mail.feature.inbox.domain.SentMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.time.Instant

/**
 * Sends a draft with `EmailSubmission/set` (RFC 8621, section 7.5). One request stores the draft
 * as written and submits that copy; on success the server moves it to Sent and clears `$draft`.
 * Without an envelope, the server takes the recipients from To, Cc, and Bcc, and strips Bcc.
 */
class JmapDraftSender(
    private val connections: JmapConnections,
    private val logins: LoginStore,
) : DraftSender,
    ScheduledSends {
    override suspend fun send(
        draft: Draft,
        answered: AnsweredMessage?,
        at: Instant?,
    ): Result<SentMessage> =
        try {
            val account = mailAccount(connections, logins, draft.accountId) ?: failSend(SendFailure.ServerProblem)
            val from = draft.from?.address?.takeIf { account.mayUse(JmapCapability.SUBMISSION) }
            val sent = account.submit(draft, account.route(from ?: failSend(SendFailure.NoIdentity)), at)
            account.tidy(draft.emailId, answered)
            Result.success(sent)
        } catch (error: SendException) {
            Result.failure(error)
        } catch (error: JmapException) {
            Result.failure(SendException(error.toSendFailure(), error))
        } catch (error: LoginStoreException) {
            Result.failure(SendException(SendFailure.ServerProblem, error))
        }

    /** The identity that sends as [from], and the folders the sent copy moves between. */
    private suspend fun MailAccount.route(from: String): SendRoute {
        lateinit var identities: JmapCall<GetResult<IdentityObject>>
        lateinit var drafts: JmapCall<QueryResult>
        lateinit var sent: JmapCall<QueryResult>
        val response =
            connection.send {
                identities = call(GetMethod(IdentityType, id, properties = listOf("id", "email")))
                drafts = call(roleQuery(DRAFTS_ROLE))
                sent = call(roleQuery(SENT_ROLE))
            }
        return SendRoute(
            identityId = response[identities].list.sendingAs(from)?.id ?: failSend(SendFailure.NoIdentity),
            drafts = response[drafts].ids.firstOrNull(),
            sent = response[sent].ids.firstOrNull(),
        )
    }

    /**
     * Stores the draft and submits it in one request. A refused submission destroys the copy the
     * request stored, so Drafts keeps only the draft's earlier copy.
     */
    private suspend fun MailAccount.submit(
        draft: Draft,
        route: SendRoute,
        at: Instant?,
    ): SentMessage {
        val folder = route.drafts ?: route.sent ?: failSend(SendFailure.ServerProblem)
        val email = draft.toEmail(folder, inlineImages(draft.htmlContent()))
        lateinit var stored: JmapCall<SetResult>
        lateinit var submitted: JmapCall<SetResult>
        val response =
            connection.send {
                stored = call(SetMethod(EmailType, id, create = mapOf(EMAIL to email)))
                submitted = call(submission(route, folder, at?.let { draft.heldEnvelope(it) }))
            }
        val created = response[stored]
        created.notCreated[EMAIL]?.let { failSend(it.toSendFailure()) }
        val copy = created.createdId(EMAIL) ?: failSend(SendFailure.ServerProblem)
        val refusal =
            try {
                response[submitted].notCreated[SUBMISSION]?.toSendFailure()
            } catch (error: JmapException.MethodError) {
                error.toSendFailure()
            }
        if (refusal != null) {
            bestEffort { connection.call(SetMethod(EmailType, id, destroy = listOf(copy))) }
            failSend(refusal)
        }
        return SentMessage(
            draft.accountId,
            copy,
            response[submitted].createdId(SUBMISSION) ?: failSend(SendFailure.ServerProblem),
        )
    }

    /**
     * The submission of the stored copy. A held one carries its [envelope] and stays in [folder],
     * only losing `$draft`, since the server sends it later; any other moves to Sent at once.
     */
    private fun MailAccount.submission(
        route: SendRoute,
        folder: String,
        envelope: JsonObject?,
    ) = SetMethod(
        EmailSubmissionType,
        id,
        create =
            mapOf(
                SUBMISSION to
                    buildJsonObject {
                        put("emailId", "#$EMAIL")
                        put("identityId", route.identityId)
                        envelope?.let { put("envelope", it) }
                    },
            ),
        extra =
            mapOf(
                "onSuccessUpdateEmail" to
                    buildJsonObject {
                        put("#$SUBMISSION", if (envelope == null) route.sentPatch(folder) else route.heldPatch())
                    },
            ),
    )

    override suspend fun maxDelaySeconds(accountId: String): Long =
        mailCallOr(0L) {
            mailAccount(connections, logins, accountId)
                ?.takeIf { it.mayUse(JmapCapability.SUBMISSION) }
                ?.let { account ->
                    account.session.accounts[account.id]
                        ?.capabilityObjects
                        ?.get(JmapCapability.SUBMISSION)
                        ?.get("maxDelayedSend")
                        ?.jsonPrimitive
                        ?.longOrNull
                } ?: 0L
        }

    override suspend fun cancel(message: SentMessage): Result<Unit> =
        try {
            val account = mailAccount(connections, logins, message.accountId) ?: failSend(SendFailure.ServerProblem)
            val undone =
                account.connection.call(
                    SetMethod(
                        EmailSubmissionType,
                        account.id,
                        update = mapOf(message.submissionId to buildJsonObject { put("undoStatus", "canceled") }),
                    ),
                )
            undone.notUpdated[message.submissionId]?.let { failSend(it.toSendFailure()) }
            if (message.submissionId !in undone.updated) failSend(SendFailure.ServerProblem)
            account.restoreCanceledDraft(message.emailId)
            Result.success(Unit)
        } catch (error: DraftRestoreException) {
            Result.failure(error)
        } catch (error: SendException) {
            Result.failure(error)
        } catch (error: JmapException) {
            Result.failure(SendException(error.toSendFailure(), error))
        } catch (error: LoginStoreException) {
            Result.failure(SendException(SendFailure.ServerProblem, error))
        }

    private suspend fun MailAccount.restoreCanceledDraft(emailId: String) {
        try {
            val restored =
                connection.call(
                    SetMethod(
                        EmailType,
                        id,
                        update = mapOf(emailId to buildJsonObject { put("keywords/$DRAFT_KEYWORD", true) }),
                    ),
                )
            if (emailId !in restored.updated || emailId in restored.notUpdated) throw DraftRestoreException()
        } catch (error: JmapException) {
            throw DraftRestoreException(error)
        }
    }

    /** Deletes the draft's earlier copy [draftId] and marks the answered message. */
    private suspend fun MailAccount.tidy(
        draftId: String?,
        answered: AnsweredMessage?,
    ) {
        val update = answered?.let { mapOf(it.emailId to buildJsonObject { put("keywords/${it.keyword}", true) }) }
        if (draftId == null && update == null) return
        // The message is already sent, so a leftover draft or a missing mark is not a failure.
        bestEffort {
            connection.call(SetMethod(EmailType, id, update = update.orEmpty(), destroy = listOfNotNull(draftId)))
        }
    }

    private companion object {
        const val EMAIL = "draft"
        const val SUBMISSION = "send"
        const val DRAFTS_ROLE = "drafts"
        const val SENT_ROLE = "sent"
    }
}

private suspend fun bestEffort(block: suspend () -> Unit) {
    try {
        block()
    } catch (expected: JmapException) {
        // The caller already knows the outcome that matters; this step only tidies up.
    }
}

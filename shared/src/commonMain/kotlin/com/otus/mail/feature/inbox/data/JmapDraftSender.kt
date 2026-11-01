package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.GetResult
import com.otus.jmap.JmapCall
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.QueryResult
import com.otus.jmap.SetMethod
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.mail.DRAFTS_ROLE
import com.otus.mail.core.mail.EmailSubmissionType
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.IdentityObject
import com.otus.mail.core.mail.IdentityType
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.SENT_ROLE
import com.otus.mail.core.mail.mailCallOr
import com.otus.mail.core.mail.roleQuery
import com.otus.mail.feature.inbox.domain.AnsweredMessage
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.DraftRestoreException
import com.otus.mail.feature.inbox.domain.DraftSender
import com.otus.mail.feature.inbox.domain.ScheduledSends
import com.otus.mail.feature.inbox.domain.SendException
import com.otus.mail.feature.inbox.domain.SendFailure
import com.otus.mail.feature.inbox.domain.SendTime
import com.otus.mail.feature.inbox.domain.SentMessage
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Sends a draft with `EmailSubmission/set` (RFC 8621, section 7.5). One request stores the draft
 * as written, and a second submits that copy by its id; on success the server moves it to Sent and
 * clears `$draft`. Without an envelope, the server takes the recipients from To, Cc, and Bcc, and
 * strips Bcc.
 */
class JmapDraftSender(
    private val connections: JmapConnections,
    private val logins: LoginStore,
) : DraftSender,
    ScheduledSends {
    // Signature images upload once, not at every send.
    private val images = InlineImageUploads()

    override suspend fun send(
        draft: Draft,
        answered: AnsweredMessage?,
        time: SendTime,
    ): Result<SentMessage> = send(draft, answered, time) {}

    override suspend fun send(
        draft: Draft,
        answered: AnsweredMessage?,
        time: SendTime,
        stored: suspend (emailId: String) -> Unit,
    ): Result<SentMessage> =
        try {
            val account = mailAccount(connections, logins, draft.accountId) ?: failSend(SendFailure.ServerProblem)
            val from = draft.from?.address?.takeIf { account.mayUse(JmapCapability.SUBMISSION) }
            val route = account.route(from ?: failSend(SendFailure.NoIdentity))
            val sent = account.submit(draft, route, time, stored)
            // Undo or a cancel can still stop a held or scheduled send, so it takes back a mark it added.
            val marked = if (time == SendTime.Now) null else answered?.let { account.unmarked(it) }
            account.tidySent(draft.emailId, answered)
            Result.success(sent.copy(marked = marked))
        } catch (error: SendException) {
            Result.failure(error)
        } catch (error: JmapException) {
            Result.failure(SendException(error.toSendFailure(), error))
        } catch (error: LoginStoreException) {
            Result.failure(SendException(SendFailure.ServerProblem, error))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Anything else, such as content that cannot be encoded, fails the send rather than the app.
            Result.failure(SendException(SendFailure.ServerProblem))
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
            identityId = response[identities].list.sendingAs(from) { it.email }?.id ?: failSend(SendFailure.NoIdentity),
            drafts = response[drafts].ids.firstOrNull(),
            sent = response[sent].ids.firstOrNull(),
        )
    }

    /**
     * Stores the draft, tells [stored] the copy's id, and then submits that copy, so a submission
     * whose answer is lost can be looked up by the id. A refused submission destroys the copy, so
     * Drafts keeps only the draft's earlier copy.
     */
    private suspend fun MailAccount.submit(
        draft: Draft,
        route: SendRoute,
        time: SendTime,
        stored: suspend (emailId: String) -> Unit,
    ): SentMessage {
        val folder = route.drafts ?: route.sent ?: failSend(SendFailure.ServerProblem)
        val email = draft.toEmail(folder, inlineImages(draft.htmlContent(), images))
        val created = connection.call(SetMethod(EmailType, id, create = mapOf(EMAIL to email)))
        val creationFailure = created.notCreated[EMAIL]
        if (creationFailure != null) failSend(creationFailure.toSendFailure())
        val copy = created.createdId(EMAIL) ?: failSend(SendFailure.ServerProblem)
        stored(copy)
        val submitted =
            try {
                connection.call(submission(route, folder, copy, draft, time))
            } catch (error: JmapException.MethodError) {
                refused(copy, error.toSendFailure())
            }
        val refusal = submitted.notCreated[SUBMISSION]
        if (refusal != null) refused(copy, refusal.toSendFailure())
        val submissionId = submitted.createdId(SUBMISSION) ?: failSend(SendFailure.ServerProblem)
        return SentMessage(draft.accountId, copy, submissionId, route.drafts, route.identityId)
    }

    /**
     * The submission of the stored [copy] in [folder]. A held or scheduled one carries an envelope
     * with its time. A scheduled copy stays in [folder], only losing `$draft`, since the server
     * sends it much later; any other moves to Sent at once.
     */
    private fun MailAccount.submission(
        route: SendRoute,
        folder: String,
        copy: String,
        draft: Draft,
        time: SendTime,
    ) = SetMethod(
        EmailSubmissionType,
        id,
        create =
            mapOf(
                SUBMISSION to
                    buildJsonObject {
                        put("emailId", copy)
                        put("identityId", route.identityId)
                        when (time) {
                            SendTime.Now -> Unit
                            is SendTime.Held -> put("envelope", draft.heldEnvelope(time.at))
                            is SendTime.Scheduled -> put("envelope", draft.heldEnvelope(time.at))
                        }
                    },
            ),
        extra =
            mapOf(
                "onSuccessUpdateEmail" to
                    buildJsonObject {
                        put(
                            "#$SUBMISSION",
                            if (time is SendTime.Scheduled) route.heldPatch() else route.sentPatch(folder),
                        )
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
        submissionCall(message) { account ->
            val refusal = account.cancelSubmission(message)
            if (refusal != null) failSend(refusal)
            account.restoreCanceledDraft(message)
        }

    /**
     * A held message cannot be sent early, so its submission is canceled and its copy submitted
     * again. When the new submission fails, the copy becomes a draft again, unless a lost answer
     * hid that the server took it. Running it again after a lost answer is safe: a hold already
     * canceled is submitted again, and one the server keeps or sent is left alone.
     */
    override suspend fun sendNow(message: SentMessage): Result<Unit> =
        submissionCall(message) { account ->
            val canceled = unlessUnanswered { account.cancelSubmission(message) == null } == true
            // Unless the cancel was confirmed, a submission still there goes at its time by itself.
            val kept = !canceled && account.isSubmitted(message.emailId) ?: failSend(SendFailure.Unreachable)
            if (kept) return@submissionCall
            val refusal =
                try {
                    account.resubmit(message)
                } catch (error: JmapException) {
                    // When the server cannot be asked, a later run decides, so the copy stays as it is.
                    if (account.isSubmitted(message.emailId) ?: throw error) return@submissionCall
                    account.restoreCanceledDraft(message)
                    throw error
                }
            if (refusal != null) {
                account.restoreCanceledDraft(message)
                failSend(refusal.toSendFailure())
            }
        }

    /** Runs [block] on [message]'s account, and turns what can go wrong into a failed result. */
    private suspend fun submissionCall(
        message: SentMessage,
        block: suspend (MailAccount) -> Unit,
    ): Result<Unit> =
        try {
            block(mailAccount(connections, logins, message.accountId) ?: failSend(SendFailure.ServerProblem))
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

    /**
     * Marks the copy of a canceled [message] as a draft again, back in Drafts if it moved to Sent,
     * and takes back the mark its send gave the original it answered.
     */
    private suspend fun MailAccount.restoreCanceledDraft(message: SentMessage) {
        val emailId = message.emailId
        val patch =
            buildJsonObject {
                put("keywords/$DRAFT_KEYWORD", true)
                if (message.draftsId != null) putJsonObject("mailboxIds") { put(message.draftsId, true) }
            }
        val unmarked =
            listOfNotNull(message.marked).associate {
                it.emailId to
                    membershipPatch("keywords", it.keyword, member = false)
            }
        try {
            // Only the copy decides: an original deleted since then keeps nothing to take back.
            val restored = connection.call(SetMethod(EmailType, id, update = mapOf(emailId to patch) + unmarked))
            if (emailId !in restored.updated || emailId in restored.notUpdated) throw DraftRestoreException()
        } catch (error: JmapException) {
            throw DraftRestoreException(error)
        }
    }

    private companion object {
        const val EMAIL = "draft"
        const val SUBMISSION = "send"
    }
}

/** Destroys the [copy] a refused submission left, and fails the send with [failure]. */
private suspend fun MailAccount.refused(
    copy: String,
    failure: SendFailure,
): Nothing {
    bestEffort { connection.call(SetMethod(EmailType, id, destroy = listOf(copy))) }
    failSend(failure)
}

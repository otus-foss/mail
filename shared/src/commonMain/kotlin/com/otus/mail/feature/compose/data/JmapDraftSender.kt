package com.otus.mail.feature.compose.data

import com.otus.jmap.GetMethod
import com.otus.jmap.GetResult
import com.otus.jmap.JmapCall
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.SetMethod
import com.otus.jmap.jmapCallOr
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.mail.EmailSubmissionType
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.IdentityObject
import com.otus.mail.core.mail.IdentityType
import com.otus.mail.core.mail.Keywords
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.MailFailure
import com.otus.mail.core.mail.SubmissionCapability
import com.otus.mail.core.mail.mailAccount
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.data.EmailChangeQueue
import com.otus.mail.core.messages.domain.AnsweredMessage
import com.otus.mail.core.messages.domain.Draft
import com.otus.mail.core.messages.domain.DraftRestoreException
import com.otus.mail.core.messages.domain.DraftSender
import com.otus.mail.core.messages.domain.HeldSend
import com.otus.mail.core.messages.domain.ScheduledSends
import com.otus.mail.core.messages.domain.SendException
import com.otus.mail.core.messages.domain.SendFailure
import com.otus.mail.core.messages.domain.SendTime
import com.otus.mail.core.messages.domain.SentMessage
import com.otus.mail.core.messages.domain.sendingAs
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Sends a draft with `EmailSubmission/set` (RFC 8621, section 7.5). One request stores the draft
 * as written, and a second submits that copy by its id; on success the server moves it to Sent and
 * clears `$draft`. Without an envelope, the server takes the recipients from To, Cc, and Bcc, and
 * strips Bcc.
 */
class JmapDraftSender internal constructor(
    private val connections: JmapConnections,
    private val copies: DraftCopies,
    private val changes: EmailChangeQueue,
) : DraftSender,
    ScheduledSends {
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
            val account =
                connections.mailAccount(draft.accountId) ?: failSend(SendFailure.Mail(MailFailure.ServerProblem))
            val from = draft.from?.address?.takeIf { account.mayUse(JmapCapability.SUBMISSION) }
            val route = account.route(from ?: failSend(SendFailure.NoIdentity))
            val sent = account.submit(draft, route, time, stored)
            // Undo or a cancel can still stop a held or scheduled send, so it takes back a mark it added.
            val marked = if (time == SendTime.Now) null else answered?.let { account.unmarked(it) }
            account.tidySent(draft.emailId, answered, changes)
            // A save whose answer was lost may have left a copy that no later save will remove.
            route.drafts?.let { copies.unanswered.destroy(account, it, kept = sent.emailId) }
            Result.success(sent.copy(marked = marked))
        } catch (error: SendException) {
            Result.failure(error)
        } catch (error: JmapException) {
            Result.failure(SendException(error.toSendFailure(), error))
        } catch (error: LoginStoreException) {
            Result.failure(SendException(SendFailure.Mail(MailFailure.ServerProblem), error))
        } catch (error: SerializationException) {
            // Content that cannot be encoded fails the send rather than the app.
            Result.failure(SendException(SendFailure.Mail(MailFailure.ServerProblem), error))
        }

    /** The identity that sends as [from], and the folders the sent copy moves between. */
    private suspend fun MailAccount.route(from: String): SendRoute {
        lateinit var identities: JmapCall<GetResult<IdentityObject>>
        val identityGet = GetMethod(IdentityType, id, properties = listOf("id", "email"))
        // Fresh folders, so a Sent re-created on another client still gets the sent copy.
        val (folders, response) = copies.mailboxes.sendFresh(this) { identities = call(identityGet) }
        return SendRoute(
            identityId = response[identities].list.sendingAs(from) { it.email }?.id ?: failSend(SendFailure.NoIdentity),
            drafts = folders.firstOrNull { it.role == MailboxRole.Drafts }?.id,
            sent = folders.firstOrNull { it.role == MailboxRole.Sent }?.id,
            scheduled = folders.scheduledFolder()?.id,
        )
    }

    /**
     * Stores the draft, tells [stored] the copy's id, and then submits that copy, so a submission
     * whose answer is lost can be looked up by the id. A refused submission destroys the copy, so
     * Drafts keeps only the draft's earlier copy. A scheduled copy waits in Scheduled, made when the
     * account has none, so Drafts holds only drafts.
     */
    private suspend fun MailAccount.submit(
        draft: Draft,
        route: SendRoute,
        time: SendTime,
        stored: suspend (emailId: String) -> Unit,
    ): SentMessage {
        val folder =
            route.scheduled.takeIf { time is SendTime.Scheduled }
                ?: createScheduledFolder().takeIf { time is SendTime.Scheduled }
                ?: route.drafts
                ?: route.sent
                ?: failSend(SendFailure.Mail(MailFailure.ServerProblem))
        val email =
            draft
                .toEmail(folder, inlineImages(draft.htmlContent(), copies.images))
                .let { if (time is SendTime.Scheduled) it.sendingAt(time.at) else it }
        val created = connection.call(SetMethod(EmailType, id, create = mapOf(EMAIL to email)))
        val creationFailure = created.notCreated[EMAIL]
        if (creationFailure != null) failSend(creationFailure.toSendFailure())
        val copy = created.createdId(EMAIL) ?: failSend(SendFailure.Mail(MailFailure.ServerProblem))
        stored(copy)
        val submitted =
            try {
                connection.call(submission(route, folder, copy, draft, time))
            } catch (error: JmapException.MethodError) {
                refused(copy, error.toSendFailure())
            }
        val refusal = submitted.notCreated[SUBMISSION]
        if (refusal != null) refused(copy, refusal.toSendFailure())
        val submissionId = submitted.createdId(SUBMISSION) ?: failSend(SendFailure.Mail(MailFailure.ServerProblem))
        return SentMessage(draft.accountId, copy, submissionId, route.drafts, route.identityId)
    }

    /**
     * The submission of the stored [copy] in [folder]. A held or scheduled one carries an envelope
     * with its time. A scheduled copy stays in [folder], only losing `$draft`, since the server
     * sends it much later, and [fileSent] moves it to Sent once it went; any other moves at once.
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
        jmapCallOr(0L) {
            connections
                .mailAccount(accountId)
                ?.takeIf { it.mayUse(JmapCapability.SUBMISSION) }
                ?.delayedSendSeconds() ?: 0L
        }

    override suspend fun held(
        accountId: String,
        emailId: String,
    ): HeldSend? =
        jmapCallOr(null) {
            connections.mailAccount(accountId)?.let { it.heldSend(emailId, copies.mailboxes.sendFresh(it) {}.first) }
        }

    override suspend fun fileSent(accountId: String) {
        jmapCallOr(Unit) {
            connections.mailAccount(accountId)?.let { it.fileScheduled(copies.mailboxes.sendFresh(it) {}.first) }
        }
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
            val canceled = jmapCallOr(null) { account.cancelSubmission(message) == null } == true
            // Unless the cancel was confirmed, a submission still there goes at its time by itself.
            val kept =
                !canceled &&
                    account.isSubmitted(message.emailId) ?: failSend(SendFailure.Mail(MailFailure.Unreachable))
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
            block(connections.mailAccount(message.accountId) ?: failSend(SendFailure.Mail(MailFailure.ServerProblem)))
            Result.success(Unit)
        } catch (error: DraftRestoreException) {
            Result.failure(error)
        } catch (error: SendException) {
            Result.failure(error)
        } catch (error: JmapException) {
            Result.failure(SendException(error.toSendFailure(), error))
        } catch (error: LoginStoreException) {
            Result.failure(SendException(SendFailure.Mail(MailFailure.ServerProblem), error))
        }

    /**
     * Marks the copy of a canceled [message] as a draft again, back in Drafts if it moved to Sent,
     * and then takes back the mark its send gave the original it answered.
     */
    private suspend fun MailAccount.restoreCanceledDraft(message: SentMessage) {
        val emailId = message.emailId
        val patch =
            buildJsonObject {
                put("keywords/${Keywords.DRAFT}", true)
                if (message.draftsId != null) putJsonObject("mailboxIds") { put(message.draftsId, true) }
            }
        try {
            val restored = connection.call(SetMethod(EmailType, id, update = mapOf(emailId to patch)))
            if (emailId !in restored.updated || emailId in restored.notUpdated) throw DraftRestoreException()
        } catch (error: JmapException) {
            throw DraftRestoreException(error)
        }
        message.marked?.let { changes.mark(login.id, it, on = false) }
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
    jmapCallOr(Unit) { connection.call(SetMethod(EmailType, id, destroy = listOf(copy))) }
    failSend(failure)
}

/**
 * How far ahead this account's server holds mail: its `maxDelayedSend`, but only while its
 * `submissionExtensions` offer FUTURERELEASE (RFC 8621, section 1.3.2), whose HOLDUNTIL parameter
 * (RFC 4865) a held or scheduled send puts in its envelope.
 */
private fun MailAccount.delayedSendSeconds(): Long? {
    val submission = capability(JmapCapability.SUBMISSION, SubmissionCapability.serializer()) ?: return null
    val holds = submission.submissionExtensions.keys.any { it.equals(FUTURE_RELEASE, ignoreCase = true) }
    return submission.maxDelayedSend.takeIf { holds }
}

private const val FUTURE_RELEASE = "FUTURERELEASE"

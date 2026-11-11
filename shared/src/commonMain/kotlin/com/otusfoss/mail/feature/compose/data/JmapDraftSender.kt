package com.otusfoss.mail.feature.compose.data

import com.otusfoss.core.jmap.GetMethod
import com.otusfoss.core.jmap.GetResult
import com.otusfoss.core.jmap.JmapCall
import com.otusfoss.core.jmap.JmapCapability
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.JmapException
import com.otusfoss.core.jmap.SetMethod
import com.otusfoss.core.jmap.isTemporary
import com.otusfoss.core.jmap.jmapCallOr
import com.otusfoss.core.jmap.login.LoginStoreException
import com.otusfoss.mail.core.mail.EmailType
import com.otusfoss.mail.core.mail.IdentityObject
import com.otusfoss.mail.core.mail.IdentityType
import com.otusfoss.mail.core.mail.MailAccount
import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.mail.SubmissionCapability
import com.otusfoss.mail.core.mail.mailAccount
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.data.EmailChangeQueue
import com.otusfoss.mail.core.messages.domain.AnsweredMessage
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.DraftRestoreException
import com.otusfoss.mail.core.messages.domain.DraftSender
import com.otusfoss.mail.core.messages.domain.HeldSend
import com.otusfoss.mail.core.messages.domain.ScheduledSends
import com.otusfoss.mail.core.messages.domain.SendCancel
import com.otusfoss.mail.core.messages.domain.SendException
import com.otusfoss.mail.core.messages.domain.SendFailure
import com.otusfoss.mail.core.messages.domain.SendTime
import com.otusfoss.mail.core.messages.domain.SentMessage
import com.otusfoss.mail.core.messages.domain.sendingAs
import kotlinx.serialization.SerializationException
import kotlin.time.Clock

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
    private val filing = ScheduledFiling()

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
            adoptable = folders.adoptableScheduled(),
        )
    }

    /**
     * Stores the draft, tells [stored] the copy's id, and then submits that copy, so a submission
     * whose answer is lost can be looked up by the id. A refused submission destroys the copy, so
     * Drafts keeps only the draft's earlier copy; a server failing for now keeps it. A scheduled copy waits in Scheduled, made when the
     * account has none, so Drafts holds only drafts.
     */
    private suspend fun MailAccount.submit(
        draft: Draft,
        route: SendRoute,
        time: SendTime,
        stored: suspend (emailId: String) -> Unit,
    ): SentMessage {
        val folder =
            (if (time is SendTime.Scheduled) scheduledFolderFor(route) else null)
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
                // The copy may still go, or have gone in part (RFC 8620, section 3.6.2), so it stays
                // for the outbox to look up by its id before anything is destroyed.
                if (error.isTemporary) throw error
                refused(copy, error.toSendFailure())
            }
        val refusal = submitted.notCreated[SUBMISSION]
        if (refusal != null) refused(copy, refusal.toSendFailure())
        val submissionId = submitted.createdId(SUBMISSION) ?: failSend(SendFailure.Mail(MailFailure.ServerProblem))
        return SentMessage(draft.accountId, copy, submissionId, route.drafts, route.identityId)
    }

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
            connections.mailAccount(accountId)?.let {
                filing.file(it, copies.mailboxes.sendFresh(it) {}.first, Clock.System.now())
            }
        }
    }

    /**
     * A cancel the server does not confirm, whether refused or with its answer lost, is checked
     * against what the server now knows of the copy, so a send canceled in a lost request is not
     * taken for one that still waits.
     */
    override suspend fun cancel(message: SentMessage): SendCancel =
        try {
            val account = connections.mailAccount(message.accountId)
            val refusal =
                account?.let {
                    try {
                        it.cancelSubmission(message)
                    } catch (error: JmapException) {
                        error.toSendFailure()
                    }
                }
            when {
                account == null -> {
                    SendCancel.Waiting
                }

                refusal == null -> {
                    account.restoredAfterCancel(message, changes)
                }

                else -> {
                    jmapCallOr(SendCancel.Waiting) { account.copyState(message.emailId).afterRefusal(refusal) }
                        ?: account.restoredAfterCancel(message, changes)
                }
            }
        } catch (expected: LoginStoreException) {
            SendCancel.Waiting
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
                    account.restoreCanceledDraft(message, changes)
                    throw error
                }
            if (refusal != null) {
                account.restoreCanceledDraft(message, changes)
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

    private companion object {
        const val EMAIL = "draft"
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

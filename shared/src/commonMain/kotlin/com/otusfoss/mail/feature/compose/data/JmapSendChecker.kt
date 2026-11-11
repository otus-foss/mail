package com.otusfoss.mail.feature.compose.data

import com.otusfoss.core.jmap.GetMethod
import com.otusfoss.core.jmap.GetResult
import com.otusfoss.core.jmap.JmapCall
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.JmapException
import com.otusfoss.core.jmap.JmapIds
import com.otusfoss.core.jmap.QueryMethod
import com.otusfoss.core.jmap.QueryResult
import com.otusfoss.core.jmap.SetMethod
import com.otusfoss.core.jmap.filterCondition
import com.otusfoss.core.jmap.isTemporary
import com.otusfoss.core.jmap.login.LoginStoreException
import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.mail.EmailType
import com.otusfoss.mail.core.mail.Keywords
import com.otusfoss.mail.core.mail.MailAccount
import com.otusfoss.mail.core.mail.mailAccount
import com.otusfoss.mail.core.messages.data.EmailChangeQueue
import com.otusfoss.mail.core.messages.data.receivedAt
import com.otusfoss.mail.core.messages.domain.AnsweredMessage
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.feature.compose.domain.SendCheck
import com.otusfoss.mail.feature.compose.domain.SendChecker
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Finds what became of a send whose answer was lost. A try that stored its copy before submitting
 * it is looked up by the copy's id: a submission of it that nobody canceled (RFC 8621, section 7.3)
 * means it went, and so does a copy without `$draft` whose submission the server no longer keeps.
 * Otherwise the draft is found by its Message-ID with the `header` filter of `Email/query`
 * (section 4.4.1): a copy without `$draft` went, and one that kept it was refused. A copy that did
 * not go is destroyed so the next send leaves no second copy. A copy the server holds, whose
 * submission is canceled after Undo, is destroyed too. A copy that went is tidied up as a send
 * would have: the saved draft is deleted and the answered message marked.
 */
class JmapSendChecker(
    private val connections: JmapConnections,
    private val changes: EmailChangeQueue,
) : SendChecker {
    override suspend fun check(
        draft: Draft,
        answered: AnsweredMessage?,
        cancelHeld: Boolean,
    ): SendCheck = check(draft, answered, cancelHeld, storedCopy = null)

    override suspend fun check(
        draft: Draft,
        answered: AnsweredMessage?,
        cancelHeld: Boolean,
        storedCopy: String?,
    ): SendCheck =
        try {
            val account = connections.mailAccount(draft.accountId)
            val draftId = draft.emailId
            when {
                account == null -> SendCheck.Unconfirmable
                storedCopy != null -> account.findCopy(storedCopy, cancelHeld, draftId, answered, changes)
                draft.messageId != null -> account.findSent(draft.messageId, cancelHeld, draftId, answered, changes)
                else -> SendCheck.Unconfirmable
            }
        } catch (error: JmapException.MethodError) {
            // A server down for now can be asked later; one that cannot look the message up, as with
            // `unsupportedFilter`, never can.
            if (error.isTemporary) SendCheck.Unreachable else SendCheck.Unconfirmable
        } catch (expected: JmapException) {
            SendCheck.Unreachable
        } catch (expected: LoginStoreException) {
            SendCheck.Unreachable
        }
}

/** What became of the [copy] an earlier try stored, by its id rather than a search. */
private suspend fun MailAccount.findCopy(
    copy: String,
    cancelHeld: Boolean,
    draftId: String?,
    answered: AnsweredMessage?,
    changes: EmailChangeQueue,
): SendCheck {
    val state = copyState(copy)
    // `$draft` goes only when a submission was made, so a copy without it whose submission the
    // server has since dropped went.
    val sentAndForgotten = state.statuses.isEmpty() && state.isDraft == false
    val went = if (state.live.isEmpty()) sentAndForgotten else !(cancelHeld && cancelHeld(listOf(copy)))
    // Neither a copy nor a submission: the user may have deleted what went, so only they can tell.
    val nothingLeft = state.isDraft == null && state.statuses.isEmpty()
    if (!went && nothingLeft && !cancelHeld) return SendCheck.Unconfirmable
    if (!went && state.isDraft != null) connection.call(SetMethod(EmailType, id, destroy = listOf(copy)))
    if (went) tidySent(draftId?.takeIf { it != copy }, answered, changes)
    return if (went) SendCheck.Sent else SendCheck.NotSent
}

private suspend fun MailAccount.findSent(
    messageId: String,
    cancelHeld: Boolean,
    draftId: String?,
    answered: AnsweredMessage?,
    changes: EmailChangeQueue,
): SendCheck {
    lateinit var query: JmapCall<QueryResult>
    lateinit var found: JmapCall<GetResult<EmailObject>>
    val response =
        connection.send {
            query = call(QueryMethod(EmailType, id, messageIdFilter(messageId)))
            found = call(GetMethod(EmailType, id, JmapIds.Result(query.resultOf("/ids")), FOUND_PROPERTIES))
        }
    // The query's own error says why; the lookup that depends on it would only fail its reference.
    response[query]
    val (stored, sent) = response[found].list.partition { Keywords.DRAFT in it.keywords }
    val went = sent.isNotEmpty() && !(cancelHeld && cancelHeld(sent.map { it.id }))
    if (!went && (stored + sent).isNotEmpty()) {
        connection.call(SetMethod(EmailType, id, destroy = (stored + sent).map { it.id }))
    }
    // As after a send: the saved draft goes, and the answered message gets its mark.
    if (went) tidySent(draftId?.takeIf { copy -> sent.none { it.id == copy } }, answered, changes)
    return if (went) SendCheck.Sent else SendCheck.NotSent
}

/** The mail whose Message-ID header holds [messageId]. */
internal fun messageIdFilter(messageId: String) =
    filterCondition("header", JsonArray(listOf(JsonPrimitive("Message-ID"), JsonPrimitive(messageId))))

private val FOUND_PROPERTIES = listOf("id", "keywords", "receivedAt")

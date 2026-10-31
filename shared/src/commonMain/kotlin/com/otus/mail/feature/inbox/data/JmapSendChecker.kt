package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.GetResult
import com.otus.jmap.JmapCall
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.JmapIds
import com.otus.jmap.QueryMethod
import com.otus.jmap.QueryResult
import com.otus.jmap.SetMethod
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.condition
import com.otus.mail.feature.inbox.domain.AnsweredMessage
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.SendCheck
import com.otus.mail.feature.inbox.domain.SendChecker
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Finds a sent draft by its Message-ID with the `header` filter of `Email/query` (RFC 8621,
 * section 4.4.1). A send stores a copy and submits it in one request, and a submission the server
 * takes clears the copy's `$draft`. So a copy without `$draft` went; one that kept it was refused,
 * and is destroyed so the next send leaves no second copy. A copy the server holds, whose
 * submission is canceled after Undo, is destroyed too. A copy that went is tidied up as a send
 * would have: the saved draft is deleted and the answered message marked.
 */
class JmapSendChecker(
    private val connections: JmapConnections,
    private val logins: LoginStore,
) : SendChecker {
    override suspend fun check(
        draft: Draft,
        answered: AnsweredMessage?,
        cancelHeld: Boolean,
    ): SendCheck {
        val messageId = draft.messageId ?: return SendCheck.Unconfirmable
        return try {
            mailAccount(connections, logins, draft.accountId)?.findSent(messageId, cancelHeld, draft.emailId, answered)
                ?: SendCheck.Unconfirmable
        } catch (error: JmapException.MethodError) {
            // A server down for now can be asked later; one that cannot look the message up, as with
            // `unsupportedFilter`, never can.
            if (error.type in TEMPORARY_METHOD_ERRORS) SendCheck.Unreachable else SendCheck.Unconfirmable
        } catch (expected: JmapException) {
            SendCheck.Unreachable
        } catch (expected: LoginStoreException) {
            SendCheck.Unreachable
        }
    }
}

private suspend fun MailAccount.findSent(
    messageId: String,
    cancelHeld: Boolean,
    draftId: String?,
    answered: AnsweredMessage?,
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
    val (stored, sent) = response[found].list.partition { DRAFT_KEYWORD in it.keywords }
    val went = sent.isNotEmpty() && !(cancelHeld && cancelHeld(sent.map { it.id }))
    if (!went && (stored + sent).isNotEmpty()) {
        connection.call(SetMethod(EmailType, id, destroy = (stored + sent).map { it.id }))
    }
    // As after a send: the saved draft goes, and the answered message gets its mark.
    if (went) tidySent(draftId?.takeIf { copy -> sent.none { it.id == copy } }, answered)
    return if (went) SendCheck.Sent else SendCheck.NotSent
}

/** The mail whose Message-ID header holds [messageId]. */
private fun messageIdFilter(messageId: String) =
    condition("header", JsonArray(listOf(JsonPrimitive("Message-ID"), JsonPrimitive(messageId))))

private val FOUND_PROPERTIES = listOf("id", "keywords", "receivedAt")

package com.otus.mail.feature.compose.data

import com.otus.jmap.GetMethod
import com.otus.jmap.GetResult
import com.otus.jmap.JmapCall
import com.otus.jmap.JmapIds
import com.otus.jmap.QueryMethod
import com.otus.jmap.QueryResult
import com.otus.jmap.filterCondition
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.EmailSubmissionType
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.Keywords
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.messages.domain.SendCancel
import com.otus.mail.core.messages.domain.SendFailure
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * What the server knows of a stored copy: the `undoStatus` of every submission of it, null where
 * the server left it out, and whether the copy is a draft, null when the copy is gone.
 */
internal data class CopyState(
    val statuses: List<String?>,
    val isDraft: Boolean?,
) {
    /** The submissions nobody canceled. */
    val live: List<String?> get() = statuses.filter { it != CANCELED }

    /**
     * What a cancel the server did not confirm ended in, after [refusal]; null when the send is
     * canceled and its copy still has to become a draft. Another live submission of the copy, as
     * from another client, keeps it going, so only none at all lets the copy free.
     */
    fun afterRefusal(refusal: SendFailure?): SendCancel? =
        when {
            FINAL in statuses -> SendCancel.Final
            PENDING in statuses -> if (refusal == SendFailure.CannotUnsend) SendCancel.Final else SendCancel.Waiting
            null in statuses -> SendCancel.Unknown
            isDraft == true -> SendCancel.Stopped
            statuses.isNotEmpty() -> null
            else -> SendCancel.Unknown
        }
}

/** [CopyState] of [emailId], in one request; it throws when the server cannot be asked. */
internal suspend fun MailAccount.copyState(emailId: String): CopyState {
    lateinit var query: JmapCall<QueryResult>
    lateinit var submissions: JmapCall<GetResult<JsonObject>>
    lateinit var found: JmapCall<GetResult<EmailObject>>
    val ofCopy = filterCondition("emailIds", JsonArray(listOf(JsonPrimitive(emailId))))
    val response =
        connection.send {
            query = call(QueryMethod(EmailSubmissionType, id, ofCopy))
            val ids = JmapIds.Result(query.resultOf("/ids"))
            submissions = call(GetMethod(EmailSubmissionType, id, ids, listOf("id", "undoStatus")))
            found = call(GetMethod(EmailType, id, JmapIds.Of(listOf(emailId)), KEYWORD_PROPERTIES))
        }
    // The query's own error says why; the lookup that depends on it would only fail its reference.
    response[query]
    return CopyState(
        response[submissions].list.map { it["undoStatus"]?.jsonPrimitive?.contentOrNull },
        response[found].list.singleOrNull()?.let { Keywords.DRAFT in it.keywords },
    )
}

internal const val CANCELED = "canceled"

internal const val FINAL = "final"

internal const val PENDING = "pending"

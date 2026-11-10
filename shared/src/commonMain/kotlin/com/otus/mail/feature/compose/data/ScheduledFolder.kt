package com.otus.mail.feature.compose.data

import com.otus.jmap.GetMethod
import com.otus.jmap.GetResult
import com.otus.jmap.JmapCall
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapIds
import com.otus.jmap.QueryMethod
import com.otus.jmap.QueryResult
import com.otus.jmap.SetMethod
import com.otus.jmap.allOf
import com.otus.jmap.filterCondition
import com.otus.jmap.jmapCallOr
import com.otus.jmap.jmapPatch
import com.otus.mail.core.mail.EmailSubmissionType
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.Keywords
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.MailboxType
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.HeldSend
import com.otus.mail.core.messages.domain.SentMessage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Instant

/**
 * The folder scheduled mail waits in, the one with the `scheduled` role (IANA IMAP Mailbox Name
 * Attributes), which the sidebar and the Scheduled page show.
 */
internal fun List<MailFolder>.scheduledFolder(): MailFolder? = firstOrNull { it.role == MailboxRole.Scheduled }

/** Creates the Scheduled folder with its role; null when the server refused it or could not be reached. */
internal suspend fun MailAccount.createScheduledFolder(): String? =
    jmapCallOr(null) {
        val folder =
            buildJsonObject {
                put("name", SCHEDULED_NAME)
                put("role", MailboxRole.Scheduled.wire)
            }
        connection.call(SetMethod(MailboxType, id, create = mapOf(FOLDER to folder))).createdId(FOLDER)
    }

/** [fileScheduled] with the account's Drafts, Scheduled, and Sent among [folders]; nothing without Sent. */
internal suspend fun MailAccount.fileScheduled(folders: List<MailFolder>) {
    val sent = folders.firstOrNull { it.role == MailboxRole.Sent }?.id ?: return
    fileScheduled(folders.firstOrNull { it.role == MailboxRole.Drafts }?.id, folders.scheduledFolder()?.id, sent)
}

/** The send still waiting for [emailId], whose cancel makes it a draft in the Drafts among [folders]. */
internal suspend fun MailAccount.heldSend(
    emailId: String,
    folders: List<MailFolder>,
): HeldSend? {
    val pending = pendingSubmission(emailId) ?: return null
    val drafts = folders.firstOrNull { it.role == MailboxRole.Drafts }?.id
    return HeldSend(SentMessage(login.id, emailId, pending.id, drafts, pending.identityId), pending.at)
}

/**
 * Files scheduled mail by its submission (RFC 8621, section 7): a copy whose send finished
 * (`undoStatus` `final`) moves from [scheduled] to [sent], since some servers send held mail but
 * leave its copy where it was. A copy still waiting that an older Mail left in [drafts] without
 * `$draft` moves to [scheduled], once the account has it: only a scheduled send makes the folder.
 * A copy in [scheduled] whose every send was canceled, as when making it a draft again failed,
 * goes back to [drafts] as a draft, since nothing would send or file it.
 */
internal suspend fun MailAccount.fileScheduled(
    drafts: String?,
    scheduled: String?,
    sent: String,
) {
    if (!mayUse(JmapCapability.SUBMISSION)) return
    val inScheduled = scheduled?.let { emailsIn(it, notKeyword = Keywords.DRAFT) }.orEmpty()
    val inDrafts = drafts?.let { emailsIn(it, notKeyword = Keywords.DRAFT) }.orEmpty()
    val status = submissionStatus((inScheduled + inDrafts).toList())
    val moves =
        buildMap {
            status.filterValues { FINAL in it }.keys.forEach { email ->
                val from = if (email in inDrafts) drafts else scheduled
                if (from != null) put(email, move(from, sent))
            }
            if (drafts != null && scheduled != null) {
                inDrafts.filter { status[it]?.contains(PENDING) == true }.forEach { put(it, move(drafts, scheduled)) }
            }
            if (scheduled != null) {
                val draftAgain =
                    jmapPatch {
                        set(JsonPrimitive(true), "keywords", Keywords.DRAFT)
                        if (drafts != null) include(move(scheduled, drafts))
                    }
                inScheduled.filter { status[it] == ONLY_CANCELED }.forEach { put(it, draftAgain) }
            }
        }
    if (moves.isNotEmpty()) connection.call(SetMethod(EmailType, id, update = moves))
}

/** The emails in [folder], without those that have [notKeyword]. */
private suspend fun MailAccount.emailsIn(
    folder: String,
    notKeyword: String? = null,
): Set<String> {
    val inFolder = filterCondition("inMailbox", JsonPrimitive(folder))
    val filter = notKeyword?.let { allOf(inFolder, filterCondition("notKeyword", JsonPrimitive(it))) } ?: inFolder
    return connection.call(QueryMethod(EmailType, id, filter)).ids.toSet()
}

/** The `undoStatus` of every submission of each of [emailIds] that has one. */
private suspend fun MailAccount.submissionStatus(emailIds: List<String>): Map<String, Set<String>> {
    if (emailIds.isEmpty()) return emptyMap()
    lateinit var all: JmapCall<QueryResult>
    lateinit var submissions: JmapCall<GetResult<JsonObject>>
    val response =
        connection.send {
            all =
                call(
                    QueryMethod(
                        EmailSubmissionType,
                        id,
                        filterCondition("emailIds", JsonArray(emailIds.map(::JsonPrimitive))),
                    ),
                )
            submissions =
                call(
                    GetMethod(
                        EmailSubmissionType,
                        id,
                        JmapIds.Result(all.resultOf("/ids")),
                        listOf("emailId", "undoStatus"),
                    ),
                )
        }
    return response[submissions]
        .list
        .mapNotNull { submission ->
            val email = submission.text("emailId")
            val undo = submission.text("undoStatus")
            if (email != null && undo != null && email in emailIds) email to undo else null
        }.groupBy({ it.first }, { it.second })
        .mapValues { it.value.toSet() }
}

/** The submission of [emailId] the server still holds, with its identity and when it goes; null when none waits. */
internal suspend fun MailAccount.pendingSubmission(emailId: String): PendingSubmission? {
    lateinit var waiting: JmapCall<QueryResult>
    lateinit var submissions: JmapCall<GetResult<JsonObject>>
    val response =
        connection.send {
            waiting =
                call(
                    QueryMethod(
                        EmailSubmissionType,
                        id,
                        allOf(
                            filterCondition("emailIds", JsonArray(listOf(JsonPrimitive(emailId)))),
                            filterCondition("undoStatus", JsonPrimitive(PENDING)),
                        ),
                        limit = 1,
                    ),
                )
            submissions =
                call(
                    GetMethod(
                        EmailSubmissionType,
                        id,
                        JmapIds.Result(waiting.resultOf("/ids")),
                        listOf("id", "identityId", "sendAt"),
                    ),
                )
        }
    val submission = response[submissions].list.firstOrNull()
    val id = submission?.text("id")
    val at = submission?.text("sendAt")?.let { runCatching { Instant.parse(it) }.getOrNull() }
    return if (id != null && at != null) PendingSubmission(id, submission.text("identityId").orEmpty(), at) else null
}

private fun JsonObject.text(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

/** A held submission [id], sent as [identityId] at [at]. */
internal data class PendingSubmission(
    val id: String,
    val identityId: String,
    val at: Instant,
)

private fun move(
    from: String,
    to: String,
): JsonObject =
    jmapPatch {
        remove("mailboxIds", from)
        set(JsonPrimitive(true), "mailboxIds", to)
    }

/** The submissions of a copy whose every send was canceled. */
private val ONLY_CANCELED = setOf(CANCELED)

private const val SCHEDULED_NAME = "Scheduled"

private const val FOLDER = "scheduled"

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
import com.otus.jmap.noneOf
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.EmailSubmissionType
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.Keywords
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.MailboxType
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.data.boxes
import com.otus.mail.core.messages.domain.HeldSend
import com.otus.mail.core.messages.domain.SentMessage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.minutes
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
internal suspend fun MailAccount.fileScheduled(
    folders: List<MailFolder>,
    now: Instant,
) {
    val sent = folders.firstOrNull { it.role == MailboxRole.Sent }?.id ?: return
    fileScheduled(folders.firstOrNull { it.role == MailboxRole.Drafts }?.id, folders.scheduledFolder()?.id, sent, now)
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
 * Files scheduled mail by its submission (RFC 8621, section 7), whichever client scheduled it. A
 * copy whose send still waits for a time later than Undo holds mail, in [drafts] or [sent], moves
 * to [scheduled]: other clients often file it in Sent at once, and an older Mail left it in Drafts.
 * The Scheduled folder is made for such a copy when the account has none. A copy whose send
 * finished (`undoStatus` `final`) moves from [scheduled], or from [drafts] where an older Mail left
 * it without `$draft`, to [sent], since some servers send held mail but leave its copy where it was.
 */
internal suspend fun MailAccount.fileScheduled(
    drafts: String?,
    scheduled: String?,
    sent: String,
    now: Instant,
) {
    if (!mayUse(JmapCapability.SUBMISSION)) return
    val inScheduled = scheduled?.let { emailsIn(it) }.orEmpty()
    val inDrafts = drafts?.let { emailsIn(it, notKeyword = Keywords.DRAFT) }.orEmpty()
    val finished = submissionStatus((inScheduled + inDrafts).toList()).filterValues { it == FINAL }.keys
    val misfiled =
        scheduledCopies(now)
            .filterValues { scheduled == null || scheduled !in it }
            .mapValues { (_, boxes) -> boxes.filter { it == drafts || it == sent } }
            .filterValues { it.isNotEmpty() }
    val target = scheduled ?: misfiled.takeIf { it.isNotEmpty() }?.let { createScheduledFolder() }
    val moves =
        buildMap {
            finished.forEach { email ->
                val from = if (email in inDrafts) drafts else scheduled
                if (from != null) put(email, move(listOf(from), sent))
            }
            if (target != null) misfiled.forEach { (email, from) -> put(email, move(from, target)) }
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

private fun move(
    from: List<String>,
    to: String,
): JsonObject =
    jmapPatch {
        from.forEach { remove("mailboxIds", it) }
        set(JsonPrimitive(true), "mailboxIds", to)
    }

private const val FINAL = "final"

private const val SCHEDULED_NAME = "Scheduled"

private const val FOLDER = "scheduled"

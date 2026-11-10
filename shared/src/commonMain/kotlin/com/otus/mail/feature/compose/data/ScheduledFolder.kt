package com.otus.mail.feature.compose.data

import com.otus.jmap.JmapCapability
import com.otus.jmap.QueryMethod
import com.otus.jmap.SetMethod
import com.otus.jmap.allOf
import com.otus.jmap.filterCondition
import com.otus.jmap.jmapCallOr
import com.otus.jmap.jmapPatch
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.Keywords
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.MailboxType
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.HeldSend
import com.otus.mail.core.messages.domain.SentMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

/**
 * The folder scheduled mail waits in, the one with the `scheduled` role (IANA IMAP Mailbox Name
 * Attributes), which the sidebar and the Scheduled page show.
 */
internal fun List<MailFolder>.scheduledFolder(): MailFolder? = firstOrNull { it.role == MailboxRole.Scheduled }

/**
 * An empty top-level folder of the user's own named Scheduled, without a role, which Mail gives
 * the role rather than make a second folder of that name, which servers refuse. A folder that
 * holds mail stays the user's, so filing never moves mail Mail did not schedule.
 */
internal fun List<MailFolder>.adoptableScheduled(): String? =
    firstOrNull {
        it.role == null &&
            it.otherRole == null &&
            it.parentId == null &&
            it.sharedIn == null &&
            it.total == 0L &&
            it.name.equals(SCHEDULED_NAME, ignoreCase = true)
    }?.id

/**
 * Gives [adoptable] the `scheduled` role, or makes a Scheduled folder when there is none to
 * adopt; its id, or null when the server refused. It throws when the server cannot be reached.
 */
internal suspend fun MailAccount.makeScheduledFolder(adoptable: String?): String? {
    val role = buildJsonObject { put("role", MailboxRole.Scheduled.wire) }
    if (adoptable != null) {
        val adopted = connection.call(SetMethod(MailboxType, id, update = mapOf(adoptable to role)))
        return adoptable.takeIf { it in adopted.updated }
    }
    val folder = buildJsonObject { put("name", SCHEDULED_NAME) } + role
    return connection.call(SetMethod(MailboxType, id, create = mapOf(FOLDER to JsonObject(folder)))).createdId(FOLDER)
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
 * Files scheduled mail by its submissions (RFC 8621, section 7), whichever client scheduled it.
 *
 * A copy whose send still waits for a time later than Undo holds mail, in [drafts] or [sent],
 * moves to [scheduled]: other clients often file it in Sent at once, and an older Mail left it in
 * Drafts. [makeFolder] gives the account a Scheduled folder for such a copy when it has none.
 *
 * A copy in [scheduled] without `$draft` leaves once its send is over: to [sent] when it went or
 * its submission is gone, and back to [drafts] as a draft when someone canceled it. A copy with
 * `$draft` may be one Mail stored but has not submitted yet, so it stays. Keywords are read before
 * submissions, so a send that completes in between is not taken for one that is gone. A copy an
 * older Mail left in [drafts] without `$draft` goes to [sent] once sent, or becomes a draft again
 * once canceled.
 */
internal suspend fun MailAccount.fileScheduled(
    drafts: String?,
    scheduled: String?,
    sent: String,
    now: Instant,
    makeFolder: suspend () -> String?,
) {
    if (!mayUse(JmapCapability.SUBMISSION)) return
    val folders = SweptFolders(drafts, scheduled, sent)
    val inScheduled = scheduled?.let { unsentIn(it) }.orEmpty()
    val inDrafts = drafts?.let { unsentIn(it) }.orEmpty()
    val states = sendStates((inScheduled + inDrafts).toList())
    val misfiled = folders.misfiled(scheduledCopies(now))
    val target = scheduled ?: misfiled.takeIf { it.isNotEmpty() }?.let { makeFolder() }
    val filed = target?.let { to -> misfiled.mapValues { (_, from) -> move(from, to) } }.orEmpty()
    val moves = folders.ended(states, inScheduled, inDrafts) + filed
    if (moves.isNotEmpty()) connection.call(SetMethod(EmailType, id, update = moves))
}

/** [route]'s Scheduled folder, made or adopted when it has none; null when that failed. */
internal suspend fun MailAccount.scheduledFolderFor(route: SendRoute): String? =
    route.scheduled ?: jmapCallOr(null) { makeScheduledFolder(route.adoptable) }

/** The folders [fileScheduled] moves copies between. */
private class SweptFolders(
    val drafts: String?,
    val scheduled: String?,
    val sent: String,
) {
    /** The folders among Drafts and Sent that each of [copies] should leave for Scheduled. */
    fun misfiled(copies: Map<String, Set<String>>): Map<String, List<String>> =
        copies
            .filterValues { scheduled == null || scheduled !in it }
            .mapValues { (_, boxes) -> boxes.filter { it == drafts || it == sent } }
            .filterValues { it.isNotEmpty() }

    /**
     * The changes to copies [inScheduled] and [inDrafts] whose send is over, by their [states];
     * none in Scheduled when [states] is null, since the server's answer may lack submissions.
     */
    fun ended(
        states: Map<String, SendState>?,
        inScheduled: Set<String>,
        inDrafts: Set<String>,
    ): Map<String, JsonObject> =
        buildMap {
            if (scheduled != null && states != null) {
                inScheduled.forEach { email ->
                    leaving(states[email] ?: SendState.Gone, scheduled)?.let { put(email, it) }
                }
            }
            if (drafts != null) {
                inDrafts.forEach { email -> leavingDrafts(states?.get(email), drafts)?.let { put(email, it) } }
            }
        }

    /** Where a copy in [scheduled] goes once its send is over; null while it waits. */
    private fun leaving(
        state: SendState,
        scheduled: String,
    ): JsonObject? =
        when (state) {
            SendState.Sent, SendState.Gone -> move(listOf(scheduled), sent)
            SendState.Canceled -> drafts?.let { move(listOf(scheduled), it, draft = true) }
            SendState.Pending -> null
        }

    /** What happens to a copy an older Mail left in [drafts] without `$draft`, by its [state]. */
    private fun leavingDrafts(
        state: SendState?,
        drafts: String,
    ): JsonObject? =
        when (state) {
            SendState.Sent -> move(listOf(drafts), sent)
            SendState.Canceled -> jmapPatch { set(JsonPrimitive(true), "keywords", Keywords.DRAFT) }
            else -> null
        }
}

/** The emails in [folder] without `$draft`. */
private suspend fun MailAccount.unsentIn(folder: String): Set<String> {
    val filter =
        allOf(
            filterCondition("inMailbox", JsonPrimitive(folder)),
            filterCondition("notKeyword", JsonPrimitive(Keywords.DRAFT)),
        )
    return connection.call(QueryMethod(EmailType, id, filter)).ids.toSet()
}

/** Moves an email from [from] to [to], and makes it a draft again when [draft] is true. */
private fun move(
    from: List<String>,
    to: String,
    draft: Boolean = false,
): JsonObject =
    jmapPatch {
        from.forEach { remove("mailboxIds", it) }
        set(JsonPrimitive(true), "mailboxIds", to)
        if (draft) set(JsonPrimitive(true), "keywords", Keywords.DRAFT)
    }

private const val SCHEDULED_NAME = "Scheduled"

private const val FOLDER = "scheduled"

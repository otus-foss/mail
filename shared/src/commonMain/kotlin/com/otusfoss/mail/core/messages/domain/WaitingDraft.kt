package com.otusfoss.mail.core.messages.domain

import com.otusfoss.core.jmap.JmapJson
import com.otusfoss.mail.core.storage.StoreSpec
import com.otusfoss.mail.core.storage.TextCodec
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A draft the server could not take, kept on the device under [id] since [savedMillis] until the
 * server can be reached. Its [draft] names in `emailId` the server's copy it replaces, if any. A
 * [discarded] one is no draft any more: it only waits to destroy that copy, which lists hide.
 */
@Serializable
data class WaitingDraft(
    val id: String,
    val draft: Draft,
    val savedMillis: Long,
    val discarded: Boolean = false,
) {
    val loginId: String get() = draft.accountId
}

/** Where drafts wait for the server, by their device id. It must survive the process. */
object WaitingDraftStore : StoreSpec<String, WaitingDraft>(
    "waiting_drafts",
    TextCodec.Text,
    TextCodec.json(WaitingDraft.serializer(), JmapJson),
    commit = true,
)

/** The drafts that wait on the device, for the lists that show them and the changes that drop them. */
interface WaitingDraftList {
    /** [loginId]'s waiting drafts, the one saved last first, with the discarded ones. */
    suspend fun waiting(loginId: String): List<WaitingDraft>

    /** Drops the waiting draft [id], as when the user deletes it; false when none waits under it. */
    suspend fun drop(id: String): Boolean
}

/** Whether [emailId] names a draft kept on the device instead of an Email on the server. */
fun isDeviceDraftId(emailId: String): Boolean = emailId.startsWith(DEVICE_DRAFT_PREFIX)

@OptIn(ExperimentalUuidApi::class)
internal fun newDeviceDraftId(): String = "$DEVICE_DRAFT_PREFIX${Uuid.random()}"

/** No JMAP id holds a colon (RFC 8620, section 1.2), so a server's id never starts this way. */
private const val DEVICE_DRAFT_PREFIX = "device-draft:"

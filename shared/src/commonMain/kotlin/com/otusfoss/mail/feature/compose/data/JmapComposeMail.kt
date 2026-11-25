package com.otusfoss.mail.feature.compose.data

import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.mail.core.cache.MailCache
import com.otusfoss.mail.core.mail.MailAccount
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.core.messages.data.EmailChangeQueue
import com.otusfoss.mail.core.messages.domain.DeviceDrafts
import com.otusfoss.mail.core.messages.domain.WaitingDraft
import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.feature.compose.domain.SavedDrafts
import kotlin.time.Instant

/**
 * Saving and sending drafts of every login. [drafts] and [sender] share what one leaves for the
 * other, so build them only together, here: copies whose save lost its answer, which the next
 * save or send destroys, and signature images already uploaded. Both find the Drafts folder in
 * [mailboxes]. The mark a send gives the message it answers goes through [changes], like every
 * other mark. Each saved copy gets its Message-ID from [newMessageId]. A draft the server cannot
 * be reached for waits in [waiting] on the device, and [wake] asks the platform to store it once
 * the network is back; [cache] keeps each login's identities, so Write knows them offline.
 */
class JmapComposeMail(
    connections: JmapConnections,
    mailboxes: Mailboxes,
    changes: EmailChangeQueue,
    cache: MailCache,
    waiting: DeviceStore<String, WaitingDraft>? = null,
    wake: (Instant) -> Unit = {},
    newMessageId: () -> String = ::draftMessageId,
) {
    private val copies = DraftCopies(mailboxes)
    val drafts: DeviceDrafts =
        DeviceDrafts(JmapDraftRepository(connections, copies, cache, newMessageId), waiting, wake)
    val sender: JmapDraftSender = JmapDraftSender(connections, copies, changes)
    val saved: SavedDrafts = DeviceSavedDrafts(drafts, JmapSavedDrafts(connections))
}

/** What saving and sending share: the folders, lost copies, and uploaded signature images. */
internal class DraftCopies(
    val mailboxes: Mailboxes,
) {
    val unanswered = UnansweredDrafts()
    val images = InlineImageUploads()

    /** The id of [account]'s Drafts folder; null when it has none. */
    suspend fun draftsFolder(account: MailAccount): String? =
        mailboxes
            .of(account)
            .firstOrNull { it.role == MailboxRole.Drafts }
            ?.id
}

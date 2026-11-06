package com.otus.mail.feature.compose.data

import com.otus.jmap.JmapConnections
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.mailbox.Mailboxes
import com.otus.mail.core.messages.data.EmailChangeQueue
import com.otus.mail.core.messages.domain.DraftRepository

/**
 * Saving and sending drafts of every login. [drafts] and [sender] share what one leaves for the
 * other, so build them only together, here: copies whose save lost its answer, which the next
 * save or send destroys, and signature images already uploaded. Both find the Drafts folder in
 * [mailboxes]. The mark a send gives the message it answers goes through [changes], like every
 * other mark. Each saved copy gets its Message-ID from [newMessageId].
 */
class JmapComposeMail(
    connections: JmapConnections,
    mailboxes: Mailboxes,
    changes: EmailChangeQueue,
    newMessageId: () -> String = ::draftMessageId,
) {
    private val copies = DraftCopies(mailboxes)
    val drafts: DraftRepository = JmapDraftRepository(connections, copies, newMessageId)
    val sender: JmapDraftSender = JmapDraftSender(connections, copies, changes)
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

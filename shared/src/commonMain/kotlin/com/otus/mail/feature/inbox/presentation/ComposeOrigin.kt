package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.AddressBook
import com.otus.mail.feature.inbox.domain.AttachmentFiles
import com.otus.mail.feature.inbox.domain.AttachmentUploads
import com.otus.mail.feature.inbox.domain.DraftRepository
import com.otus.mail.feature.inbox.domain.DraftSender
import com.otus.mail.feature.inbox.domain.PhotoCleaning
import com.otus.mail.feature.inbox.domain.ScheduledSends
import com.otus.mail.feature.inbox.domain.ThreadHistory

/** An account a draft can be sent from, by its [name] in the Send-from list. */
data class ComposeAccount(
    val id: String,
    val name: String,
)

/**
 * Where a draft saves, sends, and finds people to suggest; [scheduled] where sending later works,
 * [threads] for the earlier messages of a reply's thread, and [files] where the user can pick
 * files to attach, which [uploads] sends to the server.
 */
class ComposeServices(
    val drafts: DraftRepository,
    val sender: DraftSender,
    val addressBook: AddressBook,
    val scheduled: ScheduledSends? = null,
    val threads: ThreadHistory? = null,
    val uploads: AttachmentUploads? = null,
    val files: AttachmentFiles? = null,
)

/**
 * The accounts around a draft: it starts in [accountId], and every one of [accounts] can send it.
 * [ownAddresses] are left out of Reply all, and [recentFrom] picks the address of a new message
 * from all accounts. A new message always gets the identity's signature; replies and forwards
 * get it only with [signatureInReplies]. [photoCleaning] decides whether attached photos lose their
 * metadata and names; without it, files attach as picked.
 */
class ComposeOrigin(
    val accountId: String,
    val accounts: List<ComposeAccount>,
    val ownAddresses: Collection<String>,
    val recentFrom: List<String> = emptyList(),
    val signatureInReplies: Boolean = true,
    val photoCleaning: () -> PhotoCleaning = { PhotoCleaning.Never },
)

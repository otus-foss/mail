package com.otus.mail.feature.compose.presentation

import com.otus.avatar.ResolvedAvatar
import com.otus.mail.core.files.AttachmentFiles
import com.otus.mail.core.messages.domain.AddressBook
import com.otus.mail.core.messages.domain.DraftRepository
import com.otus.mail.core.messages.domain.ScheduledSends
import com.otus.mail.core.messages.domain.SendIdentity
import com.otus.mail.core.settings.domain.PhotoCleaning
import com.otus.mail.feature.compose.domain.AttachmentUploads
import com.otus.mail.feature.compose.domain.ThreadHistory

/** An account a draft can be sent from, by its [name] in the Send-from list. */
data class ComposeAccount(
    val id: String,
    val name: String,
)

/**
 * Where a draft saves and finds people to suggest; [scheduled] where sending later works,
 * [threads] for the earlier messages of a reply's thread, and [files] where the user can pick
 * files to attach, which [uploads] sends to the server.
 */
class ComposeServices(
    val drafts: DraftRepository,
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
 * metadata and names; without it, files attach as picked. [ownAvatar] is the avatar the user gave
 * an account's address, which the Send-from choices show.
 */
class ComposeOrigin(
    val accountId: String,
    val accounts: List<ComposeAccount>,
    val ownAddresses: Collection<String>,
    val recentFrom: List<String> = emptyList(),
    val signatureInReplies: Boolean = true,
    val photoCleaning: () -> PhotoCleaning = { PhotoCleaning.Never },
    val ownAvatar: (accountId: String, address: String) -> ResolvedAvatar? = { _, _ -> null },
)

/** [identity] with the avatar the user gave its address, for the Send-from choices. */
internal fun ComposeOrigin.withOwnAvatar(identity: SendIdentity): SendIdentity =
    ownAvatar(identity.accountId, identity.sender.address)
        ?.let { identity.copy(sender = identity.sender.copy(avatar = it)) }
        ?: identity

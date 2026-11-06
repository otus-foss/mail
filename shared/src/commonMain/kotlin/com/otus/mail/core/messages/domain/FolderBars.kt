package com.otus.mail.core.messages.domain

import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.MailAction.DeleteForever
import com.otus.mail.core.messages.domain.MailAction.EmptyFolder
import com.otus.mail.core.messages.domain.MailAction.Filters
import com.otus.mail.core.messages.domain.MailAction.FollowUp
import com.otus.mail.core.messages.domain.MailAction.Forward
import com.otus.mail.core.messages.domain.MailAction.NewMail
import com.otus.mail.core.messages.domain.MailAction.NotSpam
import com.otus.mail.core.messages.domain.MailAction.Restore
import com.otus.mail.core.messages.domain.MailAction.Search
import com.otus.mail.core.settings.domain.MailBehavior

/**
 * [place]'s default bar on a page of [role] folders; null where the place keeps its usual one.
 * Selections in Sent keep theirs, because Follow up and Forward need one message.
 */
fun ActionBarPlace.folderDefault(role: MailboxRole): ActionBarLayout? =
    when (role) {
        MailboxRole.Junk -> cleanupBar(NotSpam)
        MailboxRole.Trash -> cleanupBar(Restore)
        MailboxRole.Sent -> ActionBarLayout(listOf(Forward), FollowUp).takeIf { this == ActionBarPlace.Message }
        MailboxRole.Inbox, MailboxRole.Drafts, MailboxRole.Archive -> null
    }

/** Spam and Trash: messages leave with [primary] or for good, and the list can be emptied. */
private fun ActionBarPlace.cleanupBar(primary: MailAction): ActionBarLayout? =
    when (this) {
        ActionBarPlace.Inbox -> ActionBarLayout(listOf(Search, Filters, EmptyFolder), NewMail)
        ActionBarPlace.Selection, ActionBarPlace.Message -> ActionBarLayout(listOf(DeleteForever), primary)
        ActionBarPlace.Compose, ActionBarPlace.Everywhere -> null
    }

/**
 * The bar [place] shows on a page of [role] folders. While the place's saved bar is still its
 * default, in Per screen mode, the folder's default replaces it. A bar the user changed, and the
 * Same everywhere bar, look the same in every folder.
 */
fun MailBehavior.barLayout(
    place: ActionBarPlace,
    role: MailboxRole?,
): ActionBarLayout =
    role
        ?.takeIf { actionBarMode == ActionBarMode.Adaptive && savedLayout(place) == place.defaultLayout }
        ?.let(place::folderDefault)
        ?: barLayout(place)

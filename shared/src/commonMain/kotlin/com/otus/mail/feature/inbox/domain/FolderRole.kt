package com.otus.mail.feature.inbox.domain

import com.otus.mail.feature.inbox.domain.MailAction.DeleteForever
import com.otus.mail.feature.inbox.domain.MailAction.EmptyFolder
import com.otus.mail.feature.inbox.domain.MailAction.Filters
import com.otus.mail.feature.inbox.domain.MailAction.FollowUp
import com.otus.mail.feature.inbox.domain.MailAction.Forward
import com.otus.mail.feature.inbox.domain.MailAction.NewMail
import com.otus.mail.feature.inbox.domain.MailAction.NotSpam
import com.otus.mail.feature.inbox.domain.MailAction.Restore
import com.otus.mail.feature.inbox.domain.MailAction.Search

/** A system folder with bars of its own, found by its role (RFC 8621, section 2). */
enum class FolderRole(
    val role: String,
) {
    Junk("junk"),
    Trash("trash"),
    Sent("sent"),
    ;

    companion object {
        fun of(role: String?): FolderRole? = entries.firstOrNull { it.role == role }
    }
}

/**
 * [place]'s default bar on a page of [role] folders; null where the place keeps its usual one.
 * Selections in Sent keep theirs, because Follow up and Forward need one message.
 */
fun ActionBarPlace.folderDefault(role: FolderRole): ActionBarLayout? =
    when (role) {
        FolderRole.Junk -> cleanupBar(NotSpam)
        FolderRole.Trash -> cleanupBar(Restore)
        FolderRole.Sent -> ActionBarLayout(listOf(Forward), FollowUp).takeIf { this == ActionBarPlace.Message }
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
    role: FolderRole?,
): ActionBarLayout {
    val untouched = actionBarMode == ActionBarMode.Adaptive && savedLayout(place) == place.defaultLayout
    val folderBar = role?.takeIf { untouched }?.let(place::folderDefault)
    return folderBar ?: barLayout(place)
}

package com.otus.mail.feature.inbox.domain

import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.QuickFacet
import com.otus.mail.core.settings.domain.FilterSection
import com.otus.mail.core.settings.domain.InboxPill

/**
 * What a list page holds, which decides the filters that can narrow it. The user's filter layout
 * picks from every filter; a page leaves out those that would match all of its mail or none.
 */
enum class ListKind {
    Inbox,
    Drafts,
    Sent,

    /** Mail waiting for its send time. */
    Scheduled,
    Spam,
    Conversations,

    /** Any other folder: Archive, Trash, Read later, and the user's own. */
    Folder,
    ;

    /** Whether nobody received the mail here yet, so no thread, mark, or keyword tells it apart. */
    val unsent: Boolean
        get() = this == Drafts || this == Scheduled

    /** Whether the user wrote all the mail here, so it is read and has no sender to pick. */
    val outgoing: Boolean
        get() = unsent || this == Sent

    /** Status has nothing to split in mail the user wrote, which is always read. */
    fun offers(section: FilterSection): Boolean = section != FilterSection.Status || !outgoing

    fun offers(facet: QuickFacet): Boolean = facet !in hiddenQuick

    /** Whether the mail type filters show: newsletters and invites arrive, and the user's own mail is neither. */
    val offersTypes: Boolean
        get() = !outgoing

    /** A row pill shows where the filter it turns on does: Unread with Status, the others as quick filters. */
    fun offers(pill: InboxPill.Filter): Boolean = pill.quick?.let { offers(it) } ?: offers(FilterSection.Status)

    private val hiddenQuick: Set<QuickFacet>
        get() =
            when (this) {
                Inbox -> emptySet()

                // Mail not sent yet only has attachments.
                Drafts, Scheduled -> QuickFacet.entries.toSet() - QuickFacet.Attachments

                // A sent row leads with the user's own email, and servers mark the forwarded
                // original, not the copy that went out.
                Sent -> setOf(QuickFacet.ReadLater, QuickFacet.NeedsMyReply, QuickFacet.Forwarded)

                // Nobody answers spam.
                Spam -> NO_THREADS + QuickFacet.Forwarded

                Conversations, Folder -> setOf(QuickFacet.ReadLater)
            }

    companion object {
        /** The kind of a folder with [role]; folders without one, and Archive and Trash, filter alike. */
        fun of(role: MailboxRole?): ListKind =
            when (role) {
                MailboxRole.Inbox -> Inbox
                MailboxRole.Drafts -> Drafts
                MailboxRole.Sent -> Sent
                MailboxRole.Scheduled -> Scheduled
                MailboxRole.Junk -> Spam
                MailboxRole.Archive, MailboxRole.Trash, null -> Folder
            }

        /**
         * Read later is a folder of its own, so only the Inbox, which spans folders, can narrow to
         * it. The reply and conversation filters read a thread's back-and-forth.
         */
        private val NO_THREADS =
            setOf(
                QuickFacet.ReadLater,
                QuickFacet.NeedsMyReply,
                QuickFacet.AwaitingReply,
                QuickFacet.Conversations,
            )
    }
}

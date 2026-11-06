package com.otus.mail.app

import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.feature.contacts.domain.CardKey

/** Every page, including subpages. Pages without a parent are sidebar destinations. */
internal sealed interface MailPage {
    /** Where the chevron and the system back gesture lead; null for sidebar destinations. */
    val parent: MailPage?
        get() = null

    /** The sidebar destination this page belongs to. */
    val root: MailPage
        get() = parent?.root ?: this

    /** How many parents lead back to a sidebar destination, which is at depth 0. */
    val depth: Int
        get() = parent?.let { it.depth + 1 } ?: 0

    data object Inbox : MailPage

    data object Settings : MailPage

    data object EmailAccounts : MailPage {
        override val parent = Settings
    }

    data object Appearance : MailPage {
        override val parent = Settings
    }

    data object Notifications : MailPage {
        override val parent = Settings
    }

    data object NotificationOverrides : MailPage {
        override val parent = Notifications
    }

    data object Behavior : MailPage {
        override val parent = Settings
    }

    data object SidebarSettings : MailPage {
        override val parent = Behavior
    }

    data object FilterPanelSettings : MailPage {
        override val parent = Behavior
    }

    data object PinnedSettings : MailPage {
        override val parent = SidebarSettings
    }

    data object ThemeSettings : MailPage {
        override val parent = Appearance
    }

    data object MailListSettings : MailPage {
        override val parent = Appearance
    }

    data object ActionBarSettings : MailPage {
        override val parent = Behavior
    }

    data object Privacy : MailPage {
        override val parent = Settings
    }

    data object AllowedSenders : MailPage {
        override val parent = Privacy
    }

    /** A draft started from [request]; it closes back to [parent], the page that opened it. */
    data class Compose(
        val request: ComposeRequest,
        override val parent: MailPage = Inbox,
    ) : MailPage

    data object Folders : MailPage {
        override val parent = Settings
    }

    data object Signatures : MailPage {
        override val parent = Behavior
    }

    /** Edits the signature of the identity [identityId] of [accountId]. */
    data class SignatureEditor(
        val accountId: String,
        val identityId: String,
    ) : MailPage {
        override val parent = Signatures
    }

    /**
     * One folder's settings from Settings › Folders & rules: [accountId] is the login, [folderId]
     * the mailbox id, and [sharedIn] the JMAP account of a mailbox shared with the login, else null.
     */
    data class FolderDetail(
        val accountId: String,
        val folderId: String,
        val sharedIn: String? = null,
    ) : MailPage {
        override val parent = Folders
    }

    /** One folder's messages; [accountId] is the login and [folderId] the server's mailbox id. */
    data class Folder(
        val accountId: String,
        val folderId: String,
    ) : MailPage

    /** One system folder of every account, such as the Trash of all of them. */
    data class Combined(
        val role: MailboxRole,
    ) : MailPage

    /** Every account's Read later folder. */
    data object ReadLater : MailPage

    /** Every thread where the user and someone else both wrote, across accounts. */
    data object Conversations : MailPage

    /** One of the user's folders, merged across accounts; [key] is its lower-case path. */
    data class Shared(
        val key: List<String>,
    ) : MailPage

    /** Every login's contacts. */
    data object Contacts : MailPage

    /** One person, opened from the card [key]; a group card opens its members. */
    data class Contact(
        val key: CardKey,
    ) : MailPage {
        override val parent = Contacts
    }

    /** The editor for the card [key], or for a new contact while it is null. */
    data class ContactEditor(
        val key: CardKey? = null,
    ) : MailPage {
        override val parent: MailPage = key?.let(::Contact) ?: Contacts
    }
}

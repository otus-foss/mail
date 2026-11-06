package com.otus.mail.app

import androidx.compose.runtime.saveable.Saver
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.inbox.domain.ComposeRequest

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

    companion object {
        private val fixed =
            listOf(
                Inbox,
                Settings,
                EmailAccounts,
                Appearance,
                Notifications,
                NotificationOverrides,
                Behavior,
                ActionBarSettings,
                Folders,
                ReadLater,
                Conversations,
                Contacts,
                SidebarSettings,
                PinnedSettings,
                FilterPanelSettings,
                ThemeSettings,
                MailListSettings,
                Privacy,
                AllowedSenders,
            )

        val Saver: Saver<MailPage, List<String>> =
            Saver(
                save = { page -> saved(page) },
                restore = { saved ->
                    when (saved.firstOrNull()) {
                        FOLDER -> Folder(saved[1], saved[2])
                        FOLDER_DETAIL -> FolderDetail(saved[1], saved[2], saved[3].ifEmpty { null })
                        COMBINED -> combinedRole(saved[1])?.let(::Combined) ?: Inbox
                        SHARED -> Shared(saved.drop(1))
                        CONTACT -> Contact(CardKey(saved[1], saved[2], saved[3]))
                        CONTACT_EDITOR -> ContactEditor(saved.getOrNull(1)?.let { CardKey(it, saved[2], saved[3]) })
                        else -> fixed.firstOrNull { it.toString() == saved.firstOrNull() } ?: Inbox
                    }
                },
            )

        /** A draft's request holds a whole message, so a restored app returns to the page that opened it. */
        private fun saved(page: MailPage): List<String> =
            when (page) {
                is Folder -> listOf(FOLDER, page.accountId, page.folderId)
                is FolderDetail -> listOf(FOLDER_DETAIL, page.accountId, page.folderId, page.sharedIn.orEmpty())
                is Combined -> listOf(COMBINED, page.role.wire)
                is Shared -> listOf(SHARED) + page.key
                is Compose -> saved(page.parent)
                is Contact -> listOf(CONTACT, page.key.loginId, page.key.accountId, page.key.cardId)
                is ContactEditor -> listOf(CONTACT_EDITOR) + savedKey(page.key)
                else -> listOf(page.toString())
            }

        private fun savedKey(key: CardKey?): List<String> =
            key?.let { listOf(it.loginId, it.accountId, it.cardId) }.orEmpty()

        private const val FOLDER = "Folder"
        private const val FOLDER_DETAIL = "FolderDetail"

        /**
         * The combined role saved as its JMAP role, or by builds before 2026-11-06 under the old
         * folder names, such as `Spam`; null for anything else.
         */
        internal fun combinedRole(saved: String): MailboxRole? =
            (MailboxRole.of(saved) ?: MailboxRole.Junk.takeIf { saved == OLD_SPAM })?.takeIf {
                it in
                    MailboxRole.combined
            }

        private const val OLD_SPAM = "Spam"
        private const val COMBINED = "Combined"
        private const val SHARED = "Shared"
        private const val CONTACT = "Contact"
        private const val CONTACT_EDITOR = "ContactEditor"
    }
}

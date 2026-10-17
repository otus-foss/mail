package com.otus.mail.app

import androidx.compose.runtime.saveable.Saver
import com.otus.mail.feature.folders.domain.CombinedFolder
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

    data object SidebarSettings : MailPage {
        override val parent = Appearance
    }

    data object InboxSettings : MailPage {
        override val parent = Appearance
    }

    data object ThemeSettings : MailPage {
        override val parent = Appearance
    }

    data object ActionBarSettings : MailPage {
        override val parent = Appearance
    }

    /** A draft started from [request]; it closes back to [parent], the page that opened it. */
    data class Compose(
        val request: ComposeRequest,
        override val parent: MailPage = Inbox,
    ) : MailPage

    data object Folders : MailPage {
        override val parent = Settings
    }

    /**
     * One folder's settings from Settings › Folders: [accountId] is the login, [folderId] the
     * mailbox id, and [sharedIn] the JMAP account of a mailbox shared with the login, else null.
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
        val folder: CombinedFolder,
    ) : MailPage

    /** Every account's Read later folder. */
    data object ReadLater : MailPage

    /** One of the user's folders, merged across accounts; [key] is its lower-case path. */
    data class Shared(
        val key: List<String>,
    ) : MailPage

    companion object {
        private val fixed =
            listOf(
                Inbox,
                Settings,
                EmailAccounts,
                Appearance,
                Notifications,
                ActionBarSettings,
                Folders,
                ReadLater,
                SidebarSettings,
                InboxSettings,
                ThemeSettings,
            )

        val Saver: Saver<MailPage, List<String>> =
            Saver(
                save = { page -> saved(page) },
                restore = { saved ->
                    when (saved.firstOrNull()) {
                        FOLDER -> Folder(saved[1], saved[2])
                        FOLDER_DETAIL -> FolderDetail(saved[1], saved[2], saved[3].ifEmpty { null })
                        COMBINED -> CombinedFolder.entries.firstOrNull { it.name == saved[1] }?.let(::Combined) ?: Inbox
                        SHARED -> Shared(saved.drop(1))
                        else -> fixed.firstOrNull { it.toString() == saved.firstOrNull() } ?: Inbox
                    }
                },
            )

        /** A draft's request holds a whole message, so a restored app returns to the page that opened it. */
        private fun saved(page: MailPage): List<String> =
            when (page) {
                is Folder -> listOf(FOLDER, page.accountId, page.folderId)
                is FolderDetail -> listOf(FOLDER_DETAIL, page.accountId, page.folderId, page.sharedIn.orEmpty())
                is Combined -> listOf(COMBINED, page.folder.name)
                is Shared -> listOf(SHARED) + page.key
                is Compose -> saved(page.parent)
                else -> listOf(page.toString())
            }

        private const val FOLDER = "Folder"
        private const val FOLDER_DETAIL = "FolderDetail"
        private const val COMBINED = "Combined"
        private const val SHARED = "Shared"
    }
}

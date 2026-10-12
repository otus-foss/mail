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

    data object Inbox : MailPage

    data object Settings : MailPage

    data object EmailAccounts : MailPage {
        override val parent = Settings
    }

    data object Appearance : MailPage {
        override val parent = Settings
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

    data object Rules : MailPage {
        override val parent = Settings
    }

    /** Edits one rule of [accountId]; a null [ruleId] starts a new rule. */
    data class RuleEditor(
        val accountId: String,
        val ruleId: String?,
    ) : MailPage {
        override val parent = Rules
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

    companion object {
        private val fixed = listOf(Inbox, Settings, EmailAccounts, Appearance, ActionBarSettings, Folders, Rules)

        val Saver: Saver<MailPage, List<String>> =
            Saver(
                save = { page -> saved(page) },
                restore = { saved ->
                    when (saved.firstOrNull()) {
                        FOLDER -> Folder(saved[1], saved[2])
                        RULE -> RuleEditor(saved[1], saved[2].ifEmpty { null })
                        COMBINED -> CombinedFolder.entries.firstOrNull { it.name == saved[1] }?.let(::Combined) ?: Inbox
                        else -> fixed.firstOrNull { it.toString() == saved.firstOrNull() } ?: Inbox
                    }
                },
            )

        /** A draft's request holds a whole message, so a restored app returns to the page that opened it. */
        private fun saved(page: MailPage): List<String> =
            when (page) {
                is Folder -> listOf(FOLDER, page.accountId, page.folderId)
                is RuleEditor -> listOf(RULE, page.accountId, page.ruleId.orEmpty())
                is Combined -> listOf(COMBINED, page.folder.name)
                is Compose -> saved(page.parent)
                else -> listOf(page.toString())
            }

        private const val FOLDER = "Folder"
        private const val RULE = "RuleEditor"
        private const val COMBINED = "Combined"
    }
}

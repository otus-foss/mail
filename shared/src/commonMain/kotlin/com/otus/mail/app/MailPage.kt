package com.otus.mail.app

import androidx.compose.runtime.saveable.Saver

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

    companion object {
        private val fixed = listOf(Inbox, Settings, EmailAccounts, Appearance, Folders, Rules)

        val Saver: Saver<MailPage, List<String>> =
            Saver(
                save = { page ->
                    when (page) {
                        is Folder -> listOf(FOLDER, page.accountId, page.folderId)
                        is RuleEditor -> listOf(RULE, page.accountId, page.ruleId.orEmpty())
                        else -> listOf(page.toString())
                    }
                },
                restore = { saved ->
                    when (saved.firstOrNull()) {
                        FOLDER -> Folder(saved[1], saved[2])
                        RULE -> RuleEditor(saved[1], saved[2].ifEmpty { null })
                        else -> fixed.firstOrNull { it.toString() == saved.firstOrNull() } ?: Inbox
                    }
                },
            )

        private const val FOLDER = "Folder"
        private const val RULE = "RuleEditor"
    }
}

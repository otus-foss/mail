package com.otus.mail.app

import com.otus.mail.core.messages.presentation.MailEdits
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.feature.folders.presentation.FolderCleaning
import com.otus.mail.feature.folders.presentation.FolderColorsState
import com.otus.mail.feature.folders.presentation.FolderNudgeState
import com.otus.mail.feature.folders.presentation.FolderRulesState
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.folders.presentation.MessageFiling
import com.otus.mail.feature.folders.presentation.MessageSorting
import com.otus.mail.feature.folders.presentation.SenderRules
import com.otus.mail.feature.folders.presentation.forget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Every account's folders and what keeps mail in them: colors, the color nudge, Sieve rules, moves,
 * the sorting offer after a move, and cleaning. One per [MailStates].
 */
internal class FolderStates(
    services: MailServices,
    scope: CoroutineScope,
    behavior: MailBehaviorState,
    edits: MailEdits,
) {
    val all: FoldersState =
        FoldersState(
            services.folders,
            scope,
            loadCached = { services.readLater.cachedAccounts { behavior.behavior.readLater } },
            loadAccounts = { services.readLater.accounts { behavior.behavior.readLater } },
            onDeleted = {
                colors.forget(it)
                rules.forget(it)
            },
        )

    init {
        // Folder counts follow every saved change of mail.
        scope.launch { edits.changed.collect { all.refresh() } }
    }

    /** Colors the user gave their folders; the Folder colors switch hides them. */
    val colors: FolderColorsState =
        FolderColorsState(
            services.stores.folders.colors,
            { all.accounts },
        ) { behavior.behavior.sidebar.layout.folderColors }

    /** Which folder the color nudge offers a color for; only while folder colors and folder icons are on. */
    val nudge =
        FolderNudgeState(services.stores.folders.nudges, colors, { all.accounts }) {
            behavior.behavior.sidebar.layout.folderColors && behavior.behavior.list.layout.folderIcons
        }

    /** Each account's folder rules, kept in the Otus block of its active Sieve script. */
    val rules = FolderRulesState(services.rules, scope)

    val filing = MessageFiling(services.senderHistory, all, colors, edits)

    /** Rules that sort mail like a moved message, and the offer that follows a saved one. */
    val sorting = MessageSorting(rules, services.rules, all, filing, scope)

    /** "Rule for sender": a folder, then the folder's Add rule sheet. */
    val senderRules = SenderRules(rules, all)

    val cleaning = FolderCleaning(all, edits)

    /** Loads the folders, their rules, and their colors again, as after the accounts change. */
    fun reload() {
        all.refresh()
        rules.refresh()
        colors.reload()
    }
}

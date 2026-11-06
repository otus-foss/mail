package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.folderTree
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.messages.domain.MessageFolder
import com.otus.mail.core.messages.domain.OwnAddresses
import com.otus.mail.core.messages.presentation.MailEdits
import com.otus.mail.core.messages.presentation.MessageActions
import com.otus.mail.core.settings.domain.allowingSender
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.feature.folders.presentation.FolderColorsState
import com.otus.mail.feature.folders.presentation.FolderDialog
import com.otus.mail.feature.folders.presentation.components.FolderActions
import com.otus.mail.feature.folders.presentation.components.FolderDialogs
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.components.ReadView
import com.otus.mail.feature.read.presentation.ReadContext
import com.otus.mail.feature.read.presentation.ReadDisplay
import com.otus.mail.feature.read.presentation.ReadEdits
import com.otus.mail.feature.read.presentation.ReadEffects
import com.otus.mail.feature.read.presentation.ReadFiles
import com.otus.mail.feature.read.presentation.ReadFolders
import com.otus.mail.feature.read.presentation.ReadPrivacy
import com.otus.mail.feature.read.presentation.ReadServices
import com.otus.mail.feature.read.presentation.components.NewFolderSheet
import com.otus.mail.feature.read.presentation.components.PersonSheet
import com.otus.mail.feature.read.presentation.screens.MessageScreen
import kotlinx.coroutines.launch

/**
 * Connects the Read view to the app: the services behind its pages, strips, and menu, the saved
 * privacy setting, and the folders of each account. Its changes are [edits] on behalf of the list
 * it opened from. Its toasts and their work belong to the process, see [MailServices.work].
 * [folderActions] make the folders that the folders sheet starts. One per [MailStates].
 */
internal class ReadWiring(
    private val services: MailServices,
    private val behavior: MailBehaviorState,
    private val folders: FolderStates,
    private val edits: MailEdits,
    private val folderActions: FolderActions,
) {
    private val effects =
        ReadEffects(edits.toasts.toasts, launch = { task -> services.work.launch { task() } }, services.newsletterSteps)
    private val privacy =
        ReadPrivacy(
            setting = { behavior.behavior.remoteImages },
            allowed = { behavior.behavior.allowedSenders },
            known = services.knownPeople,
            allowSender = { address -> behavior.update { it.allowingSender(address) } },
        )
    private val files = ReadFiles(services.attachments, services.pickers.savedFiles, services.pickers.printer)

    /** The Read view of messages that [feed] lists, whose folder, when it shows one, is left out of strips. */
    fun context(
        feed: InboxFeedState,
        person: PersonSheet?,
        own: OwnAddresses,
        onCompose: (ComposeRequest) -> Unit,
    ): ReadContext =
        ReadContext(
            pages = services.conversationMessages,
            folders =
                ReadFolders(
                    of = { message ->
                        folders.all
                            .account(message.location.accountId)
                            ?.messageFolders(folders.colors)
                            .orEmpty()
                    },
                    cameFrom = { message -> cameFrom(feed.mailScope, message) },
                    newFolder =
                        NewFolderSheet { accountId, onDismiss ->
                            val dialog = FolderDialog.Create(accountId, parentId = null)
                            FolderDialogs(dialog, folders.all.account(accountId), folderActions) {
                                if (it == null) onDismiss()
                            }
                        },
                ),
            privacy = privacy,
            files = files,
            services =
                ReadServices(services.newsletters, services.headers, services.blocks, ReadEdits(edits, feed), person),
            effects = effects,
            display =
                ReadDisplay(
                    showQuotes = { behavior.behavior.showQuotes },
                    zen = { behavior.behavior.zenRead },
                    setZen = { on -> behavior.update { it.copy(zenRead = on) } },
                ),
            own = own,
            loadContent = services.bodies::content,
            onCompose = onCompose,
        )

    /**
     * The folders of [message]'s account that [scope] lists: a folder page's folder, or the account's
     * folders of the roles a page such as the unified inbox shows, with its own folders when the page
     * shows them too. Empty for lists without a folder, such as All Mail.
     */
    private fun cameFrom(
        scope: MailScope,
        message: MailMessage,
    ): Set<String> {
        val accountId = message.location.accountId
        val own =
            folders.all
                .account(accountId)
                ?.folders
                .orEmpty()
        return when (scope) {
            is MailScope.Folder -> if (scope.accountId == accountId) setOf(scope.folderId) else emptySet()
            is MailScope.Folders -> scope.folderIds(accountId)
            is MailScope.Role -> own.filter { it.role == scope.role }.map { it.id }.toSet()
            is MailScope.Roles -> scope.folderIds(own)
            else -> emptySet()
        }
    }

    /** The account's folders in tree order, each with its full path and saved color. */
    private fun AccountFolders.messageFolders(colors: FolderColorsState): List<MessageFolder> =
        folderTree().map { node ->
            MessageFolder(node.folder.id, node.path, node.folder.role, colors.colorOf(node.folder), node.readLater)
        }
}

/** The Read view of [feed]'s rows on [page], whose drafts close back to that page; again when the feed changes. */
@Composable
internal fun rememberReadView(
    states: MailStates,
    feed: InboxFeedState,
    navigation: MailNavigation,
    page: MailPage,
    actions: MessageActions,
    own: OwnAddresses,
): ReadView {
    val read =
        remember(states, feed, own) {
            states.reading.context(feed, contactSheet(states.contacts.cards, navigation), own) {
                navigation.go(MailPage.Compose(it, parent = page))
            }
        }
    return ReadView { opened ->
        val cameFrom = if (opened.mixed) emptySet() else read.folders.cameFrom(opened.message)
        MessageScreen(opened.message, actions, opened.notice, read, cameFrom, opened.close)
    }
}

private fun MailScope.Roles.folderIds(folders: List<MailFolder>): Set<String> =
    folders
        .filter { it.role in roles || (ownFolders && it.isCustom) }
        .map { it.id }
        .toSet()

private fun MailScope.Folders.folderIds(accountId: String): Set<String> =
    folders
        .filter { it.accountId == accountId }
        .map { it.folderId }
        .toSet()

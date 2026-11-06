package com.otus.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otus.mail.core.mailbox.AccountFolders
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.folderTree
import com.otus.mail.feature.folders.presentation.FolderColorsState
import com.otus.mail.feature.folders.presentation.FolderDialog
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.folders.presentation.components.FolderActions
import com.otus.mail.feature.folders.presentation.components.FolderDialogs
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MessageFolder
import com.otus.mail.feature.inbox.domain.allowingSender
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.ReadContext
import com.otus.mail.feature.inbox.presentation.ReadDisplay
import com.otus.mail.feature.inbox.presentation.ReadEffects
import com.otus.mail.feature.inbox.presentation.ReadFiles
import com.otus.mail.feature.inbox.presentation.ReadFolders
import com.otus.mail.feature.inbox.presentation.ReadPrivacy
import com.otus.mail.feature.inbox.presentation.ReadServices
import com.otus.mail.feature.inbox.presentation.components.NewFolderSheet
import com.otus.mail.feature.inbox.presentation.components.PersonSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Connects the Read view to the app: the services behind its pages, strips, and menu, the saved
 * privacy setting, and the folders of each account. Folder and page changes show on the list at
 * once and are undone with a notice if the server refuses them; deleting one page offers Undo in
 * [moveToasts]. Its toasts and their work belong to the process, see [MailServices.work].
 * [folderActions] make the folders that the folders sheet starts. One per [MailStates].
 */
internal class ReadWiring(
    private val services: MailServices,
    private val scope: CoroutineScope,
    private val behavior: MailBehaviorState,
    private val folders: FoldersState,
    private val colors: FolderColorsState,
    private val moveToasts: MoveToasts,
    private val folderActions: FolderActions,
) {
    private val effects =
        ReadEffects(moveToasts.toasts, launch = { task -> services.work.launch { task() } }, services.newsletterSteps)
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
        person: PersonSheet? = null,
    ): ReadContext {
        val changes = ReadPageChanges(services, folders, moveToasts, scope, feed)
        return ReadContext(
            pages = services.conversationMessages,
            folders =
                ReadFolders(
                    of = { message -> folders.account(message.location.accountId)?.messageFolders().orEmpty() },
                    cameFrom = { message -> cameFrom(feed.mailScope, message) },
                    apply = changes::refile,
                    newFolder =
                        NewFolderSheet { accountId, onDismiss ->
                            val dialog = FolderDialog.Create(accountId, parentId = null)
                            FolderDialogs(dialog, folders.account(accountId), folderActions) {
                                if (it == null) onDismiss()
                            }
                        },
                ),
            privacy = privacy,
            files = files,
            services =
                ReadServices(services.newsletters, services.headers, services.mute, services.blocks, changes, person),
            effects = effects,
            display =
                ReadDisplay(
                    showQuotes = { behavior.behavior.showQuotes },
                    zen = { behavior.behavior.zenRead },
                    setZen = { on -> behavior.update { it.copy(zenRead = on) } },
                ),
        )
    }

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
        val own = folders.account(accountId)?.folders.orEmpty()
        return when (scope) {
            is MailScope.Folder -> if (scope.accountId == accountId) setOf(scope.folderId) else emptySet()
            is MailScope.Folders -> scope.folderIds(accountId)
            is MailScope.Role -> own.filter { it.role == scope.role }.map { it.id }.toSet()
            is MailScope.Roles -> scope.folderIds(own)
            else -> emptySet()
        }
    }

    /** The account's folders in tree order, each with its full path and saved color. */
    private fun AccountFolders.messageFolders(): List<MessageFolder> =
        folderTree().map { node ->
            MessageFolder(node.folder.id, node.path, node.folder.role, colors.colorOf(node.folder), node.readLater)
        }
}

/** The Read view for [feed]'s list, again when the feed changes. */
@Composable
internal fun rememberReadContext(
    states: MailStates,
    feed: InboxFeedState,
    navigation: MailNavigation,
): ReadContext = remember(states, feed) { states.reading.context(feed, contactSheet(states, navigation)) }

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

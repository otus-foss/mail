package com.otusfoss.mail.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.OwnAddresses
import com.otusfoss.mail.core.messages.domain.PersonHistories
import com.otusfoss.mail.core.messages.presentation.MailEdits
import com.otusfoss.mail.core.messages.presentation.MessageActions
import com.otusfoss.mail.core.messages.presentation.actions.ScheduledActions
import com.otusfoss.mail.core.settings.domain.allowingSender
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import com.otusfoss.mail.feature.folders.presentation.FolderDialog
import com.otusfoss.mail.feature.folders.presentation.account
import com.otusfoss.mail.feature.folders.presentation.components.FolderActions
import com.otusfoss.mail.feature.folders.presentation.components.FolderDialogs
import com.otusfoss.mail.feature.inbox.presentation.InboxFeedState
import com.otusfoss.mail.feature.inbox.presentation.InboxFilterState
import com.otusfoss.mail.feature.inbox.presentation.components.ReadView
import com.otusfoss.mail.feature.read.presentation.ReadCategories
import com.otusfoss.mail.feature.read.presentation.ReadContext
import com.otusfoss.mail.feature.read.presentation.ReadDisplay
import com.otusfoss.mail.feature.read.presentation.ReadEdits
import com.otusfoss.mail.feature.read.presentation.ReadEffects
import com.otusfoss.mail.feature.read.presentation.ReadFiles
import com.otusfoss.mail.feature.read.presentation.ReadLoads
import com.otusfoss.mail.feature.read.presentation.ReadPrivacy
import com.otusfoss.mail.feature.read.presentation.ReadReplies
import com.otusfoss.mail.feature.read.presentation.ReadSender
import com.otusfoss.mail.feature.read.presentation.ReadSenderMail
import com.otusfoss.mail.feature.read.presentation.ReadServices
import com.otusfoss.mail.feature.read.presentation.components.NewFolderSheet
import com.otusfoss.mail.feature.read.presentation.components.PersonSheet
import com.otusfoss.mail.feature.read.presentation.readFolders
import com.otusfoss.mail.feature.read.presentation.screens.MessageScreen
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
            setting = { behavior.behavior.read.remoteImages },
            allowed = { behavior.behavior.read.allowedSenders },
            known = services.knownPeople,
            allowSender = { address -> behavior.updateRead { it.allowingSender(address) } },
        )

    /** Looks up a person's threads for the person sheet. */
    val personHistories: PersonHistories get() = services.personHistories

    fun openMessage(
        loginId: String,
        emailId: String,
    ) = services.requests.openMessage(loginId, emailId)

    private val files = ReadFiles(services.attachments, services.pickers.savedFiles, services.pickers.printer)

    private val categories =
        ReadCategories({ behavior.behavior.list.categories }) { address, category ->
            behavior.updateList { it.copy(categories = it.categories.withSender(address, category)) }
        }

    /**
     * The Read view of messages that [feed] lists, whose folder, when it shows one, is left out of
     * strips. Only a [sorted] list, the Inbox, offers its categories in the ⋯ menu.
     */
    fun context(
        feed: InboxFeedState,
        person: PersonSheet?,
        own: OwnAddresses,
        sorted: Boolean,
        scheduled: ScheduledActions,
        onCompose: (ComposeRequest) -> Unit,
    ): ReadContext =
        ReadContext(
            loads = ReadLoads(services.conversationMessages, services.bodies::content),
            folders =
                readFolders(
                    folders.all::account,
                    { feed.mailScope },
                    folders.colors::colorOf,
                    NewFolderSheet { accountId, onDismiss ->
                        val dialog = FolderDialog.Create(accountId, parentId = null)
                        FolderDialogs(dialog, folders.all.account(accountId), folderActions) {
                            if (it == null) onDismiss()
                        }
                    },
                ),
            privacy = privacy,
            services =
                ReadServices(
                    services.newsletters,
                    services.headers,
                    ReadEdits(edits, feed),
                    person,
                    files,
                    categories.takeIf { sorted },
                    ReadSender(
                        services.blocks,
                        ReadSenderMail(services.senderMails, services.requests::openMessage),
                    ),
                ),
            effects = effects,
            display =
                ReadDisplay(
                    showQuotes = { behavior.behavior.read.showQuotes },
                    zen = { behavior.behavior.read.zen },
                    setZen = { on -> behavior.updateRead { it.copy(zen = on) } },
                ),
            replies = ReadReplies(own, onCompose, scheduled),
        )
}

/**
 * The Read view of [feed]'s rows on [page], whose drafts close back to that page, again when the
 * feed changes. On a [sorted] page its ⋯ menu offers the categories.
 */
@Composable
internal fun rememberReadView(
    states: MailStates,
    feed: InboxFeedState,
    navigation: MailNavigation,
    page: MailPage,
    actions: MessageActions,
    own: OwnAddresses,
    sorted: Boolean,
    filters: InboxFilterState,
): ReadView {
    val scheduled = rememberScheduledActions(states, navigation, page)
    val read =
        remember(states, feed, own, sorted, filters) {
            val person = personSheet(states, filters, navigation, page, own)
            states.reading.context(feed, person, own, sorted, scheduled) {
                navigation.go(MailPage.Compose(it, parent = page))
            }
        }
    return ReadView { opened ->
        val cameFrom = if (opened.mixed) emptySet() else read.folders.cameFrom(opened.message)
        MessageScreen(opened.message, actions, opened.notice, read, cameFrom, opened.close)
    }
}

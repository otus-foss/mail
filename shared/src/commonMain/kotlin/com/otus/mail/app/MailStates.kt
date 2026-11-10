package com.otus.mail.app

import com.otus.accounts.domain.ownAddresses
import com.otus.accounts.presentation.AccountsState
import com.otus.avatar.AvatarOptions
import com.otus.avatar.avatarOptions
import com.otus.avatar.ui.presentation.OwnAvatarsState
import com.otus.dateformat.DateFormatter
import com.otus.mail.core.mail.EmailAddressKey
import com.otus.mail.core.messages.domain.DevicePrivacy
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailScopeChoice
import com.otus.mail.core.messages.domain.MessageContent
import com.otus.mail.core.messages.presentation.MailEdits
import com.otus.mail.core.messages.presentation.MoveToasts
import com.otus.mail.core.settings.presentation.AddressPinsState
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.feature.folders.presentation.components.folderActions
import com.otus.mail.feature.notifications.presentation.NotificationSettingsState
import kotlinx.coroutines.CoroutineScope

/**
 * State holders that outlive a single page, by feature: [folders], [list], [contacts], [compose],
 * and [reading], with the accounts and settings they share. One per composition of [App].
 * [chooseScope] chooses whose mail the pages show, as the sidebar and the inbox's account switcher
 * do; [dates] is the user's date format now, for dates in notices and toasts.
 */
internal class MailStates(
    private val services: MailServices,
    scope: CoroutineScope,
    val behavior: MailBehaviorState,
    private val chosenScope: () -> MailScopeChoice,
    val chooseScope: (MailScopeChoice) -> Unit,
    dates: () -> DateFormatter,
) {
    /** Work that outlives a screen, such as a toast's Retry. */
    val work: CoroutineScope get() = services.work

    /** Whose mail the pages show. A removed account's choice falls back to every account. */
    val mailScope: MailScopeChoice
        get() = chosenScope().within(accounts.accounts.map { it.id })

    /**
     * The changes the user makes to mail, which every list and Read view shows at once. An Undo or
     * Retry can outlive this screen, so it reloads through [MailServices.serverChanges], which the
     * screen shown by then follows.
     */
    val edits: MailEdits = MailEdits(services.writer, scope, services.moveToasts, services.serverChanges::changed)

    val folders: FolderStates = FolderStates(services, scope, behavior, edits)

    val list: ListStates = ListStates(services, scope, behavior, edits, folders, dates) { mailScope }

    val contacts =
        ContactStates(
            services,
            behavior,
            ownAddresses = {
                accounts.accounts
                    .flatMap { it.ownAddresses }
                    .map { EmailAddressKey(it) }
                    .toSet()
            },
        ) { list.refreshAll() }

    val addressPins = AddressPinsState(services.stores.addressPins)

    val devicePrivacy: DevicePrivacy get() = services.devicePrivacy

    /** The avatars of the user's own addresses; a change redraws the mail that shows them. */
    val ownAvatars =
        OwnAvatarsState(services.ownAvatars(behavior::avatarOptions), services.pickers.avatarPhotos, scope) {
            list.refreshAll()
        }

    val accounts: AccountsState =
        AccountsState(services.accounts, scope) {
            list.refreshAll()
            contacts.reload()
            folders.reload()
            addressPins.reload()
        }

    /** Null where the platform has no instant pushes, which hides the Notifications page. */
    val notifications =
        services.instantPush?.let {
            NotificationSettingsState(it, scope, services.stores.alerts.previews) { accounts.accounts }
        }

    val compose =
        ComposeStates(services, scope, behavior, accounts, ownAvatars, dates)

    val moveToasts: MoveToasts get() = services.moveToasts

    val requests: MailRequests get() = services.requests

    /** The Read view's services, folders, and privacy setting; its toasts join the move toasts. */
    val reading =
        ReadWiring(
            services,
            behavior,
            folders,
            edits,
            folderActions(folders.all, folders.colors, folders.rules) { knownAddresses(it) },
        )

    suspend fun messageContent(message: MailMessage): MessageContent? = services.bodies.content(message)
}

/** Avatars load from outside the user's servers only while Website icons is on. */
internal fun MailBehaviorState.avatarOptions(): AvatarOptions =
    behavior.avatars.let { avatarOptions(it.websiteIcons, it.iconSource, it.bulwarkAddress) }

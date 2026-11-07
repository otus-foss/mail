package com.otus.mail.app

import com.otus.mail.core.mail.EmailAddressKey
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.feature.contacts.data.VCardFiles
import com.otus.mail.feature.contacts.presentation.ContactAvatarUpdates
import com.otus.mail.feature.contacts.presentation.ContactPickers
import com.otus.mail.feature.contacts.presentation.ContactSync
import com.otus.mail.feature.contacts.presentation.ContactsState
import com.otus.mail.feature.contacts.presentation.FrequentContacts

/**
 * Every login's contact cards with the contact changes waiting for the server, and the photos
 * sender avatars take from the device's contacts, which run [onPhotos] when they change.
 * [ownAddresses] tell the user's own cards apart. One per [MailStates].
 */
internal class ContactStates(
    services: MailServices,
    behavior: MailBehaviorState,
    ownAddresses: () -> Set<EmailAddressKey>,
    onPhotos: () -> Unit,
) {
    val avatars =
        ContactAvatarUpdates(services.avatars, services.work, { behavior.behavior.avatars.allAccountPhotos }, onPhotos)

    val cards: ContactsState =
        ContactsState(
            services.contacts,
            ContactSync(services.contacts, services.stores.contacts, services.work),
            services.work,
            ContactPickers(
                services.pickers.attachmentFiles?.let { picker ->
                    services.pickers.savedFiles?.let { VCardFiles(picker, it) }
                },
                services.pickers.avatarPhotos,
            ),
            FrequentContacts(services.mailTraces, services.stores.contacts, services.work),
            avatars,
            ownAddresses,
        )

    /** Loads the contacts and their waiting changes again, as after the accounts change. */
    fun reload() {
        cards.sync.reload()
        cards.refresh()
    }
}

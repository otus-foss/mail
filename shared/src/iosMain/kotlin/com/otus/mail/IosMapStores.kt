package com.otus.mail

import com.otus.mail.core.messages.domain.EmailChangeStore
import com.otus.mail.core.settings.domain.AddressPinStore
import com.otus.mail.feature.contacts.domain.ContactStore
import com.otus.mail.feature.folders.domain.FolderColorStore
import com.otus.mail.feature.folders.domain.FolderNudgeStore
import com.otus.mail.feature.folders.domain.ReadLaterStore
import com.otus.mail.feature.inbox.domain.OutboxStore
import com.otus.storage.StringMapStore
import com.otus.storage.UserDefaultsMap

// Each store keeps one user-defaults dictionary. Changing a key loses what users saved.

class IosAddressPinStore :
    AddressPinStore,
    StringMapStore by UserDefaultsMap("otus.mail.address_pins")

class IosContactStore :
    ContactStore,
    StringMapStore by UserDefaultsMap("otus.mail.contacts")

class IosFolderColorStore :
    FolderColorStore,
    StringMapStore by UserDefaultsMap("otus.mail.folder_colors")

class IosFolderNudgeStore :
    FolderNudgeStore,
    StringMapStore by UserDefaultsMap("otus.mail.folder_nudges")

class IosEmailChangeStore :
    EmailChangeStore,
    StringMapStore by UserDefaultsMap("otus.mail.pending_changes")

class IosOutboxStore :
    OutboxStore,
    StringMapStore by UserDefaultsMap("otus.mail.outbox")

class IosReadLaterStore :
    ReadLaterStore,
    StringMapStore by UserDefaultsMap("otus.mail.read_later_folders")

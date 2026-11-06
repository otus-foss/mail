package com.otus.mail

import android.content.Context
import com.otus.mail.core.messages.domain.EmailChangeStore
import com.otus.mail.core.settings.domain.AddressPinStore
import com.otus.mail.feature.contacts.domain.ContactStore
import com.otus.mail.feature.folders.domain.FolderColorStore
import com.otus.mail.feature.folders.domain.FolderNudgeStore
import com.otus.mail.feature.folders.domain.ReadLaterStore
import com.otus.mail.feature.inbox.domain.OutboxStore
import com.otus.mail.feature.notifications.domain.MailStateStore
import com.otus.storage.PreferenceMap
import com.otus.storage.StringMapStore

// Each store keeps its own shared preferences file. Changing a file name loses what users saved.

class AndroidAddressPinStore(
    context: Context,
) : AddressPinStore,
    StringMapStore by PreferenceMap(context, "otus_mail_address_pins")

class AndroidContactStore(
    context: Context,
) : ContactStore,
    StringMapStore by PreferenceMap(context, "otus_mail_contacts")

class AndroidFolderColorStore(
    context: Context,
) : FolderColorStore,
    StringMapStore by PreferenceMap(context, "otus_mail_folder_colors")

class AndroidFolderNudgeStore(
    context: Context,
) : FolderNudgeStore,
    StringMapStore by PreferenceMap(context, "otus_mail_folder_nudges")

class AndroidMailStateStore(
    context: Context,
) : MailStateStore,
    StringMapStore by PreferenceMap(context, "otus_mail_states")

// The outbox and waiting changes write at once rather than in the background, so they survive the
// app closing right after.
class AndroidOutboxStore(
    context: Context,
) : OutboxStore,
    StringMapStore by PreferenceMap(context, "otus_mail_outbox", commit = true)

class AndroidEmailChangeStore(
    context: Context,
) : EmailChangeStore,
    StringMapStore by PreferenceMap(context, "otus_mail_pending_changes", commit = true)

class AndroidReadLaterStore(
    context: Context,
) : ReadLaterStore,
    StringMapStore by PreferenceMap(context, "otus_mail_read_later")

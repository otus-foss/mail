package com.otusfoss.mail.core.settings.data

import com.otusfoss.core.avatar.IconSource
import com.otusfoss.core.storage.KeyValueEditor
import com.otusfoss.core.storage.KeyValueStore
import com.otusfoss.core.storage.decoded
import com.otusfoss.core.storage.flag
import com.otusfoss.core.storage.int
import com.otusfoss.mail.core.messages.domain.ActionBarLayout
import com.otusfoss.mail.core.messages.domain.ActionBarMode
import com.otusfoss.mail.core.messages.domain.ActionBarPlace
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.RemoteImages
import com.otusfoss.mail.core.settings.domain.ActionBehavior
import com.otusfoss.mail.core.settings.domain.AvatarBehavior
import com.otusfoss.mail.core.settings.domain.FilterLayout
import com.otusfoss.mail.core.settings.domain.InboxContents
import com.otusfoss.mail.core.settings.domain.InboxPill
import com.otusfoss.mail.core.settings.domain.ListBehavior
import com.otusfoss.mail.core.settings.domain.MailBehavior
import com.otusfoss.mail.core.settings.domain.MailBehaviorStore
import com.otusfoss.mail.core.settings.domain.MailListLayout
import com.otusfoss.mail.core.settings.domain.PhotoCleaning
import com.otusfoss.mail.core.settings.domain.PrivacyBehavior
import com.otusfoss.mail.core.settings.domain.QuickViewModule
import com.otusfoss.mail.core.settings.domain.ReadBehavior
import com.otusfoss.mail.core.settings.domain.SidebarBehavior
import com.otusfoss.mail.core.settings.domain.SidebarLayout
import com.otusfoss.mail.core.settings.domain.SidebarPin
import com.otusfoss.mail.core.settings.domain.WriteBehavior
import com.otusfoss.mail.core.settings.domain.decodeNotificationActions
import com.otusfoss.mail.core.settings.domain.encodeNotificationActions
import com.otusfoss.mail.core.storage.TextCodec

/** Keeps [MailBehavior] in [values], one key per setting; each group reads and writes its own keys. */
class KeyValueMailBehaviorStore(
    private val values: KeyValueStore,
) : MailBehaviorStore {
    override fun read(): MailBehavior =
        with(values) {
            val readLater = flag(READ_LATER, MailBehavior().readLater)
            MailBehavior(
                readLater = readLater,
                list = listBehavior(readLater),
                read = readBehavior(),
                write = writeBehavior(),
                actions = actionBehavior(),
                avatars = avatarBehavior(),
                privacy = privacyBehavior(),
                sidebar = sidebarBehavior(),
                updateToasts = flag(UPDATE_TOASTS, MailBehavior().updateToasts),
                lettersAtEnd = flag(LETTERS_AT_END, MailBehavior().lettersAtEnd),
                // A device that never saved it has not finished the first run.
                onboarded = flag(ONBOARDED, false),
            )
        }

    override fun write(behavior: MailBehavior) {
        values.edit {
            put(READ_LATER, behavior.readLater)
            put(behavior.list)
            put(behavior.read)
            put(behavior.write)
            put(behavior.actions)
            put(behavior.avatars)
            put(behavior.privacy)
            put(behavior.sidebar)
            put(UPDATE_TOASTS, behavior.updateToasts)
            put(LETTERS_AT_END, behavior.lettersAtEnd)
            put(ONBOARDED, behavior.onboarded)
        }
    }
}

private fun KeyValueStore.listBehavior(readLater: Boolean): ListBehavior {
    val defaults = ListBehavior()
    return ListBehavior(
        pills = decoded(PILLS, InboxPill::decode) ?: InboxPill.defaults(readLater),
        inbox = decoded(INBOX, defaults.inbox, InboxContents::decode),
        filters = decoded(FILTERS, defaults.filters, FilterLayout::decode),
        quickView = decoded(QUICK_VIEW, defaults.quickView, QuickViewModule::decode),
        layout = decoded(LIST, defaults.layout, MailListLayout::decode),
        categories = inboxCategories(),
    )
}

private fun KeyValueEditor.put(list: ListBehavior) {
    put(PILLS, InboxPill.encode(list.pills))
    put(INBOX, InboxContents.encode(list.inbox))
    put(FILTERS, FilterLayout.encode(list.filters))
    put(QUICK_VIEW, QuickViewModule.encode(list.quickView))
    put(LIST, MailListLayout.encode(list.layout))
    put(list.categories)
}

private fun KeyValueStore.readBehavior(): ReadBehavior {
    val defaults = ReadBehavior()
    return ReadBehavior(
        markReadOnOpen = flag(MARK_READ_ON_OPEN, defaults.markReadOnOpen),
        showQuotes = flag(SHOW_QUOTES, defaults.showQuotes),
        zen = flag(ZEN_READ, defaults.zen),
        remoteImages =
            decodeRemoteImages(string(REMOTE_IMAGES), string(ALWAYS_REMOTE_IMAGES), string(EARLIER_REMOTE_IMAGES))
                ?: defaults.remoteImages,
        allowedSenders = string(ALLOWED_SENDERS)?.let(TextCodec.Lines.decode).orEmpty(),
    )
}

private fun KeyValueEditor.put(read: ReadBehavior) {
    put(MARK_READ_ON_OPEN, read.markReadOnOpen)
    put(SHOW_QUOTES, read.showQuotes)
    put(ZEN_READ, read.zen)
    put(REMOTE_IMAGES, read.remoteImages.storageValue)
    put(ALLOWED_SENDERS, TextCodec.Lines.encode(read.allowedSenders))
}

private fun KeyValueStore.writeBehavior(): WriteBehavior {
    val defaults = WriteBehavior()
    return WriteBehavior(
        recentFrom = string(RECENT_FROM)?.let(TextCodec.Lines.decode).orEmpty(),
        signatureInReplies = flag(SIGNATURE_IN_REPLIES, defaults.signatureInReplies),
        undoSendSeconds = int(UNDO_SEND_SECONDS, defaults.undoSendSeconds),
        photoCleaning = PhotoCleaning.decode(string(PHOTO_CLEANING)),
    )
}

private fun KeyValueEditor.put(write: WriteBehavior) {
    put(RECENT_FROM, TextCodec.Lines.encode(write.recentFrom))
    put(SIGNATURE_IN_REPLIES, write.signatureInReplies)
    put(UNDO_SEND_SECONDS, write.undoSendSeconds)
    put(PHOTO_CLEANING, write.photoCleaning.storageValue)
}

private val RemoteImages.storageValue: String
    get() = name.lowercase()

/**
 * The image setting saved under its own key, [stored], else Never when a key it had before, in
 * [earlier], held Never. Each earlier key held its default for anyone who saved any setting, Known
 * and then Always, so an earlier value is kept only when it was a choice and differs from the
 * default now, which is Never alone. Null for a value [storageValue] cannot have made.
 */
internal fun decodeRemoteImages(
    stored: String?,
    vararg earlier: String?,
): RemoteImages? =
    RemoteImages.entries.firstOrNull { it.storageValue == stored }
        ?: RemoteImages.Never.takeIf { it.storageValue in earlier }

private const val MARK_READ_ON_OPEN = "mark_read_on_open"
private const val READ_LATER = "read_later"
private const val QUICK_VIEW = "quick_view_modules"
private const val PILLS = "inbox_pills"

private const val SHOW_QUOTES = "show_quotes"
private const val UPDATE_TOASTS = "update_toasts"
private const val LETTERS_AT_END = "contact_letters_at_end"
private const val ONBOARDED = "onboarded"
private const val ZEN_READ = "read_zen"
private const val PHOTO_CLEANING = "photo_cleaning"
private const val RECENT_FROM = "recent_from"
private const val SIGNATURE_IN_REPLIES = "signature_in_replies"
private const val UNDO_SEND_SECONDS = "undo_send_seconds"
private const val INBOX = "inbox_contents"
private const val FILTERS = "filter_layout"
private const val LIST = "mail_list"
private const val REMOTE_IMAGES = "remote_images_setting"

// The keys the setting had while images loaded always, and before that, by default.
private const val ALWAYS_REMOTE_IMAGES = "load_remote_images"
private const val EARLIER_REMOTE_IMAGES = "remote_images"
private const val ALLOWED_SENDERS = "allowed_senders"

package com.otus.mail.app

import androidx.compose.runtime.saveable.Saver
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.feature.contacts.domain.CardKey

/**
 * Saves the page as a list of strings: its name, then its arguments. [saved] names every page in a
 * `when` without `else`, so a new page does not compile until it saves; [restored] reads each name
 * back and falls back to the Inbox for anything else.
 */
internal val MailPageSaver: Saver<MailPage, List<String>> =
    Saver(save = { saved(it) }, restore = { restored(it) })

/** A draft's request holds a whole message, so a restored app returns to the page that opened it. */
private fun saved(page: MailPage): List<String> =
    when (page) {
        MailPage.Inbox, MailPage.Settings, MailPage.EmailAccounts, MailPage.Appearance, MailPage.Notifications,
        MailPage.NotificationOverrides, MailPage.Behavior, MailPage.SidebarSettings, MailPage.FilterPanelSettings,
        MailPage.PinnedSettings, MailPage.ThemeSettings, MailPage.MailListSettings, MailPage.ActionBarSettings,
        MailPage.Privacy, MailPage.AllowedSenders, MailPage.Folders, MailPage.Signatures, MailPage.ReadLater,
        MailPage.Conversations, MailPage.Contacts,
        -> listOf(page.toString())

        is MailPage.Compose -> saved(page.parent)

        is MailPage.SignatureEditor -> listOf(SIGNATURE_EDITOR, page.accountId, page.identityId)

        is MailPage.FolderDetail -> listOf(FOLDER_DETAIL, page.accountId, page.folderId, page.sharedIn.orEmpty())

        is MailPage.Folder -> listOf(FOLDER, page.accountId, page.folderId)

        is MailPage.Combined -> listOf(COMBINED, page.role.wire)

        is MailPage.Shared -> listOf(SHARED) + page.key

        is MailPage.Contact -> listOf(CONTACT) + savedKey(page.key)

        is MailPage.ContactEditor -> listOf(CONTACT_EDITOR) + page.key?.let(::savedKey).orEmpty()
    }

private fun restored(saved: List<String>): MailPage =
    when (val name = saved.firstOrNull()) {
        FOLDER -> MailPage.Folder(saved[1], saved[2])
        FOLDER_DETAIL -> MailPage.FolderDetail(saved[1], saved[2], saved[3].ifEmpty { null })
        COMBINED -> combinedRole(saved[1])?.let(MailPage::Combined) ?: MailPage.Inbox
        SHARED -> MailPage.Shared(saved.drop(1))
        CONTACT -> MailPage.Contact(CardKey(saved[1], saved[2], saved[3]))
        CONTACT_EDITOR -> MailPage.ContactEditor(saved.getOrNull(1)?.let { CardKey(it, saved[2], saved[3]) })
        SIGNATURE_EDITOR -> MailPage.SignatureEditor(saved[1], saved[2])
        else -> fixedPages.firstOrNull { it.toString() == name } ?: oldSignatureEditor(name) ?: MailPage.Inbox
    }

/** The pages without arguments, which save as their name. */
private val fixedPages =
    listOf(
        MailPage.Inbox,
        MailPage.Settings,
        MailPage.EmailAccounts,
        MailPage.Appearance,
        MailPage.Notifications,
        MailPage.NotificationOverrides,
        MailPage.Behavior,
        MailPage.SidebarSettings,
        MailPage.FilterPanelSettings,
        MailPage.PinnedSettings,
        MailPage.ThemeSettings,
        MailPage.MailListSettings,
        MailPage.ActionBarSettings,
        MailPage.Privacy,
        MailPage.AllowedSenders,
        MailPage.Folders,
        MailPage.Signatures,
        MailPage.ReadLater,
        MailPage.Conversations,
        MailPage.Contacts,
    )

/** Builds before 2026-11-06 saved a signature editor as its `toString()`. */
private fun oldSignatureEditor(saved: String?): MailPage? =
    saved
        ?.let(OLD_SIGNATURE_EDITOR::matchEntire)
        ?.destructured
        ?.let { (accountId, identityId) -> MailPage.SignatureEditor(accountId, identityId) }

private fun savedKey(key: CardKey): List<String> = listOf(key.loginId, key.accountId, key.cardId)

/**
 * The combined role saved as its JMAP role, or by builds before 2026-11-06 under the old folder
 * names, such as `Spam`; null for anything else.
 */
internal fun combinedRole(saved: String): MailboxRole? =
    (MailboxRole.of(saved) ?: MailboxRole.Junk.takeIf { saved == OLD_SPAM })?.takeIf { it in MailboxRole.combined }

private val OLD_SIGNATURE_EDITOR = Regex("""SignatureEditor\(accountId=(.*), identityId=(.*)\)""")
private const val OLD_SPAM = "Spam"
private const val FOLDER = "Folder"
private const val FOLDER_DETAIL = "FolderDetail"
private const val COMBINED = "Combined"
private const val SHARED = "Shared"
private const val CONTACT = "Contact"
private const val CONTACT_EDITOR = "ContactEditor"
private const val SIGNATURE_EDITOR = "SignatureEditor"

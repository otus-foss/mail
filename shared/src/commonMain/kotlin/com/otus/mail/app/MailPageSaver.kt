package com.otus.mail.app

import androidx.compose.runtime.saveable.Saver
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.feature.contacts.domain.CardKey

/**
 * Saves the page as a list of strings: its name, then its arguments. [saved] names every kind of
 * page in a `when` without `else`, so a new page does not compile until it saves; [restored] reads
 * each name back and falls back to the Inbox for anything else.
 */
internal val MailPageSaver: Saver<MailPage, List<String>> =
    Saver(save = { saved(it) }, restore = { restored(it) })

/** A draft's request holds a whole message, so a restored app returns to the page that opened it. */
private fun saved(page: MailPage): List<String> =
    when (page) {
        is MailPage.Fixed -> listOf(page.toString())
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
        COMBINED -> combined(saved[1])
        SHARED -> MailPage.Shared(saved.drop(1))
        CONTACT -> MailPage.Contact(CardKey(saved[1], saved[2], saved[3]))
        CONTACT_EDITOR -> MailPage.ContactEditor(saved.getOrNull(1)?.let { CardKey(it, saved[2], saved[3]) })
        SIGNATURE_EDITOR -> MailPage.SignatureEditor(saved[1], saved[2])
        else -> MailPage.fixed.firstOrNull { it.toString() == name } ?: MailPage.Inbox
    }

/** The combined page of the role saved; a role without one, such as the inbox, opens the Inbox. */
private fun combined(role: String): MailPage =
    MailboxRole
        .of(role)
        ?.takeIf { it in MailboxRole.combined }
        ?.let(MailPage::Combined) ?: MailPage.Inbox

private fun savedKey(key: CardKey): List<String> = listOf(key.loginId, key.accountId, key.cardId)

private const val FOLDER = "Folder"
private const val FOLDER_DETAIL = "FolderDetail"
private const val COMBINED = "Combined"
private const val SHARED = "Shared"
private const val CONTACT = "Contact"
private const val CONTACT_EDITOR = "ContactEditor"
private const val SIGNATURE_EDITOR = "SignatureEditor"

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

private fun restored(saved: List<String>): MailPage {
    val name = saved.firstOrNull()
    val args = saved.drop(1)
    return when (name) {
        FOLDER -> MailPage.Folder(args[0], args[1])
        FOLDER_DETAIL -> MailPage.FolderDetail(args[0], args[1], args[2].ifEmpty { null })
        COMBINED -> combined(args[0])
        SHARED -> MailPage.Shared(args)
        CONTACT -> MailPage.Contact(restoredKey(args))
        CONTACT_EDITOR -> MailPage.ContactEditor(args.takeIf { it.isNotEmpty() }?.let(::restoredKey))
        SIGNATURE_EDITOR -> MailPage.SignatureEditor(args[0], args[1])
        else -> MailPage.fixed.firstOrNull { it.toString() == name } ?: MailPage.Inbox
    }
}

/** The combined page of the role saved; a role without one, such as the inbox, opens the Inbox. */
private fun combined(role: String): MailPage =
    MailboxRole
        .of(role)
        ?.takeIf { it in MailboxRole.combined }
        ?.let(MailPage::Combined) ?: MailPage.Inbox

private fun savedKey(key: CardKey): List<String> = listOf(key.loginId, key.accountId, key.cardId)

private fun restoredKey(saved: List<String>): CardKey = CardKey(saved[0], saved[1], saved[2])

private const val FOLDER = "Folder"
private const val FOLDER_DETAIL = "FolderDetail"
private const val COMBINED = "Combined"
private const val SHARED = "Shared"
private const val CONTACT = "Contact"
private const val CONTACT_EDITOR = "ContactEditor"
private const val SIGNATURE_EDITOR = "SignatureEditor"

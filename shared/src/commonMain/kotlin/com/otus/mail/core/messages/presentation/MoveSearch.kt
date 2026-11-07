package com.otus.mail.core.messages.presentation

import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.settings.presentation.label
import com.otus.mail.core.settings.presentation.title

/** The system folders among these targets, in the order the move sheet shows them. */
fun List<MoveTarget>.shortcuts(): List<MoveTarget> =
    MailboxRole.entries.mapNotNull { shortcut -> firstOrNull { it.shortcut == shortcut } }

/** The user's own folders among these targets, in tree order. */
fun List<MoveTarget>.ownFolders(): List<MoveTarget> = filter { it.own }

/** Whether the target's name, path, or system folder label contains [query]. */
fun MoveTarget.matchesSearch(query: String): Boolean =
    listOfNotNull(name, label, shortcut?.title).any { it.containsSearch(query) }

/** Whether this text contains [query], ignoring case and the spaces around it; a blank query matches. */
fun String.containsSearch(query: String): Boolean = contains(query.trim(), ignoreCase = true)

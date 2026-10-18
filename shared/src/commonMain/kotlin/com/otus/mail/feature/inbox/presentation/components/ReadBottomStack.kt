package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.AlsoInStrip
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailingList
import com.otus.mail.feature.inbox.domain.MessageContent
import com.otus.mail.feature.inbox.domain.MessageFolder
import com.otus.mail.feature.inbox.domain.folderHint
import com.otus.mail.feature.inbox.presentation.PageBody
import com.otus.mail.feature.inbox.presentation.ReadSheet
import com.otus.mail.feature.inbox.presentation.ReadState
import com.otus.mail.feature.inbox.presentation.read.subtitle

/**
 * The strips above the action bar, from the bottom up: one context strip, then the page's files.
 * A conversation shows its thread strip; a single message its newsletter strip when it can be left,
 * else the folders it is also in, else nothing.
 */
@Composable
internal fun ReadBottomStack(
    state: ReadState,
    current: Int,
    source: ReadStripSource,
    actions: ReadStripActions,
    modifier: Modifier = Modifier,
) {
    val page = state.pages.getOrNull(current) ?: return
    val content = (state.body(page) as? PageBody.Loaded)?.content
    Column(modifier.fillMaxWidth(), Arrangement.spacedBy(PageTokens.rowGap)) {
        content?.attachments?.takeIf { it.isNotEmpty() }?.let { files ->
            ReadAttachmentStrip(
                files,
                state.filesOpen,
                { state.filesOpen = !state.filesOpen },
                actions.files(page, files),
            )
        }
        ContextStrip(state, current, source, content, actions)
    }
}

@Composable
private fun ContextStrip(
    state: ReadState,
    current: Int,
    source: ReadStripSource,
    content: MessageContent?,
    actions: ReadStripActions,
) {
    val row = source.row
    val page = state.pages[current]
    val unsubscribe = content?.unsubscribe
    when {
        state.pages.size > 1 -> {
            val folders = folderHint(row.conversationMailboxes(), source.folders, source.cameFrom)
            val open = { state.threadOpen = true }
            ReadThreadStrip(state.pages, current, source.date, folders, row.attributes.flagged, open)
        }

        unsubscribe != null -> {
            val list by produceState<MailingList?>(null, page.id) { value = actions.list(page) }
            val title = content.listName ?: list?.name ?: page.sender.name.ifBlank { page.sender.address }
            ReadNewsletterStrip(
                title,
                list?.subtitle() ?: "Mailing list",
                state.newsletter(page),
                list?.olderIds?.size ?: 0,
                onUnsubscribe = { actions.unsubscribe(page, unsubscribe, title) },
                onArchive = { list?.let { actions.archiveOlder(page, it) } },
            )
        }

        else -> {
            val folders = folderHint(page.location.mailboxIds, source.folders, source.cameFrom).folders
            if (folders.isNotEmpty()) AlsoInStrip(folderNames(folders), { state.sheet = ReadSheet.Folders })
        }
    }
}

/** Every folder an email of the row's conversation is in. */
internal fun MailMessage.conversationMailboxes(): Set<String> =
    location.mailboxIds + conversation?.members?.flatMap { it.mailboxIds }.orEmpty()

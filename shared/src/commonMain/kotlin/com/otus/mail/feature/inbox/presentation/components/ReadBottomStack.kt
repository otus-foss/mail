package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.AlsoInStrip
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.feature.inbox.domain.MailingList
import com.otus.mail.feature.inbox.domain.MessageContent
import com.otus.mail.feature.inbox.domain.MessageFolder
import com.otus.mail.feature.inbox.domain.folderHint
import com.otus.mail.feature.inbox.domain.inJunk
import com.otus.mail.feature.inbox.presentation.PageBody
import com.otus.mail.feature.inbox.presentation.ReadSheet
import com.otus.mail.feature.inbox.presentation.ReadState
import com.otus.mail.feature.inbox.presentation.read.shortLabel
import com.otus.mail.feature.inbox.presentation.read.subtitle

/**
 * The strips above the action bar, from the bottom up: one context strip, then the page's files.
 * A conversation shows its thread strip; a single message its newsletter strip when it can be left
 * and is not spam, else the folders it is also in, else nothing. Until the pages load, the row's
 * message count tells a conversation from a single message, so the strip does not change kind.
 * The thread strip follows the pages as [slide] turns them, and a drag on it turns them too.
 */
@Composable
internal fun ReadBottomStack(
    state: ReadState,
    current: Int,
    source: ReadStripSource,
    actions: ReadStripActions,
    slide: StripSlide,
    modifier: Modifier = Modifier,
) {
    val page = state.pages.getOrNull(current) ?: return
    val content = (state.bodies.body(page) as? PageBody.Loaded)?.content
    Column(modifier.fillMaxWidth(), Arrangement.spacedBy(PageTokens.rowGap)) {
        content?.attachments?.takeIf { it.isNotEmpty() }?.let { files ->
            LaunchedEffect(page.id, files) { state.thumbnails.load(page, files) { actions.thumbnail(page, it) } }
            ReadAttachmentStrip(
                files,
                state.filesOpen(page),
                { state.toggleFiles(page) },
                actions.files(page, files),
                { state.thumbnails.of(page, it) },
            )
        }
        ContextStrip(state, current, source, content, actions, slide)
    }
}

@Composable
private fun ContextStrip(
    state: ReadState,
    current: Int,
    source: ReadStripSource,
    content: MessageContent?,
    actions: ReadStripActions,
    slide: StripSlide,
) {
    val row = source.row
    val page = state.pages[current]
    val unsubscribe = content?.unsubscribe?.takeUnless { inJunk(page.location.mailboxIds, source.folders) }
    val count = if (state.loaded) state.pages.size else row.conversation?.messageCount ?: 1
    when {
        count > 1 -> {
            val everyFolder =
                state
                    .mailboxes(row)
                    .values
                    .flatten()
                    .toSet()
            val folders = folderHint(everyFolder, source.folders, source.cameFrom)
            val open = { state.threadOpen = true }
            ReadThreadCarousel(state.pages.size, current, slide.position, slide.swipe) { index ->
                val shown = state.pages[index]
                val position = ThreadPosition(shown, index, count, shown.received.shortLabel(source.today))
                ReadThreadStrip(position, folders, row.attributes.flagged, open)
            }
        }

        unsubscribe != null -> {
            val list by produceState<MailingList?>(null, page.id) { value = actions.list(page) }
            val title = content.listName ?: list?.name ?: page.sender.name.ifBlank { page.sender.address }
            ReadNewsletterStrip(
                title,
                list?.subtitle() ?: "Mailing list",
                actions.step(page),
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

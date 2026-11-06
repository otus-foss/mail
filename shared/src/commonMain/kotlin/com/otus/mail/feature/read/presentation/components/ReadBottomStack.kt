package com.otus.mail.feature.read.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.otus.designsystem.mail.read.components.AlsoInStrip
import com.otus.designsystem.shared.rows.components.rememberUnfold
import com.otus.designsystem.theme.tokens.content.ReadTokens
import com.otus.designsystem.theme.tokens.layout.PageTokens
import com.otus.mail.core.messages.domain.MailAttachment
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailingList
import com.otus.mail.core.messages.domain.MessageContent
import com.otus.mail.core.messages.domain.folderHint
import com.otus.mail.core.messages.domain.inJunk
import com.otus.mail.core.messages.domain.shownName
import com.otus.mail.core.messages.presentation.shortLabel
import com.otus.mail.feature.read.presentation.PageBody
import com.otus.mail.feature.read.presentation.ReadSheet
import com.otus.mail.feature.read.presentation.ReadState
import com.otus.mail.feature.read.presentation.subtitle
import com.otus.settings.presentation.components.LocalDateFormatter

/**
 * The strips above the action bar, from the bottom up: one context strip, then the page's files.
 * A conversation shows its thread strip; a single message its newsletter strip when it can be left
 * and is not spam, else the folders it is also in, else nothing. Until the pages load, the row's
 * message count tells a conversation from a single message, so the strip does not change kind.
 * The thread strip follows the pages as [slide] turns them, and a drag on it turns them too. A
 * swipe up opens the thread strip's list, the files, or the folders sheet, and a swipe down folds
 * the files. [onFoldedHeight] gets the stack's height with the files folded, which the page leaves
 * room for under its text.
 */
@Composable
internal fun ReadBottomStack(
    state: ReadState,
    current: Int,
    source: ReadStripSource,
    actions: ReadStripActions,
    slide: StripGestures,
    modifier: Modifier = Modifier,
    onFoldedHeight: (Dp) -> Unit = {},
) {
    val page = state.pages.getOrNull(current) ?: return
    val content = (state.bodies.body(page) as? PageBody.Loaded)?.content
    val density = LocalDensity.current
    var height by remember { mutableIntStateOf(0) }
    var filesHeight by remember { mutableIntStateOf(0) }
    val files = content?.attachments?.takeIf { it.isNotEmpty() }
    // The files count as folded, so the page's room under its text stays put while they unfold over it.
    val folded = with(density) { (height - if (files != null) filesHeight else 0).toDp() }
    SideEffect { onFoldedHeight(folded + if (files != null) ReadTokens.attachmentHeight else 0.dp) }
    Column(modifier.fillMaxWidth().onSizeChanged { height = it.height }, Arrangement.spacedBy(PageTokens.rowGap)) {
        files?.let {
            LaunchedEffect(page.id, it) { state.thumbnails.load(page, it) { file -> actions.thumbnail(page, file) } }
            Box(Modifier.onSizeChanged { size -> filesHeight = size.height }) {
                key(page.id) { FilesStrip(state, page, it, actions) }
            }
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
    slide: StripGestures,
) {
    val row = source.row
    val page = state.pages[current]
    val unsubscribe = content?.unsubscribe?.takeUnless { inJunk(page.location.mailboxIds, source.folders) }
    val count = state.messageCount(row)
    when {
        count > 1 -> {
            ConversationStrip(state, current, source, slide)
        }

        unsubscribe != null -> {
            val list by produceState<MailingList?>(null, page.id) { value = actions.list(page) }
            val title = content.listName ?: list?.name ?: page.sender.shownName
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
            if (folders.isNotEmpty()) {
                AlsoInStrip(folderNames(folders), { state.sheet = ReadSheet.Folders }, slide.folders)
            }
        }
    }
}

/** The page's files, which a tap or a drag unfolds; the drag moves them with the finger. */
@Composable
private fun FilesStrip(
    state: ReadState,
    page: MailMessage,
    files: List<MailAttachment>,
    actions: ReadStripActions,
) {
    val open = state.filesOpen(page)
    val unfold = rememberUnfold(open)
    LaunchedEffect(open) { unfold.animateTo(open) }
    val drag = rememberUnfoldDrag(unfold) { if (it != state.filesOpen(page)) state.toggleFiles(page) }
    LaunchedEffect(page.id, files) { actions.loadKept(page, files) }
    ReadAttachmentStrip(
        files,
        unfold,
        { state.toggleFiles(page) },
        actions.files(page, files),
        { state.thumbnails.of(page, it) },
        rememberFoldSwipe(drag),
        kept = { actions.isKept(page, it) },
    )
}

/** How many messages [row]'s Read view pages through; until the pages load, the row's count says. */
private fun ReadState.messageCount(row: MailMessage): Int =
    if (loaded) pages.size else row.conversation?.messageCount ?: 1

/**
 * The thread strip for the conversation at [current] of [source]'s row, its end naming the
 * conversation's other folders. A tap opens its message list.
 */
@Composable
internal fun ConversationStrip(
    state: ReadState,
    current: Int,
    source: ReadStripSource,
    slide: StripGestures,
) {
    val row = source.row
    val page = state.pages[current]
    val count = state.messageCount(row)
    val everyFolder =
        state
            .mailboxes(row)
            .values
            .flatten()
            .toSet()
    val folders = folderHint(everyFolder, source.folders, source.cameFrom)
    val open = { state.threadOpen = true }
    // Until the pages load, the row's count may name pages not here yet; the strip never shows them.
    val dates = LocalDateFormatter.current
    val pageAt = { index: Int ->
        val shown = state.pages.getOrNull(index) ?: page
        ThreadPosition(shown, index, count, dates.shortLabel(shown.received, source.today))
    }
    ReadThreadStrip(pageAt, current, count, folders, row.attributes.flagged, open, slide)
}

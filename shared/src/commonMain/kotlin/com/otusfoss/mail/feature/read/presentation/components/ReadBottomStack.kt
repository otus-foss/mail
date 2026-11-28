package com.otusfoss.mail.feature.read.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.otusfoss.core.designsystem.mail.read.components.AlsoInStrip
import com.otusfoss.core.designsystem.theme.tokens.layout.PageTokens
import com.otusfoss.core.settings.presentation.components.LocalDateFormatter
import com.otusfoss.mail.core.codes.domain.SignInCode
import com.otusfoss.mail.core.codes.presentation.components.rememberCodeCopy
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailingList
import com.otusfoss.mail.core.messages.domain.MessageContent
import com.otusfoss.mail.core.messages.domain.folderHint
import com.otusfoss.mail.core.messages.domain.inJunk
import com.otusfoss.mail.core.messages.domain.shownName
import com.otusfoss.mail.core.messages.presentation.shortLabel
import com.otusfoss.mail.feature.read.presentation.PageBody
import com.otusfoss.mail.feature.read.presentation.ReadSheet
import com.otusfoss.mail.feature.read.presentation.ReadState
import com.otusfoss.mail.feature.read.presentation.subtitle

/**
 * The context strip above the action bar; the page's files are in the control panel. A page with
 * a one-time code shows its code strip, with the source's Delete. Otherwise a
 * conversation shows its thread strip; a single message its newsletter strip when it can be left
 * and is not spam, else the folders it is also in, else nothing. Until the pages load, the row's
 * message count tells a conversation from a single message, so the strip does not change kind
 * until the page's body shows a code, whose strip then takes the thread strip's place on that page.
 * The thread strip follows the pages as [slide] turns them, and a drag on it turns them too. A
 * swipe up opens the thread strip's list or the folders sheet. [onFoldedHeight] gets the stack's
 * height, which the page leaves room for under its text.
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
    Column(
        modifier.fillMaxWidth().onSizeChanged { onFoldedHeight(with(density) { it.height.toDp() }) },
        Arrangement.spacedBy(PageTokens.rowGap),
    ) {
        val code = content?.signInCode
        if (code != null) {
            CodeStrip(page, code, source.onDelete)
        } else {
            ContextStrip(state, current, source, content, actions, slide)
        }
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
                onUnsubscribe = { actions.unsubscribe(page, unsubscribe) },
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

/** The [page]'s one-time [code], copied without its spaces and kept out of the system's copy preview. */
@Composable
private fun CodeStrip(
    page: MailMessage,
    code: SignInCode,
    onDelete: (() -> Unit)?,
) {
    val copyCode = rememberCodeCopy()
    var copied by remember(page.id) { mutableStateOf(false) }
    val copy: () -> Unit = {
        copied = true
        copyCode(code)
    }
    ReadCodeStrip(code, copied, copy, onDelete)
}

/** How many messages [row]'s Read view pages through; until the pages load, the row's count says. */
internal fun ReadState.messageCount(row: MailMessage): Int =
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

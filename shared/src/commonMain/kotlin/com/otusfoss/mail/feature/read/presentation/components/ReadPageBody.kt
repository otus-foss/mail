package com.otusfoss.mail.feature.read.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalUriHandler
import com.otusfoss.core.designsystem.mail.read.components.QuoteFold
import com.otusfoss.core.designsystem.mail.read.components.QuotedLevel
import com.otusfoss.core.designsystem.mail.read.elements.HtmlView
import com.otusfoss.core.designsystem.shared.people.components.NamedAvatar
import com.otusfoss.core.designsystem.theme.colors.OtusColors
import com.otusfoss.core.designsystem.theme.tokens.content.ReadTokens
import com.otusfoss.core.designsystem.theme.tokens.layout.PageTokens
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MessageHtml
import com.otusfoss.mail.core.messages.presentation.components.chipAvatar
import com.otusfoss.mail.feature.read.presentation.PageBody
import com.otusfoss.mail.feature.read.presentation.PlainPage
import com.otusfoss.mail.feature.read.presentation.QuoteLevelText
import com.otusfoss.mail.feature.read.presentation.plainPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One page of the Read view: [top], such as the images banner and the sender details, floats
 * above the [message]'s text, which scrolls up behind it and behind whatever floats over the
 * page's edges, leaving [insets] of room at its top and bottom so it can scroll clear of them. Its
 * quoted part folds under it as [quotes] says, and levels quoted inside it fold to a row each.
 * While the [body] loads, the preview shows; when it failed, the preview stays with a note.
 */
@Composable
internal fun ReadPageBody(
    message: MailMessage,
    body: PageBody,
    images: PageImages,
    modifier: Modifier = Modifier,
    quotes: PageQuotes = PageQuotes(),
    insets: PaddingValues = PaddingValues(),
    top: @Composable ColumnScope.() -> Unit = {},
) {
    val textInset = Modifier.padding(horizontal = textSide)
    // The parts on top are measured first, so the text gets its room before it first draws:
    // a new room would load an HTML page again.
    SubcomposeLayout(modifier) { constraints ->
        val insetTop = insets.calculateTopPadding().roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val tops =
            subcompose(TOP) {
                Column(textInset.padding(top = PageTokens.contentInset), spacing, content = top)
            }.map { it.measure(loose) }
        val topHeight = tops.maxOfOrNull { it.height } ?: 0
        val room =
            PaddingValues(
                start = textSide,
                end = textSide,
                top = (insetTop + topHeight).toDp() + ReadTokens.partGap,
                bottom = insets.calculateBottomPadding(),
            )
        val bodies =
            subcompose(BODY) {
                MessageText(message, body, images, quotes, room, textInset)
            }.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            bodies.forEach { it.place(0, 0) }
            tops.forEach { it.place(0, insetTop) }
        }
    }
}

/** The message's HTML, or else its text, with [room] around it to scroll clear of what floats over it. */
@Composable
private fun MessageText(
    message: MailMessage,
    body: PageBody,
    images: PageImages,
    quotes: PageQuotes,
    room: PaddingValues,
    textInset: Modifier,
) {
    val content = (body as? PageBody.Loaded)?.content
    val html = content?.html
    if (html != null) {
        // Designed HTML runs edge to edge; HtmlView keeps only HTML without a layout off the sides.
        HtmlBody(html, images, quotes.hide, room, Modifier.fillMaxSize())
    } else {
        val text = content?.text?.ifBlank { null } ?: message.preview.ifBlank { message.body }
        val note =
            when {
                body is PageBody.Failed -> "Couldn't load the full message."
                content?.truncated == true -> "Only the start of this message was loaded."
                else -> null
            }
        PlainBody(text, note, quotes, room, Modifier.fillMaxSize().then(textInset))
    }
}

/**
 * The message's HTML with its quote folded. A short message splits at once; a long one takes a
 * while, so it splits off the main thread, and the page shows once it is done. A fold made for
 * other HTML never shows.
 */
@Composable
private fun HtmlBody(
    html: MessageHtml,
    images: PageImages,
    hideQuote: Boolean,
    room: PaddingValues,
    modifier: Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val quick = remember(html.html) { html.html.takeIf { it.length <= QUICK_FOLD_CHARS }?.let(::htmlFold) }
    val slow by produceState<Pair<String, HtmlFold>?>(null, html.html) {
        if (quick == null) value = html.html to withContext(Dispatchers.Default) { htmlFold(html.html) }
    }
    val shown = quick ?: slow?.takeIf { it.first == html.html }?.second ?: return
    HtmlView(
        shown.main,
        images.load,
        { url -> openLink(uriHandler::openUri, url) },
        modifier.fillMaxWidth(),
        onShowRemote = images.onShow,
        quote = shown.quote?.takeUnless { hideQuote },
        insets = room,
    )
}

/**
 * The message's text with its quote folded. As with HTML, a long text splits off the main thread
 * and the page shows once it is done; its pieces lay out only as they scroll into view.
 */
@Composable
private fun PlainBody(
    text: String,
    note: String?,
    quotes: PageQuotes,
    room: PaddingValues,
    modifier: Modifier,
) {
    val quick = remember(text) { text.takeIf { it.length <= QUICK_FOLD_CHARS }?.let(::plainPage) }
    val slow by produceState<Pair<String, PlainPage>?>(null, text) {
        if (quick == null) value = text to withContext(Dispatchers.Default) { plainPage(text) }
    }
    val page = quick ?: slow?.takeIf { it.first == text }?.second ?: return
    var expanded by remember(text) { mutableStateOf(false) }
    val levels =
        page.quoteLevels
            .takeUnless { quotes.hide }
            .orEmpty()
            .map { it.shown(quotes.showPerson) }
    val partTop = Modifier.padding(top = ReadTokens.partGap)
    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding =
            PaddingValues(
                top = room.calculateTopPadding(),
                bottom = PageTokens.contentInset + room.calculateBottomPadding(),
            ),
    ) {
        items(page.chunks) { Text(it) }
        if (levels.isNotEmpty()) {
            item { QuoteFold(levels.first(), expanded, { expanded = !expanded }, partTop, deeper = levels.drop(1)) }
        }
        note?.let { item { Text(it, partTop, color = OtusColors.text2) } }
    }
}

/** This level as the fold shows it: the person's pill, when the quote names them, runs [showPerson]. */
private fun QuoteLevelText.shown(showPerson: (address: String) -> (() -> Unit)?): QuotedLevel =
    QuotedLevel(
        header?.let { NamedAvatar(it.name, chipAvatar(null, it.name, it.address.orEmpty())) },
        header?.date.orEmpty(),
        text,
        header?.address?.let(showPerson),
        header?.fullDate.orEmpty(),
    )

/** Opens [url] outside the app; a link no installed app opens does nothing. */
internal fun openLink(
    open: (String) -> Unit,
    url: String,
) {
    try {
        open(url)
    } catch (expected: IllegalArgumentException) {
        // No installed app opens this link, so the tap does nothing.
    }
}

private val textSide = PageTokens.pageInset + PageTokens.textInset

private val spacing = Arrangement.spacedBy(ReadTokens.partGap)

private const val TOP = "top"
private const val BODY = "body"

/** A body up to this many characters splits on the main thread, too quickly to show a blank page. */
private const val QUICK_FOLD_CHARS = 16_000

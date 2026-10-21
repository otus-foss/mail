package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalUriHandler
import com.otus.designsystem.components.NamedAvatar
import com.otus.designsystem.components.QuoteFold
import com.otus.designsystem.components.QuotedLevel
import com.otus.designsystem.elements.HtmlQuote
import com.otus.designsystem.elements.HtmlQuoteLevel
import com.otus.designsystem.elements.HtmlView
import com.otus.designsystem.theme.InboxTokens
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageHtml
import com.otus.mail.feature.inbox.presentation.PageBody
import com.otus.mail.feature.inbox.presentation.read.HtmlQuoteSplit
import com.otus.mail.feature.inbox.presentation.read.QuoteHeader
import com.otus.mail.feature.inbox.presentation.read.QuoteLevelText
import com.otus.mail.feature.inbox.presentation.read.levels
import com.otus.mail.feature.inbox.presentation.read.markupText
import com.otus.mail.feature.inbox.presentation.read.quoteLevels
import com.otus.mail.feature.inbox.presentation.read.splitHtmlQuote
import com.otus.mail.feature.inbox.presentation.read.splitPlainQuote

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
    val textInset = Modifier.padding(horizontal = PageTokens.pageInset + PageTokens.textInset)
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
                top = (insetTop + topHeight).toDp() + InboxTokens.itemGap,
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

/** The message's HTML edge to edge, or else its text, with [room] around it to scroll clear of what floats over it. */
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
        // A mail's HTML lays out its own margins, so it runs edge to edge.
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

@Composable
private fun HtmlBody(
    html: MessageHtml,
    images: PageImages,
    hideQuote: Boolean,
    room: PaddingValues,
    modifier: Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val split = remember(html.html) { splitHtmlQuote(html.html) }
    val quote = remember(split, hideQuote) { split?.takeUnless { hideQuote }?.let(::htmlQuote) }
    HtmlView(
        split?.main ?: html.html,
        images.load,
        { url -> openLink(uriHandler::openUri, url) },
        modifier.fillMaxWidth(),
        onShowRemote = images.onShow,
        quote = quote,
        insets = room,
    )
}

@Composable
private fun PlainBody(
    text: String,
    note: String?,
    quotes: PageQuotes,
    room: PaddingValues,
    modifier: Modifier,
) {
    val split = remember(text) { splitPlainQuote(text) }
    var expanded by remember(text) { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(
                top = room.calculateTopPadding(),
                bottom = PageTokens.contentInset + room.calculateBottomPadding(),
            ),
        spacing,
    ) {
        Text(split?.body ?: text)
        split?.takeUnless { quotes.hide }?.let { quote ->
            val levels = remember(quote) { quote.levels() }.map { it.shown(quotes.showPerson) }
            QuoteFold(levels.first(), expanded, onToggle = { expanded = !expanded }, deeper = levels.drop(1))
        }
        note?.let { Text(it, color = OtusColors.text2) }
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

/**
 * The fold for [split]'s quote. When its text quotes earlier mail in turn, as `>` lines or "On …
 * wrote:" lines, it shows as text with each level folded to a row; else as the sender's markup.
 */
private fun htmlQuote(split: HtmlQuoteSplit): HtmlQuote {
    val levels = quoteLevels(split.header, markupText(split.quote))
    return if (levels.size > 1) {
        HtmlQuote.ofText(levels.first().htmlLevel(), levels.drop(1).map { it.htmlLevel() })
    } else {
        val header = split.header
        HtmlQuote(
            header.name(),
            header.initials(),
            header?.date.orEmpty(),
            split.quote,
            fullDate = header?.fullDate.orEmpty(),
        )
    }
}

private fun QuoteLevelText.htmlLevel(): HtmlQuoteLevel =
    HtmlQuoteLevel(header.name(), header.initials(), header?.date.orEmpty(), text, header?.fullDate.orEmpty())

/** Blank without a header, so the fold shows no person. */
private fun QuoteHeader?.name(): String = this?.name.orEmpty()

private fun QuoteHeader?.initials(): String =
    this?.let { chipAvatar(null, it.name, it.address.orEmpty()).initials }.orEmpty()

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

private val spacing = Arrangement.spacedBy(InboxTokens.itemGap)

private const val TOP = "top"
private const val BODY = "body"

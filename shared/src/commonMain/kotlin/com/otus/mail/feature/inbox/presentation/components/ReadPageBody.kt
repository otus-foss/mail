package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.platform.LocalUriHandler
import com.otus.designsystem.components.NamedAvatar
import com.otus.designsystem.components.QuoteFold
import com.otus.designsystem.elements.HtmlQuote
import com.otus.designsystem.elements.HtmlView
import com.otus.designsystem.theme.InboxTokens
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageHtml
import com.otus.mail.feature.inbox.presentation.PageBody
import com.otus.mail.feature.inbox.presentation.read.QuoteHeader
import com.otus.mail.feature.inbox.presentation.read.splitHtmlQuote
import com.otus.mail.feature.inbox.presentation.read.splitPlainQuote

/**
 * One page of the Read view: [top], such as the images banner and the sender details, stays put
 * above the [message]'s text, which scrolls. Its quoted part folds under it, and the quoted
 * person's pill runs what [showPerson] gives for their address, if anything. While the [body]
 * loads, the preview shows; when it failed, the preview stays with a note.
 */
@Composable
internal fun ReadPageBody(
    message: MailMessage,
    body: PageBody,
    images: PageImages,
    modifier: Modifier = Modifier,
    showPerson: (address: String) -> (() -> Unit)? = { null },
    top: @Composable ColumnScope.() -> Unit = {},
) {
    val content = (body as? PageBody.Loaded)?.content
    Column(modifier.padding(horizontal = PageTokens.textInset), spacing) {
        Column(Modifier.padding(top = PageTokens.contentInset), spacing, content = top)
        val html = content?.html
        if (html != null) {
            HtmlBody(html, images, Modifier.weight(1f))
        } else {
            val text = content?.text?.ifBlank { null } ?: message.preview.ifBlank { message.body }
            val note =
                when {
                    body is PageBody.Failed -> "Couldn't load the full message."
                    content?.truncated == true -> "Only the start of this message was loaded."
                    else -> null
                }
            PlainBody(text, note, showPerson, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HtmlBody(
    html: MessageHtml,
    images: PageImages,
    modifier: Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val split = remember(html.html) { splitHtmlQuote(html.html) }
    val quote = split?.let { HtmlQuote(it.header.name(), it.header.initials(), it.header?.date.orEmpty(), it.quote) }
    HtmlView(
        split?.main ?: html.html,
        images.load,
        { url -> openLink(uriHandler::openUri, url) },
        modifier.fillMaxWidth(),
        onShowRemote = images.onShow,
        quote = quote,
    )
}

@Composable
private fun PlainBody(
    text: String,
    note: String?,
    showPerson: (address: String) -> (() -> Unit)?,
    modifier: Modifier,
) {
    val split = remember(text) { splitPlainQuote(text) }
    var expanded by remember(text) { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = PageTokens.contentInset),
        spacing,
    ) {
        Text(split?.body ?: text)
        split?.let { quote ->
            val header = quote.header
            QuoteFold(
                header?.let { NamedAvatar(it.name, chipAvatar(null, it.name, it.address.orEmpty())) },
                header?.date.orEmpty(),
                quote.quoted,
                expanded,
                onToggle = { expanded = !expanded },
                onPerson = header?.address?.let(showPerson),
            )
        }
        note?.let { Text(it, color = OtusColors.text2) }
    }
}

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

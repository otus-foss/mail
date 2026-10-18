package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.otus.designsystem.elements.HtmlView
import com.otus.designsystem.elements.RowButton
import com.otus.designsystem.theme.InboxTokens
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageHtml

/**
 * The open message's sender, body, and attachments above [barPadding]. The message's text shows
 * at once, first its preview and then its full text; an [html] body replaces the text once it
 * loads. Its remote images stay hidden until the user shows them, and its links open outside the app.
 */
@Composable
fun MessageBody(
    message: MailMessage,
    html: MessageHtml?,
    barPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val inset = Modifier.padding(horizontal = PageTokens.textInset, vertical = PageTokens.contentInset)
    if (html == null) {
        Column(modifier.verticalScroll(rememberScrollState()).padding(barPadding).then(inset), spacing) {
            SenderLine(message)
            Text(message.body)
            MessageFooter(message, message.bodyTruncated)
        }
    } else {
        Column(modifier.padding(barPadding).then(inset), spacing) {
            SenderLine(message)
            HtmlBody(html, message.id, Modifier.weight(1f))
            MessageFooter(message, html.truncated)
        }
    }
}

@Composable
private fun HtmlBody(
    html: MessageHtml,
    messageId: String,
    modifier: Modifier,
) {
    var showImages by remember(messageId) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    if (html.remoteContent && !showImages) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Images are hidden.", Modifier.weight(1f), color = OtusColors.text2)
            RowButton("Show images", { showImages = true })
        }
    }
    HtmlView(html.html, showImages, { url -> openLink(uriHandler::openUri, url) }, modifier)
}

@Composable
private fun SenderLine(message: MailMessage) {
    Text("${message.sender.name} <${message.sender.address}>", color = OtusColors.text2)
}

@Composable
private fun MessageFooter(
    message: MailMessage,
    truncated: Boolean,
) {
    if (truncated) {
        Text("Only the start of this message was loaded.", color = OtusColors.text2)
    }
    if (message.attributes.attachments.isNotEmpty()) {
        Text("Attachments: ${message.attributes.attachments.joinToString()}", color = OtusColors.text2)
    }
}

private fun openLink(
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

package com.otus.mail.feature.read.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import com.otus.designsystem.shared.overlays.components.SheetFrame
import com.otus.designsystem.shared.rows.elements.Caption
import com.otus.designsystem.theme.colors.OtusColors
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.feature.read.domain.MailHeader

/**
 * View details: [message]'s raw headers from [loadHeaders], each name in `text3` over its value,
 * which the user can select and copy. It says so while they load and when they could not be loaded.
 */
@Composable
fun MessageDetailsSheet(
    message: MailMessage,
    loadHeaders: suspend () -> List<MailHeader>?,
    onDismiss: () -> Unit,
) {
    val headers by produceState<HeadersLoad>(HeadersLoad.Loading, message.id) {
        value = loadHeaders()?.let(HeadersLoad::Loaded) ?: HeadersLoad.Failed
    }
    SheetFrame("Details", onDismiss, done = null, closeButton = true) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = SheetTokens.inset, end = SheetTokens.inset, bottom = SheetTokens.inset),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            when (val load = headers) {
                HeadersLoad.Loading -> Caption("Loading headers…", heading = false)
                HeadersLoad.Failed -> Caption("Couldn't load the headers. Check your connection.", heading = false)
                is HeadersLoad.Loaded -> HeaderLines(load.headers)
            }
        }
    }
}

@Composable
private fun HeaderLines(headers: List<MailHeader>) {
    if (headers.isEmpty()) {
        Caption("This message has no headers.", heading = false)
        return
    }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(SheetTokens.gap)) {
            headers.forEach { header -> HeaderLine(header) }
        }
    }
}

@Composable
private fun HeaderLine(header: MailHeader) {
    Column {
        Text(header.name, color = OtusColors.text3, fontSize = SheetTokens.detailSize)
        Text(header.value.replace(FOLDED, " ").trim(), fontSize = SheetTokens.detailSize)
    }
}

private sealed interface HeadersLoad {
    data object Loading : HeadersLoad

    data object Failed : HeadersLoad

    data class Loaded(
        val headers: List<MailHeader>,
    ) : HeadersLoad
}

/** A header continued on the next line, which reads as one line (RFC 5322, section 2.2.3). */
private val FOLDED = Regex("\\r?\\n[ \\t]+")

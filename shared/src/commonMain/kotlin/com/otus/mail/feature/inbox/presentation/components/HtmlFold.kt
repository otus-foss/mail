package com.otus.mail.feature.inbox.presentation.components

import com.otus.designsystem.mail.read.html.HtmlQuote
import com.otus.designsystem.mail.read.html.HtmlQuoteLevel
import com.otus.mail.core.messages.presentation.components.chipAvatar
import com.otus.mail.feature.inbox.presentation.read.HtmlQuoteSplit
import com.otus.mail.feature.inbox.presentation.read.QuoteHeader
import com.otus.mail.feature.inbox.presentation.read.QuoteLevelText
import com.otus.mail.feature.inbox.presentation.read.markupText
import com.otus.mail.feature.inbox.presentation.read.quoteLevels
import com.otus.mail.feature.inbox.presentation.read.splitHtmlQuote

/** A message's HTML as the Read view shows it: the [main] part, with its [quote] folded under it. */
internal class HtmlFold(
    val main: String,
    val quote: HtmlQuote?,
)

/** [html] split at its quote for the Read view. It reads the whole message, so run it off the main thread. */
internal fun htmlFold(html: String): HtmlFold {
    val split = splitHtmlQuote(html)
    return HtmlFold(split?.main ?: html, split?.let(::htmlQuote))
}

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

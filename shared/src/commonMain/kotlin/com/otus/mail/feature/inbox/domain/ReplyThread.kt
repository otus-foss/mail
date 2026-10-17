package com.otus.mail.feature.inbox.domain

import kotlinx.datetime.LocalDate

/** A message earlier in the thread a reply answers: who sent it, when, and the start of its text. */
data class ThreadMessage(
    val emailId: String,
    val sender: MailSender,
    val date: LocalDate,
    val preview: String,
)

/** The messages of a thread, read from the server rather than stored by the app. */
fun interface ThreadHistory {
    /**
     * The messages up to and including [emailId] in its thread, newest first, without drafts.
     * Empty when the server cannot be read.
     */
    suspend fun earlier(
        accountId: String,
        emailId: String,
    ): List<ThreadMessage>
}

/** How much of the thread a reply quotes. */
enum class QuoteMode { Thread, Latest, None }

/**
 * The quote a reply carries after its body. [QuoteMode.Thread] quotes the original as it came,
 * with the earlier messages it quotes itself; [QuoteMode.Latest] only the original's own text,
 * cut where it starts quoting; [QuoteMode.None] nothing.
 */
fun OriginalMessage.replyQuote(mode: QuoteMode): String =
    when (mode) {
        QuoteMode.Thread -> quoted(body)
        QuoteMode.Latest -> quoted(ownText(body))
        QuoteMode.None -> ""
    }

/** The text before the first line that quotes or introduces an earlier message. */
internal fun ownText(body: String): String =
    body
        .lines()
        .takeWhile { line -> !line.trimStart().startsWith(QUOTE) && !introPattern.matches(line.trim()) }
        .joinToString("\n")
        .trimEnd()

private val introPattern = Regex("""^(On .+wrote:|-+ ?(Original|Forwarded) [Mm]essage ?-+)$""")

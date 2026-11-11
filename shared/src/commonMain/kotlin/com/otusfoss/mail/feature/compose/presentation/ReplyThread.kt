package com.otusfoss.mail.feature.compose.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otusfoss.mail.core.messages.domain.OriginalMessage
import com.otusfoss.mail.feature.compose.domain.QuoteMode
import com.otusfoss.mail.feature.compose.domain.ThreadHistory
import com.otusfoss.mail.feature.compose.domain.ThreadMessage
import com.otusfoss.mail.feature.compose.domain.replyQuote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * The thread a reply answers: its [messages], newest first, and how much of it the reply quotes.
 * It starts with the [original] alone and [load]s the rest from [history]. Choosing a [quoteMode]
 * hands the new quote to [onQuote].
 */
class ReplyThread(
    private val original: OriginalMessage,
    private val history: ThreadHistory?,
    private val scope: CoroutineScope,
    private val onQuote: (String) -> Unit,
) {
    var messages by mutableStateOf(listOf(original.asThreadMessage()))
        private set

    var quoteMode by mutableStateOf(QuoteMode.Thread)
        private set

    fun load() {
        val source = history ?: return
        scope.launch {
            val found = source.earlier(original.accountId, original.emailId)
            if (found.isNotEmpty()) messages = found
        }
    }

    fun quote(mode: QuoteMode) {
        quoteMode = mode
        onQuote(original.replyQuote(mode))
    }
}

/** The original as the thread's newest message, before the rest loads. */
private fun OriginalMessage.asThreadMessage(): ThreadMessage =
    ThreadMessage(
        emailId,
        from,
        LocalDate.parse(sentLabel.substringBefore(' ')),
        body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
    )

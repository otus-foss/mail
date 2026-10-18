package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.feature.inbox.domain.AddressBook
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.parseRecipients
import com.otus.mail.feature.inbox.domain.recipientText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A line of people a draft goes to. */
enum class RecipientField { To, Cc, Bcc }

/**
 * The address each recipient line is typing, before it becomes a chip, and the people
 * [addressBook] suggests for it. [people] and [change] read and replace a line's chips in the
 * draft of [accountId].
 */
class ComposeRecipients(
    private val addressBook: AddressBook,
    private val scope: CoroutineScope,
    private val accountId: () -> String,
    private val people: (RecipientField) -> List<MailSender>,
    private val change: (RecipientField, List<MailSender>) -> Unit,
    private val suggestDelayMillis: Long = SUGGEST_DELAY_MILLIS,
) {
    var typed by mutableStateOf(RecipientField.entries.associateWith { "" })
        private set

    /** The line being typed in and the people suggested for its text; empty without suggestions. */
    var suggestions by mutableStateOf(Suggestions())
        private set

    private var lookup: Job? = null

    /** Takes the line's new text; a comma or semicolon at its end makes chips of what came before. */
    fun type(
        field: RecipientField,
        text: String,
    ) {
        typed += field to text
        if (text.lastOrNull() in SEPARATORS) commit(field) else suggest(field, text)
    }

    /**
     * Makes chips of the addresses typed in the line, as when the user leaves it, and closes its
     * suggestions; text that is not an address stays to fix.
     */
    fun commit(field: RecipientField) {
        if (suggestions.field == field) dismissSuggestions()
        val (valid, invalid) = parseRecipients(typed.getValue(field)).partition { '@' in it.address }
        if (valid.isEmpty()) return
        add(field, valid)
        typed += field to invalid.recipientText()
    }

    /** Commits every line, so Send sees what was typed but not yet a chip. */
    fun commitAll() = RecipientField.entries.forEach(::commit)

    fun choose(
        field: RecipientField,
        person: MailSender,
    ) = add(field, listOf(person))

    /** Takes the chip at [index] back into the line's text to edit it. */
    fun edit(
        field: RecipientField,
        index: Int,
    ) {
        val line = people(field)
        val person = line.getOrNull(index) ?: return
        change(field, line - person)
        typed += field to person.recipientText()
    }

    fun removeLast(field: RecipientField) = change(field, people(field).dropLast(1))

    /** Empties the line, chips and text, as when it is hidden. */
    fun clear(field: RecipientField) {
        change(field, emptyList())
        typed += field to ""
    }

    fun dismissSuggestions() {
        lookup?.cancel()
        suggestions = Suggestions()
    }

    private fun add(
        field: RecipientField,
        found: List<MailSender>,
    ) {
        change(field, (people(field) + found).distinctBy { it.address.lowercase() })
        typed += field to ""
        dismissSuggestions()
    }

    private fun suggest(
        field: RecipientField,
        text: String,
    ) {
        lookup?.cancel()
        lookup =
            scope.launch {
                delay(suggestDelayMillis)
                val taken = people(field).map { it.address.lowercase() }.toSet()
                val found = addressBook.suggestions(accountId(), text).filterNot { it.address.lowercase() in taken }
                suggestions = Suggestions(field, found)
            }
    }

    /** People [addressBook] suggests for the text of [field]. */
    data class Suggestions(
        val field: RecipientField? = null,
        val people: List<MailSender> = emptyList(),
    )

    private companion object {
        const val SUGGEST_DELAY_MILLIS = 200L
        val SEPARATORS = setOf(',', ';')
    }
}

internal fun Draft.recipients(field: RecipientField): List<MailSender> =
    when (field) {
        RecipientField.To -> to
        RecipientField.Cc -> cc
        RecipientField.Bcc -> bcc
    }

internal fun Draft.withRecipients(
    field: RecipientField,
    people: List<MailSender>,
): Draft =
    when (field) {
        RecipientField.To -> copy(to = people)
        RecipientField.Cc -> copy(cc = people)
        RecipientField.Bcc -> copy(bcc = people)
    }

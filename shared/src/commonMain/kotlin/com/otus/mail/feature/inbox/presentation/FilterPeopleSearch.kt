package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.feature.inbox.domain.FoundPerson
import com.otus.mail.feature.inbox.domain.PeopleField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * The search field of the From or To page and the people the server [find]s for its [text], in
 * [scope], [SEARCH_DELAY_MILLIS] after the last keystroke. A new text cancels the search still
 * running, and blank text finds nobody. Without a scope only the loaded mail is searched.
 */
class FilterPeopleSearch(
    private val field: PeopleField,
    private val scope: CoroutineScope? = null,
    private val find: suspend (PeopleField, String) -> List<FoundPerson> = { _, _ -> emptyList() },
) {
    var text by mutableStateOf("")
        private set

    var found by mutableStateOf(emptyList<FoundPerson>())
        private set

    /** Whether a server search for [text] is still running. */
    var searching by mutableStateOf(false)
        private set

    private var running: Job? = null

    fun search(text: String) {
        if (text == this.text) return
        this.text = text
        running?.cancel()
        val launcher = scope
        if (text.isBlank() || launcher == null) {
            found = emptyList()
            searching = false
            return
        }
        searching = true
        running =
            launcher.launch {
                var answered = false
                try {
                    delay(SEARCH_DELAY_MILLIS)
                    found = find(field, text.trim())
                    answered = true
                } finally {
                    // A search that failed or was cancelled still ends, unless a newer one runs. A
                    // failed one also drops the last results, which belong to other text.
                    if (running === coroutineContext.job) {
                        searching = false
                        if (!answered) found = emptyList()
                    }
                }
            }
    }

    /** Empties the field, as when the panel closes. */
    fun clear() = search("")

    companion object {
        /** Typing a name searches once it pauses. */
        const val SEARCH_DELAY_MILLIS = 300L
    }
}

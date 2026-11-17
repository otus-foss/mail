package com.otusfoss.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_download
import com.otusfoss.core.designsystem.resources.symbol_forum
import com.otusfoss.core.designsystem.resources.symbol_mark_email_read
import com.otusfoss.core.designsystem.shared.feedback.components.InfoText
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceOptions
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceRow
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceSheet
import com.otusfoss.core.settings.presentation.components.ChoiceRowSpec
import com.otusfoss.core.settings.presentation.components.SettingRowSpec
import com.otusfoss.core.settings.presentation.components.ToggleRowSpec
import com.otusfoss.mail.core.messages.domain.BodyPrefetch
import com.otusfoss.mail.core.settings.domain.ReadBehavior
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState

/**
 * The Mail behavior page's Reading rows: marking mail read, quoted text, downloading mail ahead,
 * and the Read view's quick view.
 */
internal fun readingRows(state: MailBehaviorState): List<SettingRowSpec> {
    val read = state.behavior.read
    return listOf(
        ToggleRowSpec(
            "Mark as read when opened",
            read.markReadOnOpen,
            { on -> state.updateRead { it.copy(markReadOnOpen = on) } },
            Res.drawable.symbol_mark_email_read,
        ),
        ToggleRowSpec(
            "Show quoted text",
            read.showQuotes,
            { on -> state.updateRead { it.copy(showQuotes = on) } },
            Res.drawable.symbol_forum,
            info = InfoText("Show quoted text", "Swipe through the conversation to read earlier messages."),
        ),
        ChoiceRowSpec(
            PREFETCH_TITLE,
            read.prefetch.title,
            { PrefetchSheet(state, it) },
            BodyPrefetch.entries.map { it.title },
            Res.drawable.symbol_download,
            description = "New mail opens without waiting",
            keywords = listOf("Prefetch", "Mobile data", "Offline"),
        ),
    )
}

@Composable
private fun PrefetchSheet(
    state: MailBehaviorState,
    onDismiss: () -> Unit,
) {
    ChoiceSheet(
        PREFETCH_TITLE,
        ChoiceOptions(BodyPrefetch.entries, state.behavior.read.prefetch, ReadBehavior().prefetch),
        row = { ChoiceRow(it.title) },
        onSelect = { choice -> state.updateRead { it.copy(prefetch = choice) } },
        onDismiss = onDismiss,
    )
}

private val BodyPrefetch.title: String
    get() =
        when (this) {
            BodyPrefetch.Always -> "Always"
            BodyPrefetch.Unmetered -> "On Wi-Fi only"
            BodyPrefetch.Never -> "Never"
        }

private const val PREFETCH_TITLE = "Download mail ahead"

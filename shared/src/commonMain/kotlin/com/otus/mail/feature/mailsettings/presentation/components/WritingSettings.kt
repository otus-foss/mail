package com.otus.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_schedule_send
import com.otus.designsystem.resources.symbol_signature
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow
import com.otus.designsystem.shared.selection.components.ChoiceSheet
import com.otus.mail.core.settings.domain.DEFAULT_UNDO_SEND_SECONDS
import com.otus.mail.core.settings.domain.UNDO_SEND_CHOICES
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.core.settings.presentation.icon
import com.otus.settings.presentation.components.ChoiceRowSpec
import com.otus.settings.presentation.components.PageRowSpec
import com.otus.settings.presentation.components.SettingRowSpec
import com.otus.settings.presentation.components.SettingsSectionSpec
import com.otus.settings.presentation.components.ToggleRowSpec

/**
 * The Mail behavior page's Writing rows: how long a sent message waits on the device so Undo can
 * stop it, or Off to send at once, and the signatures' page, which [openSignatures] opens.
 */
fun writingRows(
    state: MailBehaviorState,
    openSignatures: (setting: String?) -> Unit,
): List<SettingRowSpec> {
    val write = state.behavior.write
    return listOf(
        ChoiceRowSpec(
            "Undo send",
            write.undoSendSeconds.undoLabel,
            { UndoSendSheet(state, it) },
            UNDO_SEND_CHOICES.map { it.undoLabel },
            Res.drawable.symbol_schedule_send,
            keywords = listOf("Delay", "Cancel sending"),
        ),
        PageRowSpec(
            "Signatures",
            openSignatures,
            description = "One for each address you send from",
            icon = Res.drawable.symbol_signature,
        ) { signaturesSettings(state) },
    )
}

// SignaturesScreen lives in the compose feature, which cannot import this one, so its switch is
// repeated here for search.
private fun signaturesSettings(state: MailBehaviorState): List<SettingsSectionSpec> =
    listOf(
        SettingsSectionSpec(
            title = null,
            rows =
                listOf(
                    ToggleRowSpec(
                        "Add to replies",
                        state.behavior.write.signatureInReplies,
                        { on -> state.updateWrite { it.copy(signatureInReplies = on) } },
                    ),
                ),
        ),
    )

@Composable
private fun UndoSendSheet(
    state: MailBehaviorState,
    onDismiss: () -> Unit,
) {
    ChoiceSheet(
        "Undo send",
        ChoiceOptions(UNDO_SEND_CHOICES, state.behavior.write.undoSendSeconds, DEFAULT_UNDO_SEND_SECONDS),
        row = { ChoiceRow(it.undoLabel, "Default".takeIf { _ -> it == DEFAULT_UNDO_SEND_SECONDS }) },
        onSelect = { chosen -> state.updateWrite { it.copy(undoSendSeconds = chosen) } },
        onDismiss = onDismiss,
    )
}

internal val Int.undoLabel: String get() = if (this == 0) "Off" else "$this seconds"

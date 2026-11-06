package com.otus.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_schedule_send
import com.otus.designsystem.resources.symbol_signature
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow
import com.otus.designsystem.shared.selection.components.ChoiceSheet
import com.otus.designsystem.shared.settings.components.SettingPageRow
import com.otus.designsystem.shared.settings.components.SettingRow
import com.otus.designsystem.shared.settings.components.SettingsSection
import com.otus.designsystem.shared.settings.elements.ValueButton
import com.otus.mail.core.settings.domain.DEFAULT_UNDO_SEND_SECONDS
import com.otus.mail.core.settings.domain.UNDO_SEND_CHOICES
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.core.settings.presentation.icon

/**
 * The Mail behavior page's Writing section: how long a sent message waits on the device so Undo
 * can stop it, or Off to send at once, and the signatures' page, which [onOpenSignatures] opens.
 */
@Composable
fun WritingSettings(
    state: MailBehaviorState,
    onOpenSignatures: () -> Unit,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val seconds = state.behavior.write.undoSendSeconds
    SettingsSection("Writing") {
        SettingRow(
            "Undo send",
            { choosing = true },
            icon = Res.drawable.symbol_schedule_send,
        ) { ValueButton(seconds.undoLabel) }
        SettingPageRow(
            "Signatures",
            onOpenSignatures,
            description = "One for each address you send from",
            icon = Res.drawable.symbol_signature,
        )
    }
    if (choosing) {
        ChoiceSheet(
            "Undo send",
            ChoiceOptions(UNDO_SEND_CHOICES, selected = seconds, default = DEFAULT_UNDO_SEND_SECONDS),
            row = { ChoiceRow(it.undoLabel, "Default".takeIf { _ -> it == DEFAULT_UNDO_SEND_SECONDS }) },
            onSelect = { chosen -> state.updateWrite { it.copy(undoSendSeconds = chosen) } },
            onDismiss = { choosing = false },
        )
    }
}

internal val Int.undoLabel: String get() = if (this == 0) "Off" else "$this seconds"

package com.otus.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow
import com.otus.designsystem.shared.selection.components.ChoiceSheet
import com.otus.mail.core.messages.domain.ActionBarMode
import com.otus.mail.core.settings.domain.ActionBehavior
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.settings.presentation.components.ChoiceRowSpec
import com.otus.settings.presentation.components.SettingsSectionSpec
import com.otus.settings.presentation.components.ToggleRowSpec

/**
 * The Action bar page's rows above its bar previews: whether each screen has its own bar or one
 * bar is used everywhere, and Minimize, which shrinks every bar to a round button for its main
 * action.
 */
internal fun actionBarSettings(state: MailBehaviorState): List<SettingsSectionSpec> {
    val actions = state.behavior.actions
    return listOf(
        SettingsSectionSpec(
            title = null,
            rows =
                listOf(
                    ChoiceRowSpec(
                        "Layout",
                        actions.barMode.label,
                        { LayoutSheet(state, it) },
                        ActionBarMode.entries.map { it.label },
                        description = actions.barMode.description,
                    ),
                    ToggleRowSpec(
                        "Minimize",
                        actions.minimizedBar,
                        { on -> state.updateActions { it.copy(minimizedBar = on) } },
                        description = "One round button for the main action",
                    ),
                ),
        ),
    )
}

@Composable
private fun LayoutSheet(
    state: MailBehaviorState,
    onDismiss: () -> Unit,
) {
    ChoiceSheet(
        "Layout",
        ChoiceOptions(ActionBarMode.entries, state.behavior.actions.barMode, ActionBehavior().barMode),
        row = { ChoiceRow(it.label, it.description) },
        onSelect = { chosen -> state.updateActions { it.copy(barMode = chosen) } },
        onDismiss = onDismiss,
    )
}

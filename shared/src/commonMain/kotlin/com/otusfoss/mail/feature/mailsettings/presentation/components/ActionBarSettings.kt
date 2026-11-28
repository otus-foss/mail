package com.otusfoss.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceOptions
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceRow
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceSheet
import com.otusfoss.core.settings.presentation.components.ChoiceRowSpec
import com.otusfoss.core.settings.presentation.components.SettingsSectionSpec
import com.otusfoss.mail.core.messages.domain.ActionBarMode
import com.otusfoss.mail.core.settings.domain.ActionBehavior
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState

/**
 * The Action bar page's rows above its bar previews: whether each screen has its own bar or one
 * bar is used everywhere.
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

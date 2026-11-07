package com.otus.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_grid_on
import com.otus.designsystem.resources.symbol_group
import com.otus.designsystem.shared.selection.components.OrderedChoice
import com.otus.designsystem.shared.selection.components.OrderedChoiceSheet
import com.otus.mail.core.settings.domain.ReadQuickModule
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.core.settings.presentation.icon
import com.otus.settings.presentation.components.ChoiceRowSpec
import org.jetbrains.compose.resources.DrawableResource

/** The Reading setting for what the Read view's quick view shows, with the sheet that picks it. */
internal fun readQuickViewRow(state: MailBehaviorState): ChoiceRowSpec =
    ChoiceRowSpec(
        "Quick view",
        state.behavior.read.quickView
            .firstOrNull()
            ?.title ?: "None",
        { ReadQuickViewSheet(state, it) },
        icon = Res.drawable.symbol_grid_on,
        description = "Pull the top panel down while reading",
    )

@Composable
private fun ReadQuickViewSheet(
    state: MailBehaviorState,
    onDismiss: () -> Unit,
) {
    OrderedChoiceSheet(
        "Quick view",
        state.behavior.read.quickView,
        ReadQuickModule.entries.map { OrderedChoice(it, it.storageValue, it.title, it.icon, "Modules") },
        ReadQuickModule::storageValue,
        onApply = { modules -> state.updateRead { it.copy(quickView = modules) } },
        onDismiss = onDismiss,
        limit = ReadQuickModule.LIMIT,
    )
}

private val ReadQuickModule.title: String
    get() =
        when (this) {
            ReadQuickModule.Thread -> "In this thread"
            ReadQuickModule.Counts -> "Counts"
        }

private val ReadQuickModule.icon: DrawableResource
    get() =
        when (this) {
            ReadQuickModule.Thread -> Res.drawable.symbol_group
            ReadQuickModule.Counts -> Res.drawable.symbol_grid_on
        }

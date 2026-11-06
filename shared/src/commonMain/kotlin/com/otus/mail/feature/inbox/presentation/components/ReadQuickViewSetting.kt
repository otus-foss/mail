package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_grid_on
import com.otus.designsystem.resources.symbol_group
import com.otus.designsystem.resources.symbol_person
import com.otus.designsystem.shared.selection.components.OrderedChoice
import com.otus.designsystem.shared.selection.components.OrderedChoiceSheet
import com.otus.designsystem.shared.settings.components.SettingRow
import com.otus.designsystem.shared.settings.elements.ValueButton
import com.otus.mail.core.settings.domain.ReadQuickModule
import com.otus.mail.core.settings.presentation.MailBehaviorState
import org.jetbrains.compose.resources.DrawableResource

/** The Reading setting for what the Read view's quick view shows, with the sheet that picks it. */
@Composable
internal fun ReadQuickViewSetting(state: MailBehaviorState) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    SettingRow(
        "Quick view",
        { choosing = true },
        icon = Res.drawable.symbol_grid_on,
        description = "Pull the top panel down while reading",
    ) { ValueButton(shownCount(state.behavior.readQuickView.size)) }
    if (!choosing) return
    OrderedChoiceSheet(
        "Quick view",
        state.behavior.readQuickView,
        ReadQuickModule.entries.map { OrderedChoice(it, it.storageValue, it.title, it.icon, "Modules") },
        ReadQuickModule::storageValue,
        onApply = { modules -> state.update { it.copy(readQuickView = modules) } },
        onDismiss = { choosing = false },
        limit = ReadQuickModule.LIMIT,
    )
}

private val ReadQuickModule.title: String
    get() =
        when (this) {
            ReadQuickModule.Thread -> "In this thread"
            ReadQuickModule.Sender -> "Sender"
            ReadQuickModule.Counts -> "Counts"
        }

private val ReadQuickModule.icon: DrawableResource
    get() =
        when (this) {
            ReadQuickModule.Thread -> Res.drawable.symbol_group
            ReadQuickModule.Sender -> Res.drawable.symbol_person
            ReadQuickModule.Counts -> Res.drawable.symbol_grid_on
        }

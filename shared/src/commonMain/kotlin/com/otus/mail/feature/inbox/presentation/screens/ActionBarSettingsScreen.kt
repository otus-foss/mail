package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.ChoiceOptions
import com.otus.designsystem.components.ChoiceSheet
import com.otus.designsystem.components.PreviewCard
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.components.Subpage
import com.otus.designsystem.elements.ValueButton
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.feature.inbox.domain.ActionBarMode
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.components.ActionBarCopySheet
import com.otus.mail.feature.inbox.presentation.components.ActionBarEditSheet
import com.otus.mail.feature.inbox.presentation.components.ActionBarPreview
import com.otus.mail.feature.inbox.presentation.components.description
import com.otus.mail.feature.inbox.presentation.components.label
import com.otus.mail.feature.inbox.presentation.components.marker
import com.otus.mail.feature.inbox.presentation.components.title

/**
 * Chooses whether each screen has its own action bar or one bar is used everywhere. A card per
 * bar that mode shows previews it as saved, and tapping the card opens its editor. The editor's
 * menu copies the bar to other screens.
 */
@Composable
fun ActionBarSettingsScreen(
    state: MailBehaviorState,
    onBack: () -> Unit,
) {
    var choosingMode by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<ActionBarPlace?>(null) }
    var copying by rememberSaveable { mutableStateOf<ActionBarPlace?>(null) }
    val mode = state.behavior.actionBarMode
    Subpage("Action bar", onBack) {
        SettingRow("Layout", { choosingMode = true }, description = mode.description) { ValueButton(mode.label) }
        Column(
            Modifier.padding(top = PageTokens.rowGap),
            verticalArrangement = Arrangement.spacedBy(PageTokens.rowGap),
        ) {
            ActionBarPlace.entries.filter { it.adaptive == (mode == ActionBarMode.Adaptive) }.forEach { place ->
                val open = { editing = place }
                PreviewCard(place.marker, title = place.title, description = place.description, onClick = open) {
                    ActionBarPreview(state.behavior.savedLayout(place), { open() })
                }
            }
        }
    }
    if (choosingMode) {
        ChoiceSheet(
            "Layout",
            ChoiceOptions(ActionBarMode.entries, mode, MailBehavior().actionBarMode),
            label = { it.label },
            detail = { it.description },
            onSelect = { chosen -> state.update { it.copy(actionBarMode = chosen) } },
            onDismiss = { choosingMode = false },
        )
    }
    editing?.let { place ->
        ActionBarEditSheet(
            state,
            place,
            onCopy = {
                editing = null
                copying = place
            },
        ) { editing = null }
    }
    copying?.let { place -> ActionBarCopySheet(state, place) { copying = null } }
}

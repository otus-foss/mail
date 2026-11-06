package com.otus.mail.feature.mailsettings.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.shared.pages.components.Subpage
import com.otus.designsystem.shared.settings.components.PreviewCard
import com.otus.designsystem.theme.tokens.layout.PageTokens
import com.otus.mail.core.messages.domain.ActionBarMode
import com.otus.mail.core.messages.domain.ActionBarPlace
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.feature.mailsettings.presentation.components.ActionBarCopySheet
import com.otus.mail.feature.mailsettings.presentation.components.ActionBarEditSheet
import com.otus.mail.feature.mailsettings.presentation.components.ActionBarPreview
import com.otus.mail.feature.mailsettings.presentation.components.actionBarSettings
import com.otus.mail.feature.mailsettings.presentation.components.description
import com.otus.mail.feature.mailsettings.presentation.components.marker
import com.otus.mail.feature.mailsettings.presentation.components.title
import com.otus.settings.presentation.components.SettingSections

/**
 * Chooses whether each screen has its own action bar or one bar is used everywhere. A card per
 * bar that mode shows previews it as saved, and tapping the card opens its editor, where Move to
 * buttons pick among [folders], the paths of every account's own folders, each icon in its
 * [folderColor]. The editor's menu copies the bar to other screens. Minimize shrinks every bar to
 * a round button for its main action.
 */
@Composable
fun ActionBarSettingsScreen(
    state: MailBehaviorState,
    folders: List<List<String>>,
    folderColor: (path: List<String>) -> String?,
    onBack: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf<ActionBarPlace?>(null) }
    var copying by rememberSaveable { mutableStateOf<ActionBarPlace?>(null) }
    val mode = state.behavior.actions.barMode
    Subpage("Action bar", onBack) {
        SettingSections(actionBarSettings(state))
        Column(
            Modifier.padding(top = PageTokens.rowGap),
            verticalArrangement = Arrangement.spacedBy(PageTokens.rowGap),
        ) {
            ActionBarPlace.entries.filter { it.adaptive == (mode == ActionBarMode.Adaptive) }.forEach { place ->
                val open = { editing = place }
                PreviewCard(place.marker, title = place.title, description = place.description, onClick = open) {
                    ActionBarPreview(state.behavior.actions.savedLayout(place), { open() })
                }
            }
        }
    }
    editing?.let { place ->
        ActionBarEditSheet(
            state,
            place,
            folders,
            folderColor,
            onCopy = {
                editing = null
                copying = place
            },
        ) { editing = null }
    }
    copying?.let { place -> ActionBarCopySheet(state, place) { copying = null } }
}

package com.otusfoss.mail.feature.notifications.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.otusfoss.core.designsystem.shared.selection.components.PickMark
import com.otusfoss.core.designsystem.shared.selection.components.PickRow
import com.otusfoss.core.designsystem.shared.selection.components.PickStyle
import com.otusfoss.mail.feature.notifications.domain.suggested
import com.otusfoss.mail.feature.notifications.presentation.NotificationSettingsState
import com.otusfoss.mail.feature.notifications.presentation.choiceRow

/**
 * The services that can deliver new mail, as cards to pick one from on a page, with periodic
 * checks last. Until the user chooses, the one that suits [private] settings shows as chosen; it
 * takes effect only once the user taps a card or the step goes on.
 */
@Composable
fun DeliveryChoices(
    state: NotificationSettingsState,
    private: Boolean,
) {
    LaunchedEffect(state) { state.watch() }
    LaunchedEffect(state.distributors, private) { state.suggest(state.distributors.suggested(private)) }
    (state.distributors + null).forEach { distributor ->
        val row = distributor.choiceRow(state.periodicChecks)
        PickRow(
            row.label,
            row.detail,
            PickMark(selected = distributor == state.shown, style = PickStyle.Card),
            onClick = { state.choose(distributor) },
        )
    }
}

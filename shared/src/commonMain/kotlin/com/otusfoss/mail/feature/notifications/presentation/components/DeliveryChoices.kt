package com.otusfoss.mail.feature.notifications.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otusfoss.core.designsystem.shared.selection.components.PickMark
import com.otusfoss.core.designsystem.shared.selection.components.PickRow
import com.otusfoss.core.designsystem.shared.selection.components.PickStyle
import com.otusfoss.mail.feature.notifications.domain.suggested
import com.otusfoss.mail.feature.notifications.presentation.NotificationSettingsState
import com.otusfoss.mail.feature.notifications.presentation.choiceRow

/**
 * The services that can deliver new mail, as cards to pick one from on a page, with periodic
 * checks last. Until the user has a service, the first load picks the one that suits [private]
 * settings, once.
 */
@Composable
fun DeliveryChoices(
    state: NotificationSettingsState,
    private: Boolean,
) {
    var suggested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state) { state.watch() }
    LaunchedEffect(state.distributors) {
        if (suggested || state.distributors.isEmpty()) return@LaunchedEffect
        suggested = true
        if (state.current == null) state.distributors.suggested(private)?.let(state::choose)
    }
    (state.distributors + null).forEach { distributor ->
        val row = distributor.choiceRow(state.periodicChecks)
        PickRow(
            row.label,
            row.detail,
            PickMark(selected = distributor == state.current, style = PickStyle.Card),
            onClick = { state.choose(distributor) },
        )
    }
}

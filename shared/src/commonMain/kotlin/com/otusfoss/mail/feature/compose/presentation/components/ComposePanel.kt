package com.otusfoss.mail.feature.compose.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.otusfoss.core.designsystem.mail.compose.elements.AddressPill
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_add
import com.otusfoss.core.designsystem.shared.controls.elements.ButtonPill
import com.otusfoss.core.designsystem.shared.controls.elements.Symbol
import com.otusfoss.core.designsystem.shared.overlays.components.OptionsItem
import com.otusfoss.core.designsystem.shared.panels.components.ChipPlacement
import com.otusfoss.core.designsystem.shared.panels.components.ContextPanel
import com.otusfoss.core.designsystem.shared.panels.elements.PanelNavigation
import com.otusfoss.core.designsystem.shared.people.components.PersonAvatar
import com.otusfoss.core.designsystem.theme.tokens.people.SenderTokens
import com.otusfoss.mail.core.messages.domain.SendIdentity
import com.otusfoss.mail.core.messages.presentation.components.OptionsButton
import com.otusfoss.mail.core.messages.presentation.components.chipAvatar
import com.otusfoss.mail.feature.compose.presentation.ComposeState

/**
 * The compose page's floating panel: Back and the [title], over pills for the address it is sent
 * from and for adding Cc, and a ⋯ menu of [options].
 * [onAddCc] is null once the Cc line shows.
 */
@Composable
internal fun ComposePanel(
    state: ComposeState,
    title: String,
    recentFrom: List<String>,
    onBack: () -> Unit,
    onAddCc: (() -> Unit)?,
    options: List<OptionsItem>,
) {
    ContextPanel(
        placement = ChipPlacement.Overlapping,
        navigation = { PanelNavigation("Back", onBack) },
        trailingAction = { OptionsButton(options) },
        chips = {
            item(FROM_PILL) { FromPill(state, recentFrom) }
            if (onAddCc != null) {
                item(CC_PILL) { ButtonPill("Cc", onAddCc, description = "Add Cc") { Symbol(Res.drawable.symbol_add) } }
            }
        },
    ) {
        Text(title, Modifier.semantics { heading() }, maxLines = 1)
    }
}

@Composable
private fun FromPill(
    state: ComposeState,
    recentFrom: List<String>,
) {
    val from = state.draft.from ?: return
    var choosing by remember { mutableStateOf(false) }
    Box {
        AddressPill(from.address, "Send from ${from.address}. Change", { choosing = true }) {
            PersonAvatar(from.chipAvatar, SenderTokens.avatarSize)
        }
        if (choosing && state.identities.size > 1) {
            FromDropdown(
                state.identities,
                state.accounts,
                recentFrom,
                from,
                onSelect = { identity: SendIdentity -> state.chooseFrom(identity) },
                onDismiss = { choosing = false },
            )
        }
    }
}

private const val FROM_PILL = "from"

private const val CC_PILL = "cc"

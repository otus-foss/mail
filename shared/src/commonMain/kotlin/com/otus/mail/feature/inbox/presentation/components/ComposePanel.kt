package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.otus.designsystem.components.ChipPlacement
import com.otus.designsystem.components.ContextPanel
import com.otus.designsystem.components.OptionsItem
import com.otus.designsystem.components.OptionsMenu
import com.otus.designsystem.components.PersonAvatar
import com.otus.designsystem.elements.AddressPill
import com.otus.designsystem.elements.ButtonPill
import com.otus.designsystem.elements.IconButton
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_add
import com.otus.designsystem.resources.symbol_chevron_left
import com.otus.designsystem.resources.symbol_more_horiz
import com.otus.designsystem.theme.ComposeTokens
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.PanelTokens
import com.otus.mail.feature.inbox.domain.SendIdentity
import com.otus.mail.feature.inbox.presentation.ComposeState

/**
 * The compose page's floating panel: Back, the [title], and "Saved" once the server holds the
 * draft, over pills for the address it is sent from and for adding Cc, and a ⋯ menu of [options].
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
        navigation = {
            IconButton("Back", onBack, outlined = false) {
                Symbol(Res.drawable.symbol_chevron_left, mirrorInRtl = true, size = PanelTokens.navigationIconSize)
            }
        },
        trailingAction = { OptionsButton(options) },
        chips = {
            item(FROM_PILL) { FromPill(state, recentFrom) }
            onAddCc?.let { add ->
                item(CC_PILL) { ButtonPill("Cc", add, description = "Add Cc") { Symbol(Res.drawable.symbol_add) } }
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f).semantics { heading() }, maxLines = 1)
            if (state.saved) Text("Saved", color = OtusColors.text3)
        }
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
            PersonAvatar(chipAvatar(from.avatar, from.name, from.address), ComposeTokens.fromAvatarSize)
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

@Composable
private fun OptionsButton(options: List<OptionsItem>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton("More options", { open = true }) { Symbol(Res.drawable.symbol_more_horiz) }
        if (open) OptionsMenu(options) { open = false }
    }
}

private const val FROM_PILL = "from"
private const val CC_PILL = "cc"

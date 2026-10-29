package com.otus.mail.feature.inbox.presentation.components

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
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_add
import com.otus.designsystem.resources.symbol_chevron_left
import com.otus.designsystem.resources.symbol_more_horiz
import com.otus.designsystem.shared.controls.elements.AddressPill
import com.otus.designsystem.shared.controls.elements.ButtonPill
import com.otus.designsystem.shared.controls.elements.IconButton
import com.otus.designsystem.shared.controls.elements.IconButtonLook
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.overlays.components.OptionsItem
import com.otus.designsystem.shared.overlays.components.OptionsMenu
import com.otus.designsystem.shared.panels.components.ChipPlacement
import com.otus.designsystem.shared.panels.components.ContextPanel
import com.otus.designsystem.shared.people.components.PersonAvatar
import com.otus.designsystem.theme.tokens.editing.ComposeTokens
import com.otus.designsystem.theme.tokens.layout.PanelTokens
import com.otus.mail.feature.inbox.domain.SendIdentity
import com.otus.mail.feature.inbox.presentation.ComposeState

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
        navigation = {
            IconButton("Back", onBack, look = IconButtonLook.Plain) {
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
            PersonAvatar(from.chipAvatar, ComposeTokens.fromAvatarSize)
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

/** The ⋯ button of a page's panel, named [description], which opens [options] under it. */
@Composable
internal fun OptionsButton(
    options: List<OptionsItem>,
    description: String = "More options",
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(description, { open = true }) { Symbol(Res.drawable.symbol_more_horiz) }
        if (open) OptionsMenu(options) { open = false }
    }
}

private const val FROM_PILL = "from"
private const val CC_PILL = "cc"

package com.otusfoss.mail.core.messages.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_more_horiz
import com.otusfoss.core.designsystem.shared.controls.elements.IconButton
import com.otusfoss.core.designsystem.shared.controls.elements.Symbol
import com.otusfoss.core.designsystem.shared.overlays.components.OptionsItem
import com.otusfoss.core.designsystem.shared.overlays.components.OptionsMenu

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

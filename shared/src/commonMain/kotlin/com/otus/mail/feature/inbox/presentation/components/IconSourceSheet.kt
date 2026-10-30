package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.shared.input.elements.InputField
import com.otus.designsystem.shared.input.elements.InputKind
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow
import com.otus.designsystem.shared.selection.components.RadioChoiceSheet
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import com.otus.mail.core.avatar.IconSource
import com.otus.mail.core.avatar.bulwarkBase

/**
 * Picks where website icons come from, starting from [source] and [address]. Picking Bulwark shows
 * its address below the choices. Done saves both through [onSave], and only while a Bulwark address
 * is a public HTTPS one, so icons are never asked of a half-typed address. Done on another source
 * keeps the saved address. The scrim, Back, or a swipe down discards both.
 */
@Composable
internal fun IconSourceSheet(
    source: IconSource,
    address: String,
    onSave: (IconSource, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(address) }
    RadioChoiceSheet(
        "Load icons from",
        ChoiceOptions(IconSource.entries, source),
        row = { ChoiceRow(it.title, it.description) },
        onSave = { picked -> onSave(picked, if (picked == IconSource.Bulwark) draft.trim() else address) },
        onDismiss = onDismiss,
        doneEnabled = { it != IconSource.Bulwark || bulwarkBase(draft) != null },
    ) { picked ->
        if (picked == IconSource.Bulwark) {
            val usable = draft.isBlank() || bulwarkBase(draft) != null
            InputField(
                "Bulwark address",
                draft,
                { draft = it },
                Modifier.padding(start = SheetTokens.inset, end = SheetTokens.inset, bottom = SheetTokens.inset),
                placeholder = "mail.example.com",
                supportingText =
                    if (usable) {
                        "The Bulwark webmail beside your Stalwart server. Stalwart has no icon service."
                    } else {
                        "Use a public HTTPS address on the standard port."
                    },
                kind = InputKind.Url,
            )
        }
    }
}

/** How each source reads in the sheet and on the settings row that opens it. */
internal val IconSource.title: String
    get() =
        when (this) {
            IconSource.Websites -> "Sender websites"
            IconSource.DuckDuckGo -> "DuckDuckGo"
            IconSource.Bulwark -> "Bulwark"
        }

internal val IconSource.description: String
    get() =
        when (this) {
            IconSource.Websites -> "Each website sees your IP address"
            IconSource.DuckDuckGo -> "Only DuckDuckGo sees your IP address"
            IconSource.Bulwark -> "Only your Bulwark server sees your IP address"
        }

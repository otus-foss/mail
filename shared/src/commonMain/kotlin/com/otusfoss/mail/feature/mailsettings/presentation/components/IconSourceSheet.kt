package com.otusfoss.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otusfoss.core.avatar.IconSource
import com.otusfoss.core.avatar.bulwarkBase
import com.otusfoss.core.designsystem.shared.input.elements.InputField
import com.otusfoss.core.designsystem.shared.input.elements.InputKind
import com.otusfoss.core.designsystem.shared.input.elements.InputNote
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceOptions
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceRow
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceSheet
import com.otusfoss.core.designsystem.shared.selection.components.SaveOnDone
import com.otusfoss.mail.core.settings.presentation.icon

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
    ChoiceSheet(
        "Load icons from",
        ChoiceOptions(IconSource.entries, source),
        row = { ChoiceRow(it.title, it.description) },
        onSelect = { picked -> onSave(picked, if (picked == IconSource.Bulwark) draft.trim() else address) },
        onDismiss = onDismiss,
        saveOnDone =
            SaveOnDone(
                enabled = { it != IconSource.Bulwark || bulwarkBase(draft) != null },
                below = { picked -> if (picked == IconSource.Bulwark) BulwarkAddress(draft) { draft = it } },
            ),
    )
}

@Composable
private fun BulwarkAddress(
    draft: String,
    onChange: (String) -> Unit,
) {
    val usable = draft.isBlank() || bulwarkBase(draft) != null
    InputField(
        "Bulwark address",
        draft,
        onChange,
        placeholder = "mail.example.com",
        note =
            InputNote(
                if (usable) {
                    "The Bulwark webmail beside your Stalwart server. Stalwart has no icon service."
                } else {
                    "Use a public HTTPS address on the standard port."
                },
            ),
        kind = InputKind.Url,
    )
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

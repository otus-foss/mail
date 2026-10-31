package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.shared.overlays.components.InputSheet

/** Asks for the web address of a link; Add runs [onAdd] with it, with https:// when it has no scheme. */
@Composable
internal fun LinkSheet(
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    InputSheet(
        "Add link",
        "Web address",
        "Add",
        { address -> onAdd(address.trim().let { if ("://" in it || it.startsWith("mailto:")) it else "https://$it" }) },
        onDismiss,
        problem = { if (it.isBlank()) "Enter a web address" else null },
    )
}

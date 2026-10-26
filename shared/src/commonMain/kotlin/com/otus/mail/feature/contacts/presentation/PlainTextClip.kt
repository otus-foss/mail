package com.otus.mail.feature.contacts.presentation

import androidx.compose.ui.platform.ClipEntry

/** [text] as something to put on the clipboard; [label] names it where the platform shows one. */
internal expect fun plainTextClip(
    label: String,
    text: String,
): ClipEntry

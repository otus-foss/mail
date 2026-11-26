package com.otusfoss.mail.core.text.presentation

import androidx.compose.ui.platform.ClipEntry

/**
 * [text] as something to put on the clipboard; [label] names it where the platform shows one. A
 * [sensitive] text, such as a one-time code, stays out of the platform's preview of what was copied.
 */
internal expect fun plainTextClip(
    label: String,
    text: String,
    sensitive: Boolean = false,
): ClipEntry

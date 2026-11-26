package com.otusfoss.mail.core.text.presentation

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun plainTextClip(
    label: String,
    text: String,
    sensitive: Boolean,
): ClipEntry = ClipEntry.withPlainText(text)

package com.otus.mail.feature.contacts.presentation

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun plainTextClip(
    label: String,
    text: String,
): ClipEntry = ClipEntry.withPlainText(text)

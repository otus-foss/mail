package com.otus.mail.feature.contacts.presentation

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

internal actual fun plainTextClip(
    label: String,
    text: String,
): ClipEntry = ClipEntry(ClipData.newPlainText(label, text))

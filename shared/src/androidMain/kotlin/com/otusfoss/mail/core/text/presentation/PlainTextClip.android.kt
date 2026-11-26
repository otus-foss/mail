package com.otusfoss.mail.core.text.presentation

import android.content.ClipData
import android.os.PersistableBundle
import androidx.compose.ui.platform.ClipEntry

internal actual fun plainTextClip(
    label: String,
    text: String,
    sensitive: Boolean,
): ClipEntry = ClipEntry(ClipData.newPlainText(label, text).apply { if (sensitive) markSensitive() })

/**
 * Keeps this clip out of the copy preview Android 13 and later show. The key is
 * the value of `ClipDescription.EXTRA_IS_SENSITIVE`, written out so older versions set it too.
 */
fun ClipData.markSensitive() {
    description.extras = PersistableBundle().apply { putBoolean(SENSITIVE_KEY, true) }
}

private const val SENSITIVE_KEY = "android.content.extra.IS_SENSITIVE"

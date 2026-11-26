package com.otusfoss.mail.core.codes.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import com.otusfoss.mail.core.codes.domain.SignInCode
import com.otusfoss.mail.core.text.presentation.plainTextClip
import kotlinx.coroutines.launch

/**
 * Copies a one-time code as it is typed, without its spaces, and keeps it out of the system's
 * preview of what was copied. The Read view's strip and the list's rows share it.
 */
@Composable
internal fun rememberCodeCopy(): (SignInCode) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) {
        { code -> scope.launch { clipboard.setClipEntry(plainTextClip(ONE_TIME_CODE, code.value, sensitive = true)) } }
    }
}

/** What a code is called on its strip and on the clipboard. */
internal const val ONE_TIME_CODE = "One-time code"

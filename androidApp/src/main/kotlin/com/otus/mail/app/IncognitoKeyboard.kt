package com.otus.mail.app

import android.os.Build
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/**
 * Asks the keyboard of every text field in [content] not to learn from what is typed while [on],
 * with `IME_FLAG_NO_PERSONALIZED_LEARNING`, so mail does not end up in its suggestions or synced
 * dictionary. Keyboards may ignore the request. The interceptor stays in place either way and reads
 * [on] when a keyboard opens: wrapping [content] only while on would rebuild the whole app on a toggle.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun IncognitoKeyboard(
    on: Boolean,
    content: @Composable () -> Unit,
) {
    val incognito by rememberUpdatedState(on)
    InterceptPlatformTextInput(
        interceptor = { request, next ->
            next.startInputMethod(
                object : PlatformTextInputMethodRequest {
                    override fun createInputConnection(outAttributes: EditorInfo): InputConnection =
                        request.createInputConnection(outAttributes).also {
                            // The flag exists from Android 8; older keyboards have no way to be asked.
                            if (incognito && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                outAttributes.imeOptions =
                                    outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                            }
                        }
                },
            )
        },
        content = content,
    )
}

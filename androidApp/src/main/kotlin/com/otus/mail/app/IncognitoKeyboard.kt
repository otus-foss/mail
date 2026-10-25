package com.otus.mail.app

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
                            if (incognito) outAttributes.imeOptions = outAttributes.imeOptions or NO_LEARNING
                        }
                },
            )
        },
        content = content,
    )
}

private const val NO_LEARNING = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING

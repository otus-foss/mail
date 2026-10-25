package com.otus.mail

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/**
 * Asks the keyboard of every text field in [content] not to learn from what is typed while [on],
 * with `IME_FLAG_NO_PERSONALIZED_LEARNING`, so mail does not end up in its suggestions or synced
 * dictionary. Keyboards may ignore the request.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun IncognitoKeyboard(
    on: Boolean,
    content: @Composable () -> Unit,
) {
    if (!on) return content()
    InterceptPlatformTextInput(
        interceptor = { request, next ->
            next.startInputMethod(
                object : PlatformTextInputMethodRequest {
                    override fun createInputConnection(outAttributes: EditorInfo): InputConnection =
                        request.createInputConnection(outAttributes).also {
                            outAttributes.imeOptions = outAttributes.imeOptions or NO_LEARNING
                        }
                },
            )
        },
        content = content,
    )
}

private const val NO_LEARNING = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING

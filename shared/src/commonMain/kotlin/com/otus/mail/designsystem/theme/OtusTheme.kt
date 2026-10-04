package com.otus.mail.designsystem.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

private val otusDarkColors =
    darkColors(
        primary = OtusColors.accentRegular,
        primaryVariant = OtusColors.accentDark,
        secondary = OtusColors.accentLight,
        secondaryVariant = OtusColors.accentDark,
        background = OtusColors.bgRegular,
        surface = OtusColors.bgRegular,
        error = OtusColors.accentLight,
        onPrimary = OtusColors.text1,
        onSecondary = OtusColors.text1,
        onBackground = OtusColors.text1,
        onSurface = OtusColors.text1,
        onError = OtusColors.text1,
    )

@Composable
fun OtusTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = otusDarkColors) {
        CompositionLocalProvider(
            LocalContentColor provides OtusColors.text1,
            LocalContentAlpha provides 1f,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = OtusColors.bgRegular,
                contentColor = OtusColors.text1,
                content = content,
            )
        }
    }
}

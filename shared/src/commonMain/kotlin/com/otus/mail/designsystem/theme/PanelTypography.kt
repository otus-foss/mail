package com.otus.mail.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.otus.mail.designsystem.resources.Res
import com.otus.mail.designsystem.resources.figtree
import org.jetbrains.compose.resources.Font

@Composable
internal fun panelTextStyle(): TextStyle {
    val font = Font(Res.font.figtree)
    return remember(font) {
        TextStyle(
            fontFamily = FontFamily(font),
            fontSize = PanelTokens.textSize,
            lineHeight = PanelTokens.lineHeight,
            color = OtusColors.text1,
        )
    }
}

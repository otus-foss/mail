package com.otus.mail.designsystem.elements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.PanelTokens
import com.otus.mail.designsystem.theme.SheetTokens
import com.otus.mail.designsystem.theme.panelTextStyle

@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    contentPadding: Dp = SheetTokens.inset,
    labelContent: (@Composable () -> Unit)? = null,
) {
    val interactions = remember { MutableInteractionSource() }
    Surface(
        modifier
            .clickable(interactions, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .controlPressFeedback(interactions),
        shape = CircleShape,
        color = if (primary && enabled) OtusColors.accentDark else OtusColors.bgDark,
        border =
            BorderStroke(
                PanelTokens.borderWidth,
                if (primary) OtusColors.accentRegular else OtusColors.strokeBgDark,
            ),
    ) {
        Box(
            Modifier
                .heightIn(
                    min = PanelTokens.touchTarget,
                ).padding(horizontal = contentPadding, vertical = PanelTokens.iconGap),
            contentAlignment = Alignment.Center,
        ) {
            if (labelContent == null) {
                Text(label, style = panelTextStyle(), color = if (enabled) OtusColors.text1 else OtusColors.text3)
            } else {
                labelContent()
            }
        }
    }
}

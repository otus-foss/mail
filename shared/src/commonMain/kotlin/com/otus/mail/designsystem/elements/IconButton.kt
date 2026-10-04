package com.otus.mail.designsystem.elements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.PanelTokens

@Composable
fun IconButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(PanelTokens.touchTarget)
            .semantics { contentDescription = description }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactions,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier
                    .size(
                        PanelTokens.chipHeight,
                    ).reportControlVisualBounds()
                    .controlPressFeedback(interactions),
            shape = CircleShape,
            color = OtusColors.bgDark,
            contentColor = if (enabled) OtusColors.text1 else OtusColors.text3,
            border = if (outlined) BorderStroke(PanelTokens.borderWidth, OtusColors.strokeBgDark) else null,
        ) {
            Box(contentAlignment = Alignment.Center) { content() }
        }
    }
}

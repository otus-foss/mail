package com.otus.mail.designsystem.elements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.PanelTokens
import com.otus.mail.designsystem.theme.panelTextStyle

/** A compact pill inside a non-overlapping, minimum 48 dp touch target. */
@Composable
fun Chip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier
            .sizeIn(minWidth = PanelTokens.touchTarget, minHeight = PanelTokens.touchTarget)
            .toggleable(
                value = selected,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = interactions,
                indication = null,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.reportControlVisualBounds().controlPressFeedback(interactions),
            shape = CircleShape,
            color = if (selected) OtusColors.accentDark else OtusColors.bgDark,
            contentColor = if (enabled) OtusColors.text1 else OtusColors.text3,
            border =
                BorderStroke(
                    PanelTokens.borderWidth,
                    if (selected) OtusColors.accentLight else OtusColors.strokeBgDark,
                ),
        ) {
            Row(
                Modifier
                    .heightIn(min = PanelTokens.chipHeight)
                    .padding(horizontal = PanelTokens.chipPadding, vertical = PanelTokens.iconGap),
                horizontalArrangement = Arrangement.spacedBy(PanelTokens.iconGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading?.invoke()
                Text(
                    label,
                    style = panelTextStyle().copy(color = androidx.compose.ui.graphics.Color.Unspecified),
                    maxLines = 1,
                )
            }
        }
    }
}

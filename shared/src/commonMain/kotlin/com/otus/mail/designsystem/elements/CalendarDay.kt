package com.otus.mail.designsystem.elements

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.otus.mail.designsystem.theme.CalendarTokens
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.PanelTokens
import com.otus.mail.designsystem.theme.panelTextStyle

/** Plain calendar cell with a rectangular range band and softly squared endpoints. */
@Composable
fun CalendarDay(
    label: String,
    description: String,
    selection: CalendarDaySelection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDoubleClick: (() -> Unit)? = null,
    monthLabel: String? = null,
) {
    val interactions = remember { MutableInteractionSource() }
    val minimumHeight =
        with(LocalDensity.current) {
            maxOf(
                PanelTokens.touchTarget,
                PanelTokens.lineHeight.toDp() + CalendarTokens.monthLabelLineHeight.toDp() * 2 +
                    CalendarTokens.markerEdgeInset * 2,
            )
        }
    val shape = selection.rangeShape()
    Box(
        modifier
            .semantics {
                contentDescription = description
                selected = selection.inRange
            }.combinedClickable(
                interactionSource = interactions,
                indication = null,
                role = Role.Button,
                onClick = onClick,
                onDoubleClick = onDoubleClick,
            ).heightIn(min = minimumHeight)
            .padding(vertical = CalendarTokens.rangeInset)
            .background(
                when {
                    selection.endpoint -> OtusColors.accentRegular
                    selection.inRange -> OtusColors.accentDark
                    else -> Color.Transparent
                },
                shape,
            ).controlPressFeedback(interactions, shape)
            .padding(vertical = PanelTokens.iconGap),
        contentAlignment = Alignment.Center,
    ) {
        val style = panelTextStyle()
        Text(label, style = style, maxLines = 1)
        if (monthLabel != null) {
            CalendarMonthLabel(monthLabel, selection.inRange, style)
        }
    }
}

@Composable
private fun CalendarMonthLabel(
    label: String,
    selected: Boolean,
    style: TextStyle,
) {
    val height = with(LocalDensity.current) { CalendarTokens.monthLabelLineHeight.toDp() }
    val offset = with(LocalDensity.current) { (PanelTokens.lineHeight.toDp() + height) / 2 }
    Text(
        label,
        modifier = Modifier.offset(y = -offset).height(height),
        style = style.copy(fontSize = CalendarTokens.monthLabelSize, lineHeight = CalendarTokens.monthLabelLineHeight),
        color = if (selected) OtusColors.text1 else OtusColors.accentLight,
        maxLines = 1,
    )
}

data class CalendarDaySelection(
    val inRange: Boolean,
    val endpoint: Boolean,
    val connectsPrevious: Boolean = false,
    val connectsNext: Boolean = false,
)

private fun CalendarDaySelection.rangeShape(): Shape {
    if (!endpoint) return RectangleShape
    val leading = if (connectsPrevious) 0.dp else CalendarTokens.endpointRadius
    val trailing = if (connectsNext) 0.dp else CalendarTokens.endpointRadius
    return RoundedCornerShape(topStart = leading, bottomStart = leading, topEnd = trailing, bottomEnd = trailing)
}

package com.otus.mail.designsystem.elements

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.PanelTokens
import com.otus.mail.designsystem.theme.SheetTokens
import com.otus.mail.designsystem.theme.panelTextStyle

@Composable
fun SelectionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    singleChoice: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (singleChoice) PanelTokens.touchTarget else SheetTokens.rowHeight)
            .then(
                if (singleChoice) {
                    Modifier.selectable(
                        selected = selected,
                        interactionSource = interactions,
                        indication = null,
                        role = Role.RadioButton,
                        onClick = onClick,
                    )
                } else {
                    Modifier.toggleable(
                        selected,
                        interactionSource = interactions,
                        indication = null,
                        role = Role.Checkbox,
                    ) { onClick() }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SheetTokens.rowRadius))
                .background(if (selected) OtusColors.accentDark else OtusColors.bgDark)
                .controlPressFeedback(interactions, RoundedCornerShape(SheetTokens.rowRadius))
                .padding(SheetTokens.gap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            leading?.invoke()
            SelectionLabel(title, subtitle, Modifier.weight(1f))
            if (!singleChoice) SelectionCheckmark(selected)
        }
    }
}

@Composable
private fun SelectionCheckmark(selected: Boolean) {
    Checkbox(
        checked = selected,
        onCheckedChange = null,
        colors =
            CheckboxDefaults.colors(
                checkedColor = OtusColors.accentLight,
                uncheckedColor = OtusColors.text3,
                checkmarkColor = OtusColors.bgDark,
            ),
    )
}

@Composable
private fun SelectionLabel(
    title: String,
    subtitle: String?,
    modifier: Modifier,
) {
    Column(modifier) {
        Text(title, style = panelTextStyle())
        subtitle?.let {
            Text(it, style = panelTextStyle(), fontSize = SheetTokens.detailSize, color = OtusColors.text2)
        }
    }
}

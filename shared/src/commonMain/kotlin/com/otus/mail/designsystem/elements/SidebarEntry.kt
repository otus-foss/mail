package com.otus.mail.designsystem.elements

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.PanelTokens
import com.otus.mail.designsystem.theme.SidebarTokens
import com.otus.mail.designsystem.theme.panelTextStyle
import org.jetbrains.compose.resources.DrawableResource

/** One destination row: icon, then name. The current destination is highlighted. */
@Composable
fun SidebarEntry(
    label: String,
    icon: DrawableResource,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactions = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(SidebarTokens.entryRadius)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = PanelTokens.touchTarget)
            .selectable(
                selected,
                interactionSource = interactions,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (selected) OtusColors.accentDark else Color.Transparent)
                .controlPressFeedback(interactions, shape)
                .padding(SidebarTokens.entryPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SidebarTokens.entryPadding),
        ) {
            Symbol(icon)
            Text(label, style = panelTextStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

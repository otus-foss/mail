package com.otus.mail.feature.folders.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.elements.controlPressFeedback
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.SheetTokens
import com.otus.mail.designsystem.resources.Res
import com.otus.mail.designsystem.resources.symbol_drive_file_move
import com.otus.mail.feature.folders.domain.MailRule
import com.otus.mail.feature.folders.presentation.summary
import com.otus.mail.feature.folders.presentation.title

/** One rule: its name, what it matches, and where matching mail goes. Tapping it offers its actions. */
@Composable
fun RuleRow(
    rule: MailRule,
    folderPath: String?,
    onClick: () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(SheetTokens.rowRadius)
    val target = folderPath ?: "a deleted folder (mail stays in the inbox)"
    val details = listOfNotNull(rule.summary().takeIf { rule.name.isNotBlank() }, "Moves to $target")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = SheetTokens.rowHeight)
            .clip(shape)
            .background(OtusColors.bgDark)
            .clickable(interactions, indication = null, role = Role.Button, onClick = onClick)
            .controlPressFeedback(interactions, shape)
            .padding(SheetTokens.gap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SheetTokens.gap),
    ) {
        Symbol(Res.drawable.symbol_drive_file_move)
        Column(Modifier.weight(1f)) {
            Text(
                rule.title(),
                color = if (rule.enabled) OtusColors.text1 else OtusColors.text2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            (details + listOfNotNull("Paused".takeUnless { rule.enabled })).forEach {
                Text(
                    it,
                    fontSize = SheetTokens.detailSize,
                    color = OtusColors.text2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

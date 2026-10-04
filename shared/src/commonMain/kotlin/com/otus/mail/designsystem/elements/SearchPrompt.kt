package com.otus.mail.designsystem.elements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.PanelTokens
import com.otus.mail.designsystem.theme.panelTextStyle

@Composable
fun SearchPrompt(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = PanelTokens.touchTarget)
            .semantics { contentDescription = "Search mailbox" }
            .clickable(interactions, indication = null, role = Role.Button, onClick = onClick)
            .controlPressFeedback(interactions),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text("Tap to search", style = panelTextStyle(), color = OtusColors.text2)
    }
}

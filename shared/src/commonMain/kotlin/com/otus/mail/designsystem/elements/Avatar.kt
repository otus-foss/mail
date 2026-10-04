package com.otus.mail.designsystem.elements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otus.mail.designsystem.theme.InboxTokens
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.panelTextStyle

@Composable
fun Avatar(
    initial: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(InboxTokens.avatarSize).background(OtusColors.bgDark, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = panelTextStyle(), color = OtusColors.text2)
    }
}

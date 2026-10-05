package com.otus.mail.designsystem.elements

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.otus.mail.designsystem.theme.InboxTokens
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.panelTextStyle

@Composable
fun Avatar(
    initial: String,
    modifier: Modifier = Modifier,
    image: ImageBitmap? = null,
    colorIndex: Int = 0,
) {
    val background =
        when (colorIndex) {
            1 -> OtusColors.bgLight
            2 -> OtusColors.bgDark
            else -> OtusColors.accentDark
        }
    Box(
        modifier.size(InboxTokens.avatarSize).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (image == null) {
            Text(initial, style = panelTextStyle(), color = OtusColors.text1)
        } else {
            Image(
                image,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

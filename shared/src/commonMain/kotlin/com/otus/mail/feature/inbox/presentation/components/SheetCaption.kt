package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.otus.designsystem.theme.colors.OtusColors
import com.otus.designsystem.theme.tokens.overlays.SheetTokens

/** Small `text2` over a group of sheet rows, or, without [heading], a note about them. */
@Composable
internal fun SheetCaption(
    text: String,
    modifier: Modifier = Modifier,
    heading: Boolean = true,
) {
    Text(
        text,
        if (heading) modifier.semantics { heading() } else modifier,
        color = OtusColors.text2,
        fontSize = SheetTokens.detailSize,
    )
}

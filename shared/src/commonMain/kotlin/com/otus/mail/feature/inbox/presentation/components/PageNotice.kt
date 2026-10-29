package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.theme.colors.OtusColors
import com.otus.designsystem.theme.tokens.layout.PageTokens
import com.otus.designsystem.theme.tokens.rows.InboxTokens

/** A notice under a page's header, such as a refused mark or a draft that could not be saved. */
@Composable
internal fun PageNotice(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = PageTokens.textInset),
        color = OtusColors.text2,
        fontSize = InboxTokens.metadataSize,
    )
}

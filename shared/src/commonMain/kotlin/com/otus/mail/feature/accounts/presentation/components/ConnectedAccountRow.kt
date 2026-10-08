package com.otus.mail.feature.accounts.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.otus.designsystem.elements.IconButton
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.SheetTokens
import com.otus.mail.designsystem.resources.Res
import com.otus.mail.designsystem.resources.symbol_alternate_email
import com.otus.mail.designsystem.resources.symbol_close
import com.otus.mail.feature.accounts.domain.ConnectedAccount

@Composable
fun ConnectedAccountRow(
    account: ConnectedAccount,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = SheetTokens.rowHeight)
            .background(OtusColors.bgDark, RoundedCornerShape(SheetTokens.rowRadius))
            .padding(start = SheetTokens.gap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SheetTokens.gap),
    ) {
        Symbol(Res.drawable.symbol_alternate_email)
        Column(Modifier.weight(1f)) {
            Text(account.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${account.username} · ${account.server}",
                color = OtusColors.text2,
                fontSize = SheetTokens.detailSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton("Remove ${account.name}", onRemove, outlined = false) { Symbol(Res.drawable.symbol_close) }
    }
}

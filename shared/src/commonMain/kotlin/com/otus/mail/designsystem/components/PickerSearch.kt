package com.otus.mail.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import com.otus.mail.designsystem.elements.IconButton
import com.otus.mail.designsystem.elements.SearchField
import com.otus.mail.designsystem.elements.Symbol
import com.otus.mail.designsystem.resources.Res
import com.otus.mail.designsystem.resources.symbol_close
import com.otus.mail.designsystem.resources.symbol_search
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.PanelTokens
import com.otus.mail.designsystem.theme.SheetTokens

@Composable
fun PickerSearch(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
) {
    val focus = LocalFocusManager.current
    Surface(
        shape = CircleShape,
        color = OtusColors.bgRegular,
        border = BorderStroke(PanelTokens.borderWidth, OtusColors.strokeBgDark),
    ) {
        Row(
            Modifier
                .heightIn(
                    min = PanelTokens.touchTarget,
                ).padding(start = SheetTokens.inset, end = PanelTokens.iconGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Symbol(Res.drawable.symbol_search)
            SearchField(
                value,
                onChange,
                placeholder,
                Modifier.weight(1f).padding(SheetTokens.gap),
                onSearch = { focus.clearFocus() },
            )
            if (value.isNotEmpty()) {
                IconButton(
                    "Clear picker search",
                    { onChange("") },
                    outlined = false,
                ) { Symbol(Res.drawable.symbol_close) }
            }
        }
    }
}

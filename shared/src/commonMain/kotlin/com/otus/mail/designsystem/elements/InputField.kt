package com.otus.mail.designsystem.elements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.PanelTokens
import com.otus.mail.designsystem.theme.SheetTokens
import com.otus.mail.designsystem.theme.panelTextStyle

/** Labelled single-line form input. */
@Composable
fun InputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    supportingText: String? = null,
    kind: InputKind = InputKind.Text,
) {
    val style = panelTextStyle()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PanelTokens.iconGap)) {
        Text(
            label,
            Modifier.padding(horizontal = SheetTokens.gap),
            style = style,
            fontSize = SheetTokens.detailSize,
            color = OtusColors.text2,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(OtusColors.accentLight),
            visualTransformation =
                if (kind ==
                    InputKind.Password
                ) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
            keyboardOptions =
                KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = kind.keyboardType,
                ),
            decorationBox = { field ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = PanelTokens.touchTarget)
                        .background(OtusColors.bgDark, RoundedCornerShape(SheetTokens.rowRadius))
                        .border(
                            BorderStroke(PanelTokens.borderWidth, OtusColors.strokeBgDark),
                            RoundedCornerShape(SheetTokens.rowRadius),
                        ).padding(horizontal = SheetTokens.gap),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) Text(placeholder, style = style, color = OtusColors.text3, maxLines = 1)
                    field()
                }
            },
        )
        supportingText?.let {
            Text(
                it,
                Modifier.padding(horizontal = SheetTokens.gap),
                style = style,
                fontSize = SheetTokens.detailSize,
                color = OtusColors.text2,
            )
        }
    }
}

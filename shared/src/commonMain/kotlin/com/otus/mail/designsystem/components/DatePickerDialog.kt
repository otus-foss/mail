package com.otus.mail.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.otus.mail.designsystem.theme.CalendarTokens
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.PanelTokens
import com.otus.mail.designsystem.theme.SheetTokens
import com.otus.mail.designsystem.theme.panelTextStyle

@Composable
internal fun DatePickerDialog(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalDensity provides density) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(SheetTokens.inset), Alignment.Center) {
                Box(
                    Modifier.matchParentSize().clickable(
                        remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
                )
                Surface(
                    Modifier
                        .widthIn(max = SheetTokens.maxWidth)
                        .fillMaxWidth()
                        .fillMaxHeight(CalendarTokens.PICKER_HEIGHT_FRACTION),
                    shape = RoundedCornerShape(SheetTokens.radius),
                    color = OtusColors.bgDark,
                    contentColor = OtusColors.text1,
                    border = BorderStroke(PanelTokens.borderWidth, OtusColors.strokeBgDark),
                ) {
                    ProvideTextStyle(panelTextStyle()) {
                        Column(
                            Modifier.padding(SheetTokens.inset),
                            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
                            content = content,
                        )
                    }
                }
            }
        }
    }
}

package com.otus.mail.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.otus.mail.designsystem.elements.IconButton
import com.otus.mail.designsystem.elements.Symbol
import com.otus.mail.designsystem.resources.Res
import com.otus.mail.designsystem.resources.symbol_close
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.PageTokens
import com.otus.mail.designsystem.theme.PanelTokens
import com.otus.mail.designsystem.theme.SheetTokens
import com.otus.mail.designsystem.theme.panelTextStyle

/** A centered popup over a scrim, with a title and a close button. Dismissing changes nothing. */
@Composable
fun DialogFrame(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalDensity provides density) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(OtusColors.bgDark.copy(alpha = SheetTokens.SCRIM_ALPHA))
                        .semantics { contentDescription = "Dismiss $title" }
                        .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
                )
                Surface(
                    Modifier
                        .safeDrawingPadding()
                        .imePadding()
                        .padding(SheetTokens.gap)
                        .widthIn(max = PageTokens.dialogMaxWidth)
                        .fillMaxWidth()
                        .semantics { paneTitle = title },
                    shape = RoundedCornerShape(SheetTokens.radius),
                    color = OtusColors.bgDark,
                    contentColor = OtusColors.text1,
                    border = BorderStroke(PanelTokens.borderWidth, OtusColors.strokeBgDark),
                ) {
                    ProvideTextStyle(panelTextStyle()) {
                        Column(
                            Modifier.padding(SheetTokens.inset),
                            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
                        ) {
                            DialogHeading(title, onDismiss)
                            content()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogHeading(
    title: String,
    onDismiss: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() }, fontSize = SheetTokens.titleSize)
        IconButton("Close", onDismiss, outlined = false) { Symbol(Res.drawable.symbol_close) }
    }
}

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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.otus.mail.designsystem.elements.ActionButton
import com.otus.mail.designsystem.elements.IconButton
import com.otus.mail.designsystem.elements.Symbol
import com.otus.mail.designsystem.resources.Res
import com.otus.mail.designsystem.resources.symbol_close
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.PanelTokens
import com.otus.mail.designsystem.theme.SheetTokens
import com.otus.mail.designsystem.theme.panelTextStyle
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Modal shell. The caller owns drafts; dismissing never applies them.
 * A second Clear tap within the double-tap timeout also calls [onApply], applying the cleared draft.
 */
@Composable
fun SelectionSheet(
    title: String,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onApply: () -> Unit,
    applyEnabled: Boolean = true,
    search: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val clear = rememberRepeatedClear(onClear, onApply)
    Dialog(onDismiss, properties = selectionSheetDialogProperties()) {
        CompositionLocalProvider(LocalDensity provides density) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(OtusColors.bgDark.copy(alpha = SheetTokens.SCRIM_ALPHA))
                        .semantics { contentDescription = "Dismiss selection" }
                        .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .imePadding()
                        .padding(SheetTokens.gap),
                    Alignment.BottomCenter,
                ) {
                    Surface(
                        Modifier
                            .widthIn(
                                max = SheetTokens.maxWidth,
                            ).fillMaxWidth()
                            .fillMaxHeight(SheetTokens.HEIGHT_FRACTION)
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
                                SheetHeading(title, onDismiss)
                                search?.invoke()
                                Column(Modifier.weight(1f), content = content)
                                Row(horizontalArrangement = Arrangement.spacedBy(SheetTokens.gap)) {
                                    ActionButton("Clear", clear, Modifier.weight(1f))
                                    ActionButton(
                                        "Apply",
                                        onApply,
                                        Modifier.weight(1f),
                                        primary = true,
                                        enabled = applyEnabled,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Clears on every tap; a quick second tap also applies the cleared draft. */
@Composable
private fun rememberRepeatedClear(
    onClear: () -> Unit,
    onApply: () -> Unit,
): () -> Unit {
    val timeout = LocalViewConfiguration.current.doubleTapTimeoutMillis.milliseconds
    var lastClear by remember { mutableStateOf<TimeMark?>(null) }
    return {
        onClear()
        if (lastClear?.let { it.elapsedNow() <= timeout } == true) {
            lastClear = null
            onApply()
        } else {
            lastClear = TimeSource.Monotonic.markNow()
        }
    }
}

/** Android's window-inset option is absent from the iOS dialog API. */
internal expect fun selectionSheetDialogProperties(): DialogProperties

@Composable
private fun SheetHeading(
    title: String,
    onDismiss: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() }, fontSize = SheetTokens.titleSize)
        IconButton("Close selection", onDismiss, outlined = false) { Symbol(Res.drawable.symbol_close) }
    }
}

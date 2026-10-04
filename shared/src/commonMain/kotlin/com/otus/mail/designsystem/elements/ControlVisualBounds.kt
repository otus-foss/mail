package com.otus.mail.designsystem.elements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/** Reports the actual painted surface separately from its larger interactive target. */
internal val LocalControlVisualBounds = staticCompositionLocalOf<((LayoutCoordinates) -> Unit)?> { null }

@Composable
internal fun Modifier.reportControlVisualBounds(): Modifier {
    val report = LocalControlVisualBounds.current ?: return this
    return onGloballyPositioned(report)
}

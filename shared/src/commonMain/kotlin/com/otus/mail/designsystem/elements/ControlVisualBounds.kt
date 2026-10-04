package com.otus.mail.designsystem.elements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

/** Reports the actual painted surface separately from its larger interactive target. */
internal val LocalControlVisualBounds = staticCompositionLocalOf<((Rect) -> Unit)?> { null }

@Composable
internal fun Modifier.reportControlVisualBounds(): Modifier {
    val report = LocalControlVisualBounds.current ?: return this
    return onGloballyPositioned {
        report(Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat())))
    }
}

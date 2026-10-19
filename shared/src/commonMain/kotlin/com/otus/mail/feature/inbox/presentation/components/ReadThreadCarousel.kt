package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The thread strips of [count] pages side by side, moving with the pages' [position], such as 1.4
 * while the user drags from the second page toward the third. Only the strips in view are shown,
 * so a drag slides the next strip in beside the current one. Screen readers hear only the strip of
 * the [current] page.
 */
@Composable
internal fun ReadThreadCarousel(
    count: Int,
    current: Int,
    position: () -> Float,
    modifier: Modifier = Modifier,
    strip: @Composable (Int) -> Unit,
) {
    // Mirrored layouts place the next page on the left, as the pager does.
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    // The page at or before the position and the one after it, so composition changes only at a page edge.
    val inView by remember(count) {
        derivedStateOf { floor(position()).toInt().let { listOf(it, it + 1) }.filter { it in 0 until count } }
    }
    BoxWithConstraints(modifier.fillMaxWidth().clipToBounds()) {
        val width = constraints.maxWidth
        inView.forEach { index ->
            key(index) {
                val shift = Modifier.offset { IntOffset(((index - position()) * width * direction).roundToInt(), 0) }
                val heard = if (index == current) Modifier else Modifier.clearAndSetSemantics {}
                Box(shift.then(heard)) { strip(index) }
            }
        }
    }
}

/** How the thread strip follows the pages: the [swipe] that turns them, and their [position]. */
internal class StripSlide(
    val swipe: Modifier,
    val position: () -> Float,
)

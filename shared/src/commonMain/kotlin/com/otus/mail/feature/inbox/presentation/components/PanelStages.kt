package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.ui.unit.Dp
import com.otus.designsystem.theme.tokens.layout.PanelTokens
import com.otus.designsystem.theme.tokens.layout.QuickViewTokens

// The steps a page's control panel grows through, shared by the inbox and the Read view: its
// usual height, a quick view, and a last stop, such as every filter or every detail.

/** A quick view's height with [modules] picked: the modules, its last row, and the grab handle. */
internal fun quickHeight(modules: Int): Dp =
    QuickViewTokens.moduleGap +
        (QuickViewTokens.moduleHeight + QuickViewTokens.moduleGap) * modules +
        QuickViewTokens.jumpHeight +
        PanelTokens.handlePadding * 2 +
        PanelTokens.handleHeight

/** How much a grown panel's quick view shows at [position]: in toward its stop, out toward the next. */
internal fun quickAlpha(position: Float): Float =
    when {
        position <= QUICK_FROM -> 0f
        position <= 1f -> (position - QUICK_FROM) / (1f - QUICK_FROM)
        else -> (1f - (position - 1f) / (FILTERS_FROM - 1f)).coerceAtLeast(0f)
    }

/** How much a grown panel's last stop, such as every filter, shows at [position]. */
internal fun filtersAlpha(position: Float): Float =
    ((position - FILTERS_FROM) / (FILTERS_STOP - FILTERS_FROM)).coerceIn(0f, 1f)

/** A grown panel's last stop, such as every filter. */
internal const val FILTERS_STOP = 2

/** Where the quick view starts to show, as the panel leaves its usual height. */
private const val QUICK_FROM = 0.3f

/** Where the last stop takes over from the quick view, halfway to it. */
internal const val FILTERS_FROM = 1.5f

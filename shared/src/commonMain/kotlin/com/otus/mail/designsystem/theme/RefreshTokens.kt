package com.otus.mail.designsystem.theme

import androidx.compose.ui.unit.dp

internal object RefreshTokens {
    /** Pull distance that triggers a refresh. The same drag is needed every time. */
    val threshold = 80.dp

    /** Where the indicator rests while refreshing. */
    val restOffset = 56.dp

    val indicatorSize = 40.dp
    val arcRadius = 7.5.dp
    val strokeWidth = 2.5.dp
    val arrowWidth = 10.dp
    val arrowHeight = 5.dp

    /** Share of a full turn the arc covers at the threshold. */
    const val MAX_ARC = 0.8f

    /** Arc opacity before the pull reaches the threshold. */
    const val ARMING_ALPHA = 0.3f
    const val FADE_MILLIS = 100
}

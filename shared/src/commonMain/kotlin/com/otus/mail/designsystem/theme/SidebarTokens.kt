package com.otus.mail.designsystem.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object SidebarTokens {
    /** The sidebar half-opens: it covers this share of the screen and the page stays visible beside it. */
    const val WIDTH_FRACTION = 0.64f
    val minWidth = 264.dp
    val maxWidth = 384.dp
    val inset = 8.dp
    val entryGap = 4.dp
    val entryPadding = 12.dp
    val entryRadius = 16.dp
    val titleSize = 22.sp
    val titleLineHeight = 28.sp
    const val SCRIM_ALPHA = 0.65f

    /** A swipe released faster than this, per second, opens or closes the sidebar regardless of position. */
    val flingVelocity = 400.dp

    /** Opening decelerates with [MotionTokens.enter]; closing accelerates away with [MotionTokens.exit]. */
    const val OPEN_MILLIS = 300
    const val CLOSE_MILLIS = 220
}

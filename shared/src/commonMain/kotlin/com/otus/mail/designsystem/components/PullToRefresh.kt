package com.otus.mail.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.pullRefreshIndicatorTransform
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.otus.mail.designsystem.elements.RefreshIndicator
import com.otus.mail.designsystem.theme.RefreshTokens

/**
 * Pulling [content] down past a fixed threshold runs [onRefresh]. A [RefreshIndicator] slides down
 * with the pull and its arc grows and turns with the distance, then spins while [refreshing].
 * A confirming haptic marks a pull that triggers a refresh.
 * Content must scroll vertically, even when it is short, so the pull reaches this container.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val state =
        rememberPullRefreshState(
            refreshing,
            {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onRefresh()
            },
            refreshThreshold = RefreshTokens.threshold,
            refreshingOffset = RefreshTokens.restOffset,
        )
    Box(modifier.pullRefresh(state, enabled)) {
        content()
        RefreshIndicator(
            progress = { state.progress },
            refreshing = refreshing,
            modifier = Modifier.align(Alignment.TopCenter).pullRefreshIndicatorTransform(state),
        )
    }
}

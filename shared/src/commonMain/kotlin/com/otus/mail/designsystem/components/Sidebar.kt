package com.otus.mail.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.PanelTokens
import com.otus.mail.designsystem.theme.SidebarTokens
import com.otus.mail.designsystem.theme.panelTextStyle
import kotlinx.coroutines.launch

/**
 * App-independent navigation sidebar. It half-opens from the leading edge over the current page,
 * and a swipe back toward that edge closes it. Wrap the page in [SidebarSwipeArea] to open it by swiping.
 * Fill [entries] and [footer] with [com.otus.mail.designsystem.elements.SidebarEntry] rows.
 */
@Composable
fun Sidebar(
    state: SidebarState,
    title: String,
    modifier: Modifier = Modifier,
    footer: @Composable ColumnScope.() -> Unit = {},
    entries: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { state.close() } }
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = state.isOpen,
        onBackCompleted = close,
    )
    // One progress value drives the slide and the scrim, so a swipe or a reversal mid-way stays continuous.
    if (!state.isOpen && state.progress == 0f) return
    val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val flingThreshold = with(LocalDensity.current) { SidebarTokens.flingVelocity.toPx() }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .draggable(
                rememberDraggableState { state.dragBy(it * sign) },
                Orientation.Horizontal,
                onDragStarted = { state.stop() },
                onDragStopped = { state.settle(it * sign, flingThreshold) },
            ),
    ) {
        val width = (maxWidth * SidebarTokens.WIDTH_FRACTION).coerceIn(SidebarTokens.minWidth, SidebarTokens.maxWidth)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = state.progress }
                .background(OtusColors.bgDark.copy(alpha = SidebarTokens.SCRIM_ALPHA))
                .semantics { contentDescription = "Close navigation" }
                .clickable(
                    remember { MutableInteractionSource() },
                    indication = null,
                    enabled = state.isOpen,
                    onClick = close,
                ),
        )
        Box(
            Modifier
                .fillMaxHeight()
                .width(width)
                .onSizeChanged { state.widthPx = it.width.toFloat() }
                .graphicsLayer {
                    // Slide fully past the leading screen edge.
                    translationX = -sign * size.width * (1f - state.progress)
                },
        ) {
            SidebarSurface(title, footer, entries)
        }
    }
}

/** Flush with the top, leading, and bottom screen edges, so only the trailing edge, mid-screen, gets a stroke. */
private fun Modifier.trailingStroke(): Modifier =
    drawWithContent {
        drawContent()
        val width = PanelTokens.borderWidth.toPx()
        val x = if (layoutDirection == LayoutDirection.Rtl) width / 2 else size.width - width / 2
        drawLine(OtusColors.strokeBgDark, Offset(x, 0f), Offset(x, size.height), width)
    }

/** The trailing edge sits mid-screen, so it needs no system-bar or cutout padding. */
private val sidebarInsetSides = WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.Start

@Composable
private fun SidebarSurface(
    title: String,
    footer: @Composable ColumnScope.() -> Unit,
    entries: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        Modifier.fillMaxSize().semantics { paneTitle = title }.trailingStroke(),
        color = OtusColors.bgDark,
        contentColor = OtusColors.text1,
    ) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(sidebarInsetSides))
                .padding(horizontal = SidebarTokens.inset),
        ) {
            // Shares the context panel's header height, so the name lines up with the page title.
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(
                        min = PanelTokens.headerHeight,
                    ).padding(horizontal = SidebarTokens.inset),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    title,
                    Modifier.semantics { heading() },
                    style =
                        panelTextStyle().copy(
                            fontSize = SidebarTokens.titleSize,
                            lineHeight = SidebarTokens.titleLineHeight,
                        ),
                )
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SidebarTokens.entryGap),
                content = entries,
            )
            Column(
                Modifier.padding(bottom = SidebarTokens.inset),
                verticalArrangement = Arrangement.spacedBy(SidebarTokens.entryGap),
                content = footer,
            )
        }
    }
}

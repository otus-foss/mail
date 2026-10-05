package com.otus.mail.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otus.mail.designsystem.theme.MotionTokens
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.PanelTokens
import com.otus.mail.designsystem.theme.SidebarTokens
import com.otus.mail.designsystem.theme.panelTextStyle

/**
 * App-independent navigation sidebar. It half-opens from the leading edge over the current page.
 * Fill [entries] and [footer] with [com.otus.mail.designsystem.elements.SidebarEntry] rows.
 */
@Composable
fun Sidebar(
    open: Boolean,
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    footer: @Composable ColumnScope.() -> Unit = {},
    entries: @Composable ColumnScope.() -> Unit,
) {
    // One progress value drives the slide and the scrim, so reversing mid-way stays continuous.
    val progress =
        animateFloatAsState(
            targetValue = if (open) 1f else 0f,
            animationSpec =
                if (open) {
                    tween(SidebarTokens.OPEN_MILLIS, easing = MotionTokens.enter)
                } else {
                    tween(SidebarTokens.CLOSE_MILLIS, easing = MotionTokens.exit)
                },
            label = "Sidebar",
        )
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = open,
        onBackCompleted = onDismiss,
    )
    if (!open && progress.value == 0f) return
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = (maxWidth * SidebarTokens.WIDTH_FRACTION).coerceIn(SidebarTokens.minWidth, SidebarTokens.maxWidth)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(OtusColors.bgDark.copy(alpha = SidebarTokens.SCRIM_ALPHA))
                .semantics { contentDescription = "Close navigation" }
                .clickable(
                    remember { MutableInteractionSource() },
                    indication = null,
                    enabled = open,
                    onClick = onDismiss,
                ),
        )
        Box(
            Modifier
                .fillMaxHeight()
                .safeDrawingPadding()
                .padding(SidebarTokens.inset)
                .width(width)
                .graphicsLayer {
                    // Slide fully past the leading screen edge, including the inset.
                    val hidden = (size.width + SidebarTokens.inset.toPx()) * (1f - progress.value)
                    translationX = if (rtl) hidden else -hidden
                },
        ) {
            SidebarSurface(title, footer, entries)
        }
    }
}

@Composable
private fun SidebarSurface(
    title: String,
    footer: @Composable ColumnScope.() -> Unit,
    entries: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        Modifier.fillMaxSize().semantics { paneTitle = title },
        shape = RoundedCornerShape(PanelTokens.panelRadius),
        color = OtusColors.bgDark,
        contentColor = OtusColors.text1,
        border = BorderStroke(PanelTokens.borderWidth, OtusColors.strokeBgDark),
    ) {
        Column(Modifier.padding(horizontal = SidebarTokens.inset)) {
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

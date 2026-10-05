package com.otus.mail.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.otus.mail.designsystem.theme.MotionTokens
import com.otus.mail.designsystem.theme.PanelTokens
import com.otus.mail.designsystem.theme.panelTextStyle
import kotlin.math.roundToInt

/** Placement is independent of whether the header contains a title, input, or richer content. */
enum class ChipPlacement { Below, Overlapping }

/** Feature-independent surface. Each [chips] item needs a stable key and a pill-shaped visual. */
@Composable
fun ContextPanel(
    modifier: Modifier = Modifier,
    placement: ChipPlacement = ChipPlacement.Below,
    navigation: (@Composable () -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null,
    chips: @Composable ContextPanelScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val overlap =
        animateFloatAsState(
            targetValue = if (placement == ChipPlacement.Overlapping) 1f else 0f,
            animationSpec = tween(PanelTokens.MODE_TRANSITION_MILLIS, easing = MotionTokens.standard),
            label = "Panel surface expansion",
        )
    val geometry = remember { PanelLayoutState() }
    val scope = remember(geometry) { ContextPanelScope(geometry) }
    val scroll = rememberScrollState()
    Box(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned(geometry::trackPanel)
            .panelSurface(geometry) { overlap.value },
    ) {
        Column {
            PanelHeader(
                Modifier.onGloballyPositioned(geometry::trackHeader),
                navigation,
                content,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        top =
                            if (placement ==
                                ChipPlacement.Below
                            ) {
                                PanelTokens.detachedGap
                            } else {
                                0.dp
                            },
                    ).onGloballyPositioned(geometry::trackRail)
                    .railInset { overlap.value },
                horizontalArrangement = Arrangement.spacedBy(PanelTokens.iconGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PanelChipRail(Modifier.weight(1f), geometry, scroll) { scope.chips() }
                if (trailingAction != null) {
                    scope.item(ActionKey) { trailingAction() }
                }
            }
        }
    }
}

internal object ActionKey

/**
 * Detached pills span the panel's full width; overlapping pills sit inside its edge, clear of the
 * rounded corners. The inset follows the surface animation, so pills never jump sideways.
 */
private fun Modifier.railInset(overlap: () -> Float): Modifier =
    layout { measurable, constraints ->
        val inset = (PanelTokens.panelInset.toPx() * overlap()).roundToInt()
        val placeable = measurable.measure(constraints.offset(horizontal = -inset * 2))
        layout(placeable.width + inset * 2, placeable.height) { placeable.placeRelative(inset, 0) }
    }

@Composable
private fun PanelHeader(
    modifier: Modifier,
    navigation: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = PanelTokens.headerHeight).padding(end = PanelTokens.panelInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigation?.invoke()
        Box(
            Modifier
                .weight(1f)
                .padding(
                    start = if (navigation == null) PanelTokens.panelInset else PanelTokens.iconGap,
                    top = PanelTokens.panelInset,
                    bottom = PanelTokens.panelInset,
                ).heightIn(min = PanelTokens.touchTarget),
            contentAlignment = Alignment.CenterStart,
        ) { ProvideTextStyle(panelTextStyle()) { content() } }
    }
}

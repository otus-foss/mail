package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.otusfoss.core.designsystem.shared.feedback.components.PullToRefresh
import com.otusfoss.core.designsystem.shared.panels.components.PanelExpansion
import com.otusfoss.core.designsystem.shared.panels.components.PanelScrim

/**
 * A [panel] drawn over a pull-to-refresh [list], so the list shows behind the panel's pills. The
 * list gets the expanded panel's height as its top inset and hands its scroll through a
 * [CollapseLink], whose progress collapses the panel. The inset keeps the expanded height while
 * the panel collapses, so rows never jump as it shrinks. While [holdInset], such as while rows are
 * selected and the panel shows a shorter header, the inset keeps its height too. While the
 * panel's [expansion] grows it, the inset keeps its height and a scrim dims the list; a tap on the
 * scrim brings the panel back.
 *
 * While [rowsShown], a load that runs behind them shows no spinner unless the user pulled for it;
 * an empty list shows the spinner for every load.
 */
@Composable
internal fun PanelOverList(
    loading: Boolean,
    rowsShown: Boolean,
    onRefresh: () -> Unit,
    holdInset: Boolean = false,
    expansion: PanelExpansion? = null,
    panel: @Composable (collapse: () -> Float, modifier: Modifier) -> Unit,
    list: @Composable (topInset: Dp, collapse: CollapseLink) -> Unit,
) {
    val collapse = remember { CollapseLink() }
    var panelHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(loading) { if (!loading) pulled = false }
    Box(Modifier.fillMaxSize()) {
        PullToRefresh(
            refreshing = loading && (pulled || !rowsShown),
            onRefresh = {
                pulled = true
                onRefresh()
            },
            Modifier.fillMaxSize(),
            indicatorInset = panelHeight,
        ) {
            list(panelHeight, collapse)
        }
        expansion?.let { PanelScrim(it) }
        panel(
            collapse::progress,
            Modifier.onSizeChanged {
                val still = !holdInset && collapse.progress() == 0f && expansion?.expanded != true
                if (still) panelHeight = with(density) { it.height.toDp() }
            },
        )
    }
}

package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.otus.designsystem.components.PullToRefresh
import com.otus.designsystem.theme.InboxTokens

/**
 * A [panel] drawn over a pull-to-refresh [list], so the list shows behind the panel's pills. The
 * list gets the full panel's height as its top inset, and reports through its `onCompactChange`
 * when the panel should turn compact. The inset keeps the full height while compact, so rows
 * never jump as the panel shrinks.
 *
 * While [rowsShown], a load that runs behind them shows no spinner unless the user pulled for it;
 * an empty list shows the spinner for every load.
 */
@Composable
internal fun PanelOverList(
    loading: Boolean,
    rowsShown: Boolean,
    onRefresh: () -> Unit,
    panel: @Composable (compact: Boolean, modifier: Modifier) -> Unit,
    list: @Composable (topInset: Dp, onCompactChange: (Boolean) -> Unit) -> Unit,
) {
    var compact by remember { mutableStateOf(false) }
    var panelHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(loading) { if (!loading) pulled = false }
    Box(Modifier.fillMaxSize().padding(vertical = InboxTokens.pageInset)) {
        PullToRefresh(
            refreshing = loading && (pulled || !rowsShown),
            onRefresh = {
                pulled = true
                onRefresh()
            },
            Modifier.fillMaxSize(),
            indicatorInset = panelHeight,
        ) {
            list(panelHeight) { compact = it }
        }
        panel(compact, Modifier.onSizeChanged { if (!compact) panelHeight = with(density) { it.height.toDp() } })
    }
}

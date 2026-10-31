package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.designsystem.shared.panels.components.PanelCollapse

/**
 * Hands the shown list's [PanelCollapse] to the control panel drawn over it. The inbox and its
 * search results scroll separately, so the [source] changes with them.
 */
internal class CollapseLink {
    var source: PanelCollapse? by mutableStateOf(null)

    fun progress(): Float = source?.progress ?: 0f
}

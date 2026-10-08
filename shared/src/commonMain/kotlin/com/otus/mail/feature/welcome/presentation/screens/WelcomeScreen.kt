package com.otus.mail.feature.welcome.presentation.screens

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.otus.designsystem.elements.Chip
import com.otus.mail.feature.welcome.presentation.components.DemoFilters
import com.otus.mail.feature.welcome.presentation.components.DemoModePicker
import com.otus.mail.feature.welcome.presentation.components.DemoScaffold
import com.otus.mail.feature.welcome.presentation.components.PanelDemo
import com.otus.mail.feature.welcome.presentation.components.PanelDemoAction
import com.otus.mail.feature.welcome.presentation.components.PanelDemoMode

@Composable
fun WelcomeScreen() {
    var mode by rememberSaveable { mutableStateOf(PanelDemoMode.Inbox) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var overlap by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("Tap search to try the overlapping layout.") }
    val keyboard = LocalSoftwareKeyboardController.current
    val changeMode: (PanelDemoMode) -> Unit = {
        mode = it
        overlap = it != PanelDemoMode.Inbox
        keyboard?.hide()
    }
    val toggle: (String) -> Unit = { label ->
        selected = if (label in selected) selected - label else selected + label
    }
    DemoScaffold {
        DemoModePicker(mode, changeMode)
        PanelDemo(
            mode = mode,
            overlap = overlap,
            query = query,
            selected = selected,
            onQueryChange = { query = it },
            onChipClick = toggle,
            onAction = { action ->
                when (action) {
                    PanelDemoAction.Back -> {
                        changeMode(PanelDemoMode.Inbox)
                    }

                    PanelDemoAction.Search -> {
                        changeMode(PanelDemoMode.Search)
                    }

                    PanelDemoAction.Filters -> {
                        showFilters = true
                    }

                    PanelDemoAction.Submit -> {
                        status = if (query.isBlank()) "Enter a search query." else "Demo query: $query"
                        keyboard?.hide()
                    }
                }
            },
        )
        Chip("Overlap chips", { overlap = !overlap }, selected = overlap)
        Text(status)
        Text("Selected: ${selected.joinToString().ifEmpty { "None" }}")
        Text("UI preview only · mail accounts and search are not connected.")
    }
    if (showFilters) DemoFilters(selected.toSet(), toggle) { showFilters = false }
}

package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_add
import com.otus.designsystem.resources.symbol_label
import com.otus.designsystem.shared.controls.elements.PlainAction
import com.otus.designsystem.shared.overlays.components.ClosableSheet
import com.otus.designsystem.shared.overlays.components.ConfirmDialog
import com.otus.designsystem.shared.rows.elements.RowButton
import com.otus.designsystem.shared.settings.components.SettingRow
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.SheetTokens

/** The custom labels in use, how many cards carry each, and removing one from all of them. */
internal class ContactLabels(
    val names: List<String>,
    val uses: (String) -> Int,
    val delete: (String) -> Unit,
)

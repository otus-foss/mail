package com.otus.mail.feature.folders.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.AddRuleChip
import com.otus.designsystem.components.RuleChip
import com.otus.designsystem.components.RuleChipGroup
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_filter_alt
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.feature.folders.domain.FolderRule
import com.otus.mail.feature.folders.presentation.chipTexts

/**
 * The Rules row and the folder's [rules] under it as full-width chips, lined up with the row's
 * edge: one condition as a chip, several as an "All of" group, then Add rule. Tapping a rule calls
 * [onOpen] with its index.
 */
@Composable
internal fun FolderRulesSection(
    rules: List<FolderRule>,
    onOpen: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    SettingRow(
        "Rules",
        onClick = null,
        description = "New mail that matches any of these goes here",
        icon = Res.drawable.symbol_filter_alt,
    )
    Column(
        Modifier.padding(horizontal = PageTokens.textInset),
        verticalArrangement = Arrangement.spacedBy(PageTokens.rowGap),
    ) {
        rules.forEachIndexed { index, rule ->
            val lines = rule.chipTexts().map { it.chipLine() }
            val wide = Modifier.fillMaxWidth()
            if (rule.isGroup) {
                RuleChipGroup("ALL OF", lines, wide) { onOpen(index) }
            } else {
                RuleChip(lines.single(), wide) { onOpen(index) }
            }
        }
        AddRuleChip("Add rule", onAdd, Modifier.fillMaxWidth())
    }
}

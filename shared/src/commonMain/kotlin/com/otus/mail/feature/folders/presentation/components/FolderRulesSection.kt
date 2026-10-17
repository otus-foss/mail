package com.otus.mail.feature.folders.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.AddRuleChip
import com.otus.designsystem.components.PageNote
import com.otus.designsystem.components.RuleChip
import com.otus.designsystem.components.RuleChipGroup
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.components.mutedRule
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_filter_alt
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.feature.folders.domain.FolderRule
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.presentation.ParentRules
import com.otus.mail.feature.folders.presentation.chipTexts

/**
 * The Rules row and the folder's [rules] under it as full-width chips, lined up with the row's
 * edge: one condition as a chip, several as an "All of" group, then Add rule. Tapping a rule calls
 * [onOpen] with its index. The [parents]' rules follow, faded, because the server checks them
 * first; tapping one calls [onOpenParent].
 */
@Composable
internal fun FolderRulesSection(
    rules: List<FolderRule>,
    parents: List<ParentRules>,
    onOpenParent: (MailFolder) -> Unit,
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
        rules.forEachIndexed { index, rule -> FolderRuleChip(rule, Modifier) { onOpen(index) } }
        AddRuleChip("Add rule", onAdd, Modifier.fillMaxWidth())
    }
    parents.forEach { parent ->
        PageNote("Checked first, in ${parent.folder.name}. Mail that matches goes there.")
        Column(
            Modifier.padding(horizontal = PageTokens.textInset),
            verticalArrangement = Arrangement.spacedBy(PageTokens.rowGap),
        ) {
            parent.rules.forEach { rule -> FolderRuleChip(rule, Modifier.mutedRule()) { onOpenParent(parent.folder) } }
        }
    }
}

/** One rule as a full-width chip, or an "All of" group when it has several conditions. */
@Composable
internal fun FolderRuleChip(
    rule: FolderRule,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val lines = rule.chipTexts().map { it.chipLine() }
    val wide = modifier.fillMaxWidth()
    if (rule.isGroup) {
        RuleChipGroup(
            "ALL OF",
            lines,
            wide,
            onClick,
        )
    } else {
        RuleChip(lines.single(), wide, onClick = onClick)
    }
}

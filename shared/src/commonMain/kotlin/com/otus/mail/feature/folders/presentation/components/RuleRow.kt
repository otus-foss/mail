package com.otus.mail.feature.folders.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.DetailRow
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_drive_file_move
import com.otus.mail.feature.folders.domain.MailRule
import com.otus.mail.feature.folders.presentation.summary
import com.otus.mail.feature.folders.presentation.title

/** Maps a server rule into a shared row. */
@Composable
fun RuleRow(
    rule: MailRule,
    folderPath: String?,
    onClick: () -> Unit,
) {
    val target = folderPath ?: "a deleted folder (mail stays in the inbox)"
    val details =
        listOfNotNull(
            rule.summary().takeIf { rule.name.isNotBlank() },
            "Moves to $target",
            "Paused".takeUnless { rule.enabled },
        )
    DetailRow(rule.title(), details, onClick, enabled = rule.enabled) {
        Symbol(Res.drawable.symbol_drive_file_move)
    }
}

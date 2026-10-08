package com.otus.mail.feature.folders.presentation.components

/** Everything the rules popups can ask for. */
class RulesActions(
    val onEdit: (accountId: String, ruleId: String) -> Unit,
    val onMove: (accountId: String, ruleId: String, offset: Int) -> Unit,
    val onPause: (accountId: String, ruleId: String, paused: Boolean) -> Unit,
    val onDelete: (accountId: String, ruleId: String) -> Unit,
    val onTurnOn: (accountId: String, keepOther: Boolean) -> Unit,
)

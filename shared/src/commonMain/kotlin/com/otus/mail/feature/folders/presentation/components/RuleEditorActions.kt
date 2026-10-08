package com.otus.mail.feature.folders.presentation.components

import com.otus.mail.feature.folders.presentation.RuleDraft

/** Everything the rule editor can ask for. [onDelete] is null for a rule that is not saved yet. */
class RuleEditorActions(
    val onChange: (RuleDraft) -> Unit,
    val onCreateFolder: (String) -> Unit,
    val onSave: () -> Unit,
    val onDelete: (() -> Unit)?,
)

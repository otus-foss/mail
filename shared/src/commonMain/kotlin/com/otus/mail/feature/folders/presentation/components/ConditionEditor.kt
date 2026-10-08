package com.otus.mail.feature.folders.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.ChoiceDialog
import com.otus.designsystem.elements.ActionButton
import com.otus.designsystem.elements.IconButton
import com.otus.designsystem.elements.InputField
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.designsystem.resources.Res
import com.otus.mail.designsystem.resources.symbol_close
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.RuleField
import com.otus.mail.feature.folders.domain.RuleOperator
import com.otus.mail.feature.folders.domain.RuleSupport
import com.otus.mail.feature.folders.presentation.example
import com.otus.mail.feature.folders.presentation.label

/**
 * One condition: which part of the message, how to compare, and the text to compare with. Only
 * fields and operators the server supports are offered. [onRemove] is null for the last condition.
 */
@Composable
fun ConditionEditor(
    condition: RuleCondition,
    support: RuleSupport,
    position: Int,
    onChange: (RuleCondition) -> Unit,
    onRemove: (() -> Unit)?,
) {
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(PageTokens.rowGap)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(PageTokens.rowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionButton(condition.field.label, { picking = FIELD }, Modifier.weight(1f))
            ActionButton(condition.operator.label, { picking = OPERATOR }, Modifier.weight(1f))
            onRemove?.let {
                IconButton("Remove condition $position", it, outlined = false) { Symbol(Res.drawable.symbol_close) }
            }
        }
        InputField(
            "Condition $position text",
            condition.value,
            { onChange(condition.copy(value = it)) },
            placeholder = condition.field.example,
        )
    }
    when (picking) {
        FIELD -> {
            ChoiceDialog(
                title = "Match on",
                options = RuleField.entries.filter(support::supports),
                selected = condition.field,
                label = { it.label },
                onSelect = { field ->
                    // Keep the comparison when the new field offers it, so the user does not lose their choice.
                    val operator = condition.operator.takeIf { it in field.operators } ?: RuleOperator.Contains
                    onChange(condition.copy(field = field, operator = operator))
                },
                onDismiss = { picking = null },
            )
        }

        OPERATOR -> {
            ChoiceDialog(
                title = "Compare",
                options = condition.field.operators.filter(support::supports),
                selected = condition.operator,
                label = { it.label },
                onSelect = { onChange(condition.copy(operator = it)) },
                onDismiss = { picking = null },
            )
        }
    }
}

private const val FIELD = "field"
private const val OPERATOR = "operator"

package com.otus.mail.feature.folders.presentation

import androidx.compose.runtime.saveable.Saver

/** The open popup on the rules page, by id, so it survives reloads and saved state. */
sealed interface RulesDialog {
    val accountId: String

    data class Actions(
        override val accountId: String,
        val ruleId: String,
    ) : RulesDialog

    data class Delete(
        override val accountId: String,
        val ruleId: String,
    ) : RulesDialog

    /** Asks before the rules replace a script that runs now. */
    data class TurnOn(
        override val accountId: String,
    ) : RulesDialog

    companion object {
        val Saver: Saver<RulesDialog?, List<String>> =
            Saver(
                save = { dialog ->
                    when (dialog) {
                        is Actions -> listOf(ACTIONS, dialog.accountId, dialog.ruleId)
                        is Delete -> listOf(DELETE, dialog.accountId, dialog.ruleId)
                        is TurnOn -> listOf(TURN_ON, dialog.accountId, "")
                        null -> null
                    }
                },
                restore = { (kind, accountId, ruleId) ->
                    when (kind) {
                        ACTIONS -> Actions(accountId, ruleId)
                        DELETE -> Delete(accountId, ruleId)
                        TURN_ON -> TurnOn(accountId)
                        else -> null
                    }
                },
            )

        private const val ACTIONS = "actions"
        private const val DELETE = "delete"
        private const val TURN_ON = "turnOn"
    }
}

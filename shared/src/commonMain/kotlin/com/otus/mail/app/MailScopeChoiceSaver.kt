package com.otus.mail.app

import androidx.compose.runtime.saveable.listSaver
import com.otus.mail.core.messages.domain.MailScopeChoice

/** Saves the "Show mail from" choice for the life of the process, as the page is saved. */
internal val MailScopeChoiceSaver =
    listSaver<MailScopeChoice, String>(
        save = { choice ->
            when (choice) {
                MailScopeChoice.All -> emptyList()
                is MailScopeChoice.Account -> listOf(choice.accountId)
                is MailScopeChoice.Address -> listOf(choice.accountId, choice.address)
            }
        },
        restore = { saved ->
            when (saved.size) {
                1 -> MailScopeChoice.Account(saved[0])
                2 -> MailScopeChoice.Address(saved[0], saved[1])
                else -> MailScopeChoice.All
            }
        },
    )

package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.MoveTarget

/**
 * The Move to sheet of an action bar or menu. [choose] opens it; picking a folder closes it, runs
 * [onChosen], such as closing the page the messages leave, and then moves them.
 */
internal class MoveChoice(
    private val onChosen: () -> Unit,
) {
    private var request by mutableStateOf<Pair<List<MailMessage>, List<MoveTarget>>?>(null)

    /** Opens the sheet for [messages], offering the [targets] every one of them can move to. */
    fun choose(
        messages: List<MailMessage>,
        targets: List<MoveTarget>,
    ) {
        request = messages to targets
    }

    @Composable
    fun Sheet(actions: MessageActions) {
        val (messages, targets) = request ?: return
        MoveSheet(
            messages,
            targets,
            actions,
            onLeave = { perform ->
                request = null
                onChosen()
                perform()
            },
            onDismiss = { request = null },
        )
    }
}

@Composable
internal fun rememberMoveChoice(onChosen: () -> Unit): MoveChoice = remember { MoveChoice(onChosen) }

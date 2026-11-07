package com.otus.mail.feature.read.presentation.components

import androidx.compose.runtime.Composable
import com.otus.mail.core.messages.domain.ActionBarPlace
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.OwnAddresses
import com.otus.mail.core.messages.domain.replyAction
import com.otus.mail.core.messages.presentation.MessageActions
import com.otus.mail.core.messages.presentation.actions.ActionSubject
import com.otus.mail.core.messages.presentation.actions.MailBar
import com.otus.mail.core.messages.presentation.actions.OfferedAction
import com.otus.mail.core.messages.presentation.actions.barFor
import com.otus.mail.core.messages.presentation.actions.offer
import com.otus.mail.core.messages.presentation.actions.showsSuggestedMove
import com.otus.mail.core.messages.presentation.components.MailActionBar
import com.otus.mail.core.messages.presentation.components.MoveChoice
import com.otus.mail.core.messages.presentation.components.rememberSuggestedMove
import com.otus.mail.core.messages.presentation.components.replyContext

/** The [bar], with the big button saying who the reply to the [page] goes to. */
@Composable
internal fun ReadActionBar(
    bar: MailBar,
    page: MailMessage,
    onAction: (OfferedAction) -> Unit,
) {
    MailActionBar(bar, onAction, context = replyContext(page, bar.layout.primary))
}

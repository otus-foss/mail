package com.otusfoss.mail.feature.read.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.otusfoss.core.designsystem.mail.read.components.MailLine
import com.otusfoss.core.designsystem.mail.read.components.MoreMail
import com.otusfoss.core.settings.presentation.components.LocalDateFormatter
import com.otusfoss.mail.core.messages.domain.shownName
import com.otusfoss.mail.core.messages.presentation.shortLabel
import com.otusfoss.mail.feature.read.domain.SenderMail

/**
 * The newest other conversations from the open page's sender, under a header that folds. It starts
 * unfolded on a message without files, which leaves the panel room for it, and is absent while the
 * sender has no other mail. A tap closes this page and opens that mail the way a notification
 * does.
 */
@Composable
internal fun ReadMoreMail(room: ReadRoom) {
    val services = room.parts.read.services
    val source = services.senderMails ?: return
    val openMail = services.openMail ?: return
    val page = room.page
    val shown =
        room.state.pages
            .map { it.emailId }
            .toSet()
    val mails by produceState(emptyList<SenderMail>(), page.id) { value = source.newest(page, shown) }
    if (mails.isEmpty()) return
    var unfolded by remember(page.id) { mutableStateOf(room.files() == null) }
    val dates = LocalDateFormatter.current
    val today =
        room.parts.read.display
            .today()
    MoreMail(
        "More from ${page.sender.shownName}",
        mails.map {
            MailLine(
                it.subject.ifBlank { NO_SUBJECT },
                it.preview,
                dates.shortLabel(it.received, today),
                it.unread,
            )
        },
        unfolded,
        onToggle = { unfolded = !unfolded },
        onOpen = { index ->
            room.parts.close()
            openMail(page.location.accountId, mails[index].emailId)
        },
    )
}

private const val NO_SUBJECT = "No subject"

package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otusfoss.mail.core.messages.domain.MailMessage

/**
 * A row the list opened, for its Read view: [notice] shows under the header, and [mixed] says
 * search, a folder pill, or folder filters mix folders, so the view names none the row came from.
 */
class OpenedMessage(
    val message: MailMessage,
    val notice: String?,
    val mixed: Boolean,
    val close: () -> Unit,
)

/** The Read view a list shows over itself for an opened row; the app supplies it. */
fun interface ReadView {
    @Composable
    fun Show(opened: OpenedMessage)
}

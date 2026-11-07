package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import com.otus.mail.app.App
import com.otus.mail.core.messages.presentation.components.LocalToday
import com.otus.mail.core.settings.domain.MailBehavior
import com.otus.mail.core.settings.domain.ReadBehavior
import com.otus.mail.core.settings.presentation.MailBehaviorState

/**
 * Shows the app signed in to [server], waiting until its inbox, folders, and rules have loaded, so
 * folder actions are offered.
 */
internal fun AndroidComposeTestRule<*, MainActivity>.showInbox(
    server: FakeFolderServer,
    behaviorState: MailBehaviorState =
        MailBehaviorState(initial = MailBehavior(read = ReadBehavior(markReadOnOpen = false))),
) {
    runOnUiThread {
        activity.setContent {
            // Dates read the same in every year the Move sheet screenshots are compared.
            CompositionLocalProvider(LocalToday provides PinnedToday) {
                App(services = server.services(), behaviorState = behaviorState)
            }
        }
    }
    waitForNode(hasText("Welcome"))
    waitForCall(server, "SieveScript/get")
    waitForIdle()
}

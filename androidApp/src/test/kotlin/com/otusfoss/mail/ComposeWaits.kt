package com.otusfoss.mail

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule

/** How long a UI test waits for the app, such as for a fake server's answer to show. */
private const val WAIT_MILLIS = 5_000L

/** Waits until some node matches [matcher]. */
internal fun ComposeTestRule.waitForNode(matcher: SemanticsMatcher) =
    waitUntil(WAIT_MILLIS) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

/** Waits until some node's text contains [text]. */
internal fun ComposeTestRule.waitForText(text: String) = waitForNode(hasText(text, substring = true))

/** Waits until the app has sent [server] a request that mentions [method]. */
internal fun ComposeTestRule.waitForCall(
    server: FakeFolderServer,
    method: String,
) = waitUntil(WAIT_MILLIS) { server.calls.any { method in it } }

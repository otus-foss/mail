package com.otus.mail.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MailVisibilityTest {
    private val changes = ServerChanges()
    private val visibility = MailVisibility()
    private var reloads = 0

    @Test
    fun pushesReloadOnlyWhileMailIsShownAndOnceWhenItComesBack() =
        runTest {
            backgroundScope.launch { reloadWhileShown(changes.changes, visibility.shown) { reloads++ } }
            runCurrent()
            push()
            assertEquals(1, reloads)

            visibility.hide()
            runCurrent()
            push()
            push()
            assertEquals(1, reloads, "Nothing loads while Mail is off screen")
            visibility.show()
            runCurrent()
            assertEquals(2, reloads, "One reload catches up on what came meanwhile")
        }

    @Test
    fun showingAgainWithoutPushesReloadsNothing() =
        runTest {
            backgroundScope.launch { reloadWhileShown(changes.changes, visibility.shown) { reloads++ } }
            runCurrent()
            visibility.hide()
            runCurrent()
            visibility.show()
            runCurrent()
            assertEquals(0, reloads)
        }

    private fun TestScope.push() {
        changes.changed()
        runCurrent()
    }
}

package com.otusfoss.mail.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PageRequestsTest {
    @Test
    fun aPageAskedForWhileNoAppRunsWaitsForTheNextOne() =
        runTest {
            val requests = PageRequests(backgroundScope)
            var taken = false
            backgroundScope.launch {
                requests.show(MailPage.Settings)
                taken = true
            }
            runCurrent()
            assertFalse(taken, "No page has taken it yet")
            val shown = mutableListOf<MailPage>()
            backgroundScope.launch { requests.collect { shown += it } }
            runCurrent()
            assertEquals(listOf<MailPage>(MailPage.Settings), shown)
            assertTrue(taken)
        }

    @Test
    fun anAppThatLeftLeavesLaterPagesToTheNextOne() =
        runTest {
            val requests = PageRequests(backgroundScope)
            val recreated = backgroundScope.launch { requests.collect { error("The old activity is gone") } }
            runCurrent()
            recreated.cancel()
            requests.showLater(MailPage.Folders)
            runCurrent()
            val shown = mutableListOf<MailPage>()
            backgroundScope.launch { requests.collect { shown += it } }
            runCurrent()
            assertEquals(listOf<MailPage>(MailPage.Folders), shown)
        }
}

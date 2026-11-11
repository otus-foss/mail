package com.otusfoss.mail.feature.notifications.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** A check reads every page of `Email/changes`, so a large sync elsewhere still alerts the newest mail. */
class NewMailChangePagesTest {
    @Test
    fun newMailAfterSixHundredUpdatesIsAlerted() =
        runTest {
            val states = States(mapOf("ada" to "s0"))
            val shown =
                checkMail(states) { body ->
                    val page =
                        Regex("\"sinceState\":\"s(\\d+)\"")
                            .find(body)
                            ?.groupValues
                            ?.get(1)
                            ?.toInt()
                    when {
                        page == null -> {
                            """{"methodResponses": [
                               ["Mailbox/get", {"state": "b1", "list": [{"id": "i", "role": "inbox"}]}, "c0"],
                               ["Email/get", {"state": "s7", "list": [${emailJson("new", "i", "{}")}]}, "c1"]]}"""
                        }

                        page < 6 -> {
                            val updated = (0 until 100).joinToString { "\"u$page-$it\"" }
                            """{"methodResponses": [["Email/changes", {"oldState": "s$page", "newState": "s${page + 1}",
                               "hasMoreChanges": true, "updated": [$updated]}, "c0"]]}"""
                        }

                        else -> {
                            """{"methodResponses": [["Email/changes", {"oldState": "s6", "newState": "s7",
                               "hasMoreChanges": false, "created": ["new"]}, "c0"]]}"""
                        }
                    }
                }
            assertEquals(listOf("new"), shown.map { it.emailId })
            assertEquals(mapOf("ada" to "s7"), states.saved)
        }

    @Test
    fun changesThatNeverEndStartAgainFromNow() =
        runTest {
            val states = States(mapOf("ada" to "s0"))
            val shown =
                checkMail(states) { body ->
                    if ("Email/changes" in body) {
                        """{"methodResponses": [["Email/changes", {"oldState": "s0", "newState": "s0",
                           "hasMoreChanges": true, "created": ["old"]}, "c0"]]}"""
                    } else {
                        """{"methodResponses": [["Email/get", {"state": "e9", "list": []}, "c0"]]}"""
                    }
                }
            assertEquals(emptyList(), shown.map { it.emailId })
            assertEquals(mapOf("ada" to "e9"), states.saved)
        }
}

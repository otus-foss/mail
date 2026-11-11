package com.otusfoss.mail.feature.notifications.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Alert text drops the hidden characters and line breaks a sender could use to fake another sender. */
class AlertTextTest {
    @Test
    fun hiddenCharactersAndLineBreaksAreRemoved() =
        runTest {
            val shown =
                checkMail(States(mapOf("ada" to "e1"))) { body ->
                    if ("Email/changes" in body) {
                        """{"methodResponses": [["Email/changes", {"oldState": "e1", "newState": "e2",
                           "hasMoreChanges": false, "created": ["m1"]}, "c0"]]}"""
                    } else {
                        val email =
                            emailJson("m1", "i", "{}", name = "Grace\\u202E Hopper")
                                .replace("Hello m1", "Hello\\r\\nFrom: Bank")
                                .replace("\"Preview\"", "\"Pre\\u2066view\"")
                        """{"methodResponses": [
                           ["Mailbox/get", {"state": "b1", "list": [{"id": "i", "role": "inbox"}]}, "c0"],
                           ["Email/get", {"state": "e2", "list": [$email]}, "c1"]]}"""
                    }
                }
            val mail = shown.single()
            assertEquals("Grace Hopper", mail.sender)
            assertEquals("Hello From: Bank", mail.subject)
            assertEquals("Preview", mail.preview)
        }
}

package com.otusfoss.mail.feature.compose.data

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.SUBMISSION_SESSION
import com.otusfoss.mail.testing.composeMail
import com.otusfoss.mail.testing.stalwartLike

/** A fake server for sending tests, which keeps each request body in [calls]. */
internal class SenderServer {
    private val login = ADA_LOGIN
    val calls = mutableListOf<String>()

    /**
     * A server with the identities in [identities] and a Sent folder unless [sent] is false. The
     * submission ends with [submitted], and an `Email/set` after it ends with [tidied].
     */
    fun sender(
        session: String = SUBMISSION_SESSION,
        identities: String = """{"id": "i1", "email": "ada@example.org"}""",
        sent: Boolean = true,
        submitted: String = """"created": {"send": {"id": "s1"}}""",
        tidied: String = """"updated": {"m1": null}, "destroyed": ["old"]""",
        originalKeywords: String = "{}",
    ): JmapDraftSender {
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        val engine =
            stalwartLike(session) { body ->
                calls += body
                when {
                    "Identity/get" in body -> {
                        val sentFolder = if (sent) """, {"id": "se", "role": "sent"}""" else ""
                        responses(
                            """["Mailbox/get", {"state": "m", "list": [{"id": "dr", "role": "drafts"}$sentFolder]}, "c0"]""",
                            """["Identity/get", {"state": "i", "list": [$identities]}, "c1"]""",
                        )
                    }

                    "Mailbox/set" in body -> {
                        responses(
                            """["Mailbox/set", {"newState": "m2", "created": {"scheduled": {"id": "sc"}}}, "c0"]""",
                        )
                    }

                    "EmailSubmission/set" in body -> {
                        responses("""["EmailSubmission/set", {"newState": "s2", $submitted}, "c0"]""")
                    }

                    "Email/set" in body && "\"create\"" in body -> {
                        responses("""["Email/set", {"newState": "e2", "created": {"draft": {"id": "e9"}}}, "c0"]""")
                    }

                    "Email/set" in body -> {
                        responses("""["Email/set", {"newState": "e3", $tidied}, "c0"]""")
                    }

                    // The copy a send tidies up is still a draft.
                    "Email/get" in body && "\"old\"" in body -> {
                        responses(
                            """["Email/get", {"state": "e", "list": [{"id": "old", $RECEIVED, "keywords": {"${'$'}draft": true}}]}, "c0"]""",
                        )
                    }

                    "Email/get" in body -> {
                        responses(
                            """["Email/get", {"state": "e", "list": [{"id": "m1", $RECEIVED, "keywords": $originalKeywords}]}, "c0"]""",
                        )
                    }

                    else -> {
                        error("Unexpected call: $body")
                    }
                }
            }
        return composeMail(JmapConnections(JmapClient(engine), store)).sender
    }

    private fun responses(vararg items: String) = """{"methodResponses": [${items.joinToString()}]}"""

    private companion object {
        const val RECEIVED = """"receivedAt": "2026-10-31T12:00:00Z""""
    }
}

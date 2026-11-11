package com.otusfoss.mail.app

import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.core.jmap.login.JmapLogin
import com.otusfoss.mail.feature.notifications.domain.AlertedMail
import com.otusfoss.mail.feature.notifications.domain.InstantPush
import com.otusfoss.mail.feature.notifications.domain.MailAlerts
import com.otusfoss.mail.feature.notifications.domain.NewMail
import com.otusfoss.mail.feature.notifications.domain.PushDeliveryStatus
import com.otusfoss.mail.feature.notifications.domain.PushDistributor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Removing an account takes its new-mail alerts and its push registration with it. */
class AlertRemovalTest {
    private val ada = JmapLogin("ada", "https://mail.example.org/jmap/session", "ada@example.org", "Ada")
    private val bob = JmapLogin("bob", "https://mail.example.com/jmap/session", "bob@example.com", "Bob")
    private val logins =
        InMemoryLoginStore().apply {
            save(ada, "secret")
            save(bob, "secret")
        }

    @Test
    fun removingAnAccountClearsOnlyItsAlerts() =
        runTest {
            val alerts = Alerts(AlertedMail("ada", "m1"), AlertedMail("ada", "m2"), AlertedMail("bob", "m3"))

            MailServices(logins, host = MailPlatform(alerts = alerts)).accounts.remove("ada")

            assertEquals(setOf(AlertedMail("bob", "m3")), alerts.showing())
        }

    @Test
    fun removingAnAccountForgetsItsPushBeforeAndAfterItsCredentialsGo() =
        runTest {
            val push = Push { loginId -> logins.logins().any { it.id == loginId } }

            MailServices(logins, host = MailPlatform(instantPush = push)).accounts.remove("ada")

            // The second time undoes a registration by a push sync that still saw the login.
            assertEquals(listOf("ada" to true, "ada" to false), push.forgotten)
        }

    private class Alerts(
        vararg showing: AlertedMail,
    ) : MailAlerts {
        private var current = showing.toSet()

        override fun showing() = current

        override fun show(mail: List<NewMail>) = Unit

        override fun clear(mail: List<AlertedMail>) {
            current = current - mail.toSet()
        }
    }

    /** Records each login it forgets, with whether [saved] still found its credentials then. */
    private class Push(
        private val saved: (String) -> Boolean,
    ) : InstantPush {
        val forgotten = mutableListOf<Pair<String, Boolean>>()

        override suspend fun distributors() = emptyList<PushDistributor>()

        override suspend fun current(): PushDistributor? = null

        override suspend fun use(distributor: PushDistributor?) = Unit

        override suspend fun statuses() = emptyMap<String, PushDeliveryStatus>()

        override suspend fun forget(loginId: String) {
            forgotten += loginId to saved(loginId)
        }
    }
}

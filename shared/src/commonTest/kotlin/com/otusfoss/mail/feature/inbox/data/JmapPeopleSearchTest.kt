package com.otusfoss.mail.feature.inbox.data

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.core.jmap.login.JmapLogin
import com.otusfoss.core.jmap.login.LoginStore
import com.otusfoss.core.jmap.login.LoginStoreException
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.PeopleField
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JmapPeopleSearchTest {
    @Test
    fun aLoginStoreThatFailsMidSearchAddsOnlyContacts() =
        runTest {
            val ada = JmapLogin("ada", "https://mail.example.org/jmap/session", "ada@example.org", "Ada")
            val store = FailsAfterFirstList(InMemoryLoginStore().apply { save(ada, "secret") })
            val engine = MockEngine { error("The search must not reach the server") }
            val grace = MailSender("Grace Hopper", "grace@navy.example")
            val search = JmapPeopleSearch(JmapConnections(JmapClient(engine), store), { _, _ -> listOf(grace) })
            assertEquals(listOf(grace), search.find(PeopleField.From, "gra", logins = null).map { it.person })
        }

    /** Lists the logins once, then fails as unreadable storage does. */
    private class FailsAfterFirstList(
        private val store: LoginStore,
    ) : LoginStore by store {
        private var listed = false

        override fun logins() =
            if (listed) throw LoginStoreException("Storage is locked") else store.logins().also { listed = true }
    }
}

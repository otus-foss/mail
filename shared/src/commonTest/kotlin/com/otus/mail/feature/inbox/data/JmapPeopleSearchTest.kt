package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.PeopleField
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

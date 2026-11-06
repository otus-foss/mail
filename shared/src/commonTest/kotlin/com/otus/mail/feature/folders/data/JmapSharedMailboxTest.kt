package com.otus.mail.feature.folders.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.feature.folders.domain.FolderOutcome
import com.otus.mail.feature.folders.domain.FolderPlacement
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Shared mailboxes from other users' accounts, and the `Mailbox/set` a drag sends. */
class JmapSharedMailboxTest {
    private val login = ADA_LOGIN
    private val sent = mutableListOf<String>()

    private fun repository(reply: (String) -> String): JmapFolderRepository {
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        val api = { body: String -> reply(body).also { sent += body } }
        val client = JmapClient(stalwartLike(session = SESSION, api = api))
        return JmapFolderRepository(JmapConnections(client, store))
    }

    @Test
    fun sharedAccountsWithMailLoadBesideTheLoginsOwnFolders() =
        runTest {
            val account =
                repository { body ->
                    when {
                        "\"accountId\":\"g1\"" in body -> {
                            mailboxes(
                                """{"id": "a", "name": "Design reviews",
                            "myRights": {"mayAddItems": false}}""",
                            )
                        }

                        "\"accountId\":\"broken\"" in body -> {
                            """{"methodResponses": [["error",
                            {"type": "accountNotFound"}, "c0"]]}"""
                        }

                        else -> {
                            mailboxes("""{"id": "a", "name": "Inbox", "role": "inbox"}""")
                        }
                    }
                }.accounts().single()
            assertEquals("ada@example.org", account.mailboxOwner)
            assertEquals(listOf("a"), account.folders.map { it.id }, "Shared mailboxes stay out of the own list")
            val shared = account.shared.single()
            assertEquals("grace@example.org" to "g1", shared.owner to shared.jmapAccountId)
            val folder = shared.folders.single()
            assertEquals(listOf("ada", "Design reviews", "g1"), listOf(folder.accountId, folder.name, folder.sharedIn))
            assertEquals(false, folder.rights.mayAddItems)
            assertTrue(sent.none { "\"accountId\":\"c1\"" in it }, "An account without mail is not asked")
        }

    @Test
    fun aDragSendsTheParentAndChangedNumbersInOneSet() =
        runTest {
            val folder = MailFolder("ada", "z", "Amazon", parentId = "r", sortOrder = 20)
            val outcome =
                repository {
                    """{"methodResponses": [["Mailbox/set", {"newState": "m2",
                       "updated": {"z": null, "b": null}}, "c0"]]}"""
                }.place(FolderPlacement(folder, null, mapOf("b" to 30L, "z" to 10L)))
            assertEquals(FolderOutcome.Done("z"), outcome)
            val body = sent.single()
            assertTrue("\"accountId\":\"d333333\"" in body, body)
            assertTrue("\"update\":{\"b\":{\"sortOrder\":30},\"z\":{\"sortOrder\":10,\"parentId\":null}}" in body, body)
        }

    @Test
    fun aSharedFolderChangesInItsOwnersAccount() =
        runTest {
            val folder = MailFolder("ada", "a", "Design reviews", sharedIn = "g1")
            repository {
                """{"methodResponses": [["Mailbox/set", {"newState": "m2", "updated": {"a": null}}, "c0"]]}"""
            }.place(FolderPlacement(folder, null, mapOf("a" to 5L)))
            val body = sent.single()
            assertTrue("\"accountId\":\"g1\"" in body && "\"update\":{\"a\":{\"sortOrder\":5}}" in body, body)
        }

    private fun mailboxes(vararg list: String) =
        """{"methodResponses": [["Mailbox/get", {"state": "m", "list": [${list.joinToString()}]}, "c0"]]}"""

    private companion object {
        const val SESSION = """
        {"capabilities": {"urn:ietf:params:jmap:core": {}, "urn:ietf:params:jmap:mail": {}},
         "accounts": {
           "d333333": {"name": "ada@example.org", "isPersonal": true,
             "accountCapabilities": {"urn:ietf:params:jmap:mail": {}}},
           "g1": {"name": "grace@example.org", "isPersonal": false,
             "accountCapabilities": {"urn:ietf:params:jmap:mail": {}}},
           "broken": {"name": "gone@example.org", "isPersonal": false,
             "accountCapabilities": {"urn:ietf:params:jmap:mail": {}}},
           "c1": {"name": "Team contacts", "isPersonal": false,
             "accountCapabilities": {"urn:ietf:params:jmap:contacts": {}}}},
         "primaryAccounts": {"urn:ietf:params:jmap:mail": "d333333"},
         "username": "ada@example.org", "apiUrl": "/jmap/", "state": "s1"}
        """
    }
}

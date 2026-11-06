package com.otus.mail.feature.folders.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.mailbox.FolderFailure
import com.otus.mail.core.mailbox.FolderLimits
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.feature.folders.domain.FolderOutcome
import com.otus.mail.feature.folders.domain.FolderPlacement
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JmapFolderRepositoryTest {
    private val login = ADA_LOGIN
    private val sent = mutableListOf<String>()

    private fun repository(reply: (String) -> String): JmapFolderRepository {
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        val api = { body: String -> reply(body).also { sent += body } }
        val client = JmapClient(stalwartLike(session = SESSION, api = api))
        return JmapFolderRepository(JmapConnections(client, store))
    }

    @Test
    fun foldersCarryHierarchyRightsCountsAndLimits() =
        runTest {
            val account =
                repository {
                    """{"methodResponses": [["Mailbox/get", {"state": "m", "list": [
                       {"id": "a", "name": "Inbox", "role": "Inbox", "unreadEmails": 3, "totalEmails": 9},
                       {"id": "r", "name": "Receipts", "parentId": null,
                        "myRights": {"mayDelete": false, "mayRename": true, "mayCreateChild": true, "mayAddItems": true,
                          "mayRemoveItems": false, "maySetSeen": false, "maySetKeywords": false}},
                       {"id": "z", "name": "Amazon", "parentId": "r", "sortOrder": 4}]}, "c0"]]}"""
                }.accounts().single()
            assertEquals(FolderLimits(mayCreateTopLevel = false, maxDepth = 3, maxNameBytes = 200), account.limits)
            assertEquals(MailboxRole.Inbox, account.inbox?.role)
            assertEquals(3, account.inbox?.unread)
            assertEquals(false, account.folder("r")?.rights?.mayDelete)
            val shared = requireNotNull(account.folder("r")).rights
            assertEquals(
                listOf(false, false, false),
                listOf(shared.mayRemoveItems, shared.maySetSeen, shared.maySetKeywords),
            )
            assertEquals(true, account.folder("z")?.rights?.maySetSeen, "Missing rights are left to the server")
            assertEquals("r", account.folder("z")?.parentId)
            assertTrue("\"parentId\"" in sent.single() && "\"myRights\"" in sent.single())
        }

    @Test
    fun creatingSendsASubscribedFolderAndReturnsItsId() =
        runTest {
            val outcome =
                repository {
                    """{"methodResponses": [["Mailbox/set", {"newState": "m2",
                       "created": {"folder": {"id": "n1"}}}, "c0"]]}"""
                }.create("ada", "  Travel ", "r")
            assertEquals(FolderOutcome.Done("n1"), outcome)
            val body = sent.single()
            assertTrue(
                "\"create\":{\"folder\":{\"name\":\"Travel\",\"parentId\":\"r\",\"isSubscribed\":true}}" in body,
                body,
            )
        }

    @Test
    fun serverRefusalsBecomeReasons() =
        runTest {
            val folder = MailFolder("ada", "r", "Receipts")
            val reasons =
                listOf(
                    "notCreated" to "alreadyExists" to FolderFailure.AlreadyExists,
                    "notUpdated" to "forbidden" to FolderFailure.NotPermitted,
                    "notDestroyed" to "mailboxHasEmail" to FolderFailure.HasMail,
                    "notDestroyed" to "mailboxHasChild" to FolderFailure.HasSubfolders,
                    "notUpdated" to "invalidProperties" to FolderFailure.Invalid,
                )
            reasons.forEach { (refusal, expected) ->
                val (key, type) = refusal
                // RFC 8620 5.3: the refusal is keyed by the submitted creation or folder ID.
                val id = if (key == "notCreated") "folder" else folder.id
                val repository =
                    repository {
                        """{"methodResponses": [["Mailbox/set", {"newState": "m",
                           "$key": {"$id": {"type": "$type"}}}, "c0"]]}"""
                    }
                val outcome =
                    when (key) {
                        "notCreated" -> repository.create("ada", "Receipts", null)
                        "notUpdated" -> repository.rename(folder, "Bills")
                        else -> repository.delete(folder)
                    }
                assertEquals(FolderOutcome.Failed(expected), outcome, type)
            }
            val deletes = sent.filter { "\"destroy\"" in it }
            assertTrue(deletes.isNotEmpty() && deletes.all { "\"onDestroyRemoveEmails\":false" in it }, "Mail is kept")
        }

    @Test
    fun movingToTheTopLevelSendsANullParent() =
        runTest {
            val folder = MailFolder("ada", "z", "Amazon", parentId = "r")
            val outcome =
                repository {
                    """{"methodResponses": [["Mailbox/set", {"newState": "m2", "updated": {"z": null}}, "c0"]]}"""
                }.place(FolderPlacement(folder, null, emptyMap()))
            assertEquals(FolderOutcome.Done("z"), outcome)
            assertTrue("\"update\":{\"z\":{\"parentId\":null}}" in sent.single(), sent.single())
        }

    @Test
    fun aRemovedLoginIsNotFound() =
        runTest {
            val outcome = repository { error("Nothing is sent for a removed login") }.create("gone", "Travel", null)
            assertEquals(FolderOutcome.Failed(FolderFailure.NotFound), outcome)
        }

    private companion object {
        const val SESSION = """
        {"capabilities": {"urn:ietf:params:jmap:core": {}, "urn:ietf:params:jmap:mail": {}},
         "accounts": {"d333333": {"name": "ada@example.org", "isPersonal": true,
           "accountCapabilities": {"urn:ietf:params:jmap:mail": {"mayCreateTopLevelMailbox": false,
             "maxMailboxDepth": 3, "maxSizeMailboxName": 200}}}},
         "primaryAccounts": {"urn:ietf:params:jmap:mail": "d333333"},
         "username": "ada@example.org", "apiUrl": "/jmap/", "state": "s1"}
        """
    }
}

package com.otus.jmap

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assume.assumeNotNull
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Runs against a real JMAP server, such as a local Stalwart, only when configured:
 * `OTUS_JMAP_TEST_URL`, `OTUS_JMAP_TEST_USER`, `OTUS_JMAP_TEST_PASSWORD`, and
 * `OTUS_JMAP_TEST_INSECURE=1` for a plain-HTTP local server. Without them JUnit reports the tests as skipped.
 */
class ServerIntegrationTest {
    private val url = System.getenv("OTUS_JMAP_TEST_URL")
    private val user = System.getenv("OTUS_JMAP_TEST_USER").orEmpty()
    private val password = System.getenv("OTUS_JMAP_TEST_PASSWORD").orEmpty()
    private val client =
        JmapClient(
            policy =
                JmapPolicy(
                    allowInsecureHttp =
                        System.getenv("OTUS_JMAP_TEST_INSECURE") == "1",
                ),
        )

    @Test
    fun discoversTheSessionAndQueriesMailboxes() =
        runTest {
            assumeNotNull(url)
            val serverUrl = requireNotNull(url)
            val connection = JmapConnection(client, serverUrl, JmapAuth.Basic(user, password))
            val session = connection.session()
            assertTrue(session.apiUrl.startsWith("http"), "apiUrl is absolute: ${session.apiUrl}")
            val account =
                requireNotNull(session.accountFor(JmapCapability.MAIL)) { "Integration server must offer mail" }
            val mailboxes = connection.call(GetMethod(MAILBOX, account, properties = listOf("id", "role")))
            assertEquals(
                mailboxes.list.size,
                mailboxes.list
                    .map { it.id }
                    .distinct()
                    .size,
            )
        }

    /** Leaves nothing behind: the mailbox is deleted again and an unused blob expires on the server. */
    @Test
    fun createsAndDeletesAMailboxAndUploadsABlob() =
        runTest {
            assumeNotNull(url)
            val connection = JmapConnection(client, requireNotNull(url), JmapAuth.Basic(user, password))
            val session = connection.session()
            val account =
                requireNotNull(session.accountFor(JmapCapability.MAIL)) { "Integration server must offer mail" }
            val folder = JsonObject(mapOf("name" to JsonPrimitive("otus-integration-${Random.nextLong().toULong()}")))
            val created = connection.call(SetMethod(MAILBOX, account, create = mapOf("n" to folder)))
            val id = requireNotNull(created.createdId("n")) { "Not created: ${created.firstError}" }
            val removal = mapOf("onDestroyRemoveEmails" to JsonPrimitive(false))
            val destroyed = connection.call(SetMethod(MAILBOX, account, destroy = listOf(id), extra = removal))
            assertEquals(listOf(id), destroyed.destroyed)
            val blob = connection.upload(account, "require \"fileinto\";\r\n".encodeToByteArray(), "application/sieve")
            assertTrue(blob.blobId.isNotEmpty())
        }

    @Test
    fun aWrongPasswordIsUnauthorized() =
        runTest {
            assumeNotNull(url)
            val serverUrl = requireNotNull(url)
            assertFailsWith<JmapException.Unauthorized> {
                client.session(
                    serverUrl,
                    JmapAuth.Basic(user, "$password-wrong"),
                )
            }
        }
}

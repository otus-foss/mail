package com.otusfoss.mail.feature.inbox.data

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.core.messages.data.DeviceDrafts
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.DraftException
import com.otusfoss.mail.core.messages.domain.DraftFailure
import com.otusfoss.mail.core.messages.domain.DraftRepository
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.SendIdentity
import com.otusfoss.mail.core.messages.domain.WaitingDraftStore
import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.feature.folders.data.JmapFolderRepository
import com.otusfoss.mail.feature.folders.data.WaitingFolderChanges
import com.otusfoss.mail.feature.folders.domain.FolderChangeStore
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.ADA_SECRET_HEADER
import com.otusfoss.mail.testing.MemoryMapStore
import com.otusfoss.mail.testing.STALWART_SESSION
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Waiting folder changes and drafts survive a server that is briefly away or a copy sent elsewhere. */
class OfflineWaitsTest {
    private val login = ADA_LOGIN
    private val logins = InMemoryLoginStore().apply { save(login, "secret") }

    @Test
    fun aWaitingFolderRenameOutlastsAMomentaryHttp503() =
        runTest {
            var down = false
            var status503 = false
            val updates = mutableListOf<String>()
            val engine =
                MockEngine(
                    MockEngineConfig().apply {
                        addHandler { request ->
                            val json = headersOf(HttpHeaders.ContentType, "application/json")
                            when {
                                request.headers[HttpHeaders.Authorization] != ADA_SECRET_HEADER -> {
                                    respond("", HttpStatusCode.Unauthorized)
                                }

                                request.url.encodedPath == "/.well-known/jmap" -> {
                                    respond(
                                        "",
                                        HttpStatusCode.TemporaryRedirect,
                                        headersOf(HttpHeaders.Location, "/jmap/session"),
                                    )
                                }

                                request.url.encodedPath == "/jmap/session" -> {
                                    respond(STALWART_SESSION, HttpStatusCode.OK, json)
                                }

                                request.url.encodedPath == "/jmap/" -> {
                                    val body = (request.body as TextContent).text
                                    if (down) throw IOException("offline")
                                    if ("Mailbox/set" in body) {
                                        if (status503) {
                                            respond("Service Unavailable", HttpStatusCode.ServiceUnavailable)
                                        } else {
                                            updates += body
                                            respond(
                                                """{"methodResponses": [["Mailbox/set", {"newState": "m2", "updated": {"r": null}}, "c0"]]}""",
                                                HttpStatusCode.OK,
                                                json,
                                            )
                                        }
                                    } else {
                                        respond(MAILBOXES, HttpStatusCode.OK, json)
                                    }
                                }

                                else -> {
                                    respond("", HttpStatusCode.NotFound)
                                }
                            }
                        }
                    },
                )
            val jmap = JmapConnections(JmapClient(engine), logins)
            val mailboxes = Mailboxes(InMemoryMailCache())
            val map = MemoryMapStore()
            val waiting =
                WaitingFolderChanges(jmap, mailboxes, DeviceStore(map, FolderChangeStore), {}, Dispatchers.Unconfined)
            val repo = JmapFolderRepository(jmap, mailboxes, waiting)
            val folder = requireNotNull(repo.accounts().single().folder("r"))

            down = true
            repo.rename(folder, "Bills")
            down = false
            status503 = true

            repo.accounts()
            status503 = false
            repo.accounts()

            assertEquals(1, updates.size, "The rename goes once the server is back")
            assertTrue("\"name\":\"Bills\"" in updates.single(), updates.single())
            assertEquals(emptyMap(), map.read())
        }

    @Test
    fun discardingAfterUndoLeavesACopyAnotherClientSentAlone() =
        runTest {
            val remote = TrackingDrafts()
            val drafts =
                DeviceDrafts(remote, DeviceStore(MemoryMapStore(), WaitingDraftStore), io = Dispatchers.Unconfined)
            val written =
                Draft(login.id, to = listOf(MailSender("Bob", "bob@example.org")), subject = "Edited", emailId = "s1")

            remote.online = false
            val deviceId = drafts.save(written).getOrThrow()
            // Send hands the draft over, Undo reopens it under its device id, and another client sends s1.
            assertEquals("s1", drafts.handOver(written.copy(emailId = deviceId)).emailId)
            remote.stillDraft = false
            remote.online = true

            drafts.discard(login.id, deviceId)

            assertEquals(1, remote.isDraftCalls)
            assertEquals(emptyList(), remote.discarded)
        }

    private class TrackingDrafts : DraftRepository {
        var online = true
        var stillDraft = true
        var isDraftCalls = 0
        val discarded = mutableListOf<String>()

        override suspend fun identities(accountId: String): List<SendIdentity> = emptyList()

        override suspend fun save(draft: Draft): Result<String> =
            if (online) {
                Result.success(
                    "e2",
                )
            } else {
                Result.failure(DraftException(DraftFailure.Mail(MailFailure.Unreachable)))
            }

        override suspend fun isDraft(
            accountId: String,
            emailId: String,
        ): Result<Boolean> {
            isDraftCalls++
            return if (online) {
                Result.success(
                    stillDraft,
                )
            } else {
                Result.failure(DraftException(DraftFailure.Mail(MailFailure.Unreachable)))
            }
        }

        override suspend fun discard(
            accountId: String,
            emailId: String,
        ): Result<Unit> {
            if (!online) return Result.failure(DraftException(DraftFailure.Mail(MailFailure.Unreachable)))
            discarded += emailId
            return Result.success(Unit)
        }
    }

    private companion object {
        const val MAILBOXES =
            """{"methodResponses": [["Mailbox/get", {"state": "m", "list": [
               {"id": "a", "name": "Inbox", "role": "inbox"}, {"id": "r", "name": "Receipts"}]}, "c0"]]}"""
    }
}

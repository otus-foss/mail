package com.otusfoss.mail.feature.compose.data

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.core.messages.data.DeviceDrafts
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.WaitingDraftStore
import com.otusfoss.mail.core.messages.domain.isDeviceDraftId
import com.otusfoss.mail.core.storage.DeviceStore
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** A cancelled draft flush must not leave a duplicate copy behind on the server. */
class CancelledDraftFlushTest {
    private val server = DraftServer()
    private val logins = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
    private val cache = InMemoryMailCache()
    private var messageIds = 0
    private val drafts =
        DeviceDrafts(
            JmapDraftRepository(
                JmapConnections(JmapClient(engine(server::answer)), logins),
                DraftCopies(Mailboxes(cache)),
                cache,
            ) { "m${++messageIds}@otus.mail" },
            DeviceStore(MemoryMapStore(), WaitingDraftStore),
            io = Dispatchers.Unconfined,
        )

    @Test
    fun aFlushCancelledAfterTheServerStoredItsCopyLeavesOneCopyOfTheDraft() =
        runTest {
            server.down = true
            val id = drafts.save(Draft("ada", subject = "Edited on the phone")).getOrThrow()
            server.down = false
            check(isDeviceDraftId(id)) { "The offline save did not wait on the device" }

            val answer = CompletableDeferred<Unit>()
            server.holdCreate = answer
            val flush = launch { drafts.flush() }
            server.createReceived.await()
            flush.cancelAndJoin()
            server.holdCreate = null
            answer.complete(Unit)

            drafts.flush()

            assertEquals(1, server.live().size, "Copies in Drafts: ${server.live().map { it.id }}")
        }
}

private class DraftServer {
    class Copy(
        val id: String,
        val messageId: String?,
        val mailboxes: Set<String>,
    )

    val stored = mutableListOf<Copy>()
    val destroyed = mutableListOf<String>()
    var down = false
    var holdCreate: CompletableDeferred<Unit>? = null
    val createReceived = CompletableDeferred<Unit>()

    fun live(): List<Copy> = stored.filter { it.id !in destroyed }

    suspend fun answer(body: String): String {
        if (down) throw IOException("The network is down")
        val responses =
            Json.parseToJsonElement(body).jsonObject.getValue("methodCalls").jsonArray.map { call ->
                val (name, arguments, id) = call.jsonArray
                val method = name.jsonPrimitive.content
                val result = respond(method, arguments.jsonObject)
                JsonArray(listOf(JsonPrimitive(if (result == null) "error" else method), result ?: UNKNOWN, id))
            }
        return buildJsonObject { put("methodResponses", JsonArray(responses)) }.toString()
    }

    private suspend fun respond(
        method: String,
        arguments: JsonObject,
    ): JsonObject? =
        when (method) {
            "Mailbox/get" -> Json.parseToJsonElement(MAILBOXES).jsonObject
            "Email/query" -> emailQuery(arguments)
            "Email/set" -> emailSet(arguments)
            else -> null
        }

    private suspend fun emailSet(arguments: JsonObject): JsonObject {
        val created =
            (arguments["create"] as? JsonObject).orEmpty().mapValues { (_, email) ->
                val fields = email.jsonObject
                Copy(
                    id = "c${stored.size + 1}",
                    messageId = (fields["messageId"] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.content,
                    mailboxes = (fields["mailboxIds"] as? JsonObject)?.keys.orEmpty(),
                ).also { stored += it }
            }
        val hold = holdCreate
        if (created.isNotEmpty() && hold != null) {
            createReceived.complete(Unit)
            hold.await()
        }
        val gone =
            (arguments["destroy"] as? JsonArray)
                .orEmpty()
                .map { it.jsonPrimitive.content }
                .filter { id -> live().any { it.id == id } }
        destroyed += gone
        return buildJsonObject {
            put("newState", JsonPrimitive("e"))
            put(
                "created",
                JsonObject(
                    created.mapValues { (_, copy) ->
                        JsonObject(mapOf("id" to JsonPrimitive(copy.id)))
                    },
                ),
            )
            put("destroyed", JsonArray(gone.map(::JsonPrimitive)))
        }
    }

    private fun emailQuery(arguments: JsonObject): JsonObject {
        val filter = arguments["filter"].toString()
        val found =
            live().filter { copy ->
                DRAFTS in copy.mailboxes && copy.messageId != null && "\"${copy.messageId}\"" in filter
            }
        return buildJsonObject {
            put("queryState", JsonPrimitive("q"))
            put("ids", JsonArray(found.map { JsonPrimitive(it.id) }))
            put("position", JsonPrimitive(0))
        }
    }

    private companion object {
        const val DRAFTS = "dr"
        const val MAILBOXES = """{"state": "m", "list": [{"id": "dr", "role": "drafts"}]}"""
        val UNKNOWN = JsonObject(mapOf("type" to JsonPrimitive("unknownMethod")))
    }
}

private fun engine(api: suspend (String) -> String): MockEngine =
    MockEngine(
        MockEngineConfig().apply {
            addHandler { request ->
                val json = headersOf(HttpHeaders.ContentType, "application/json")
                when {
                    request.headers[HttpHeaders.Authorization] != ADA_SECRET_HEADER -> {
                        respond("", HttpStatusCode.Unauthorized)
                    }

                    request.url.encodedPath == "/.well-known/jmap" -> {
                        respond("", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, "/jmap/session"))
                    }

                    request.url.encodedPath == "/jmap/session" -> {
                        respond(STALWART_SESSION, HttpStatusCode.OK, json)
                    }

                    request.url.encodedPath == "/jmap/" -> {
                        respond(api((request.body as TextContent).text), HttpStatusCode.OK, json)
                    }

                    else -> {
                        respond("", HttpStatusCode.NotFound)
                    }
                }
            }
        },
    )

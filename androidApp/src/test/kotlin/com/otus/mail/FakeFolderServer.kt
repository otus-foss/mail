package com.otus.mail

import com.otus.jmap.JmapClient
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.app.MailDeviceStores
import com.otus.mail.app.MailServices
import com.otus.mail.feature.folders.domain.FolderColorStore
import com.otus.mail.feature.notifications.domain.InstantPush
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A JMAP server shaped like Stalwart that answers each method call of a request by name and
 * remembers changes: folders created with `Mailbox/set` and the rules script uploaded and
 * activated with `SieveScript/set`. Drafts saved with `Email/set` go in the [draftsFolder] that
 * `Mailbox/query` finds by role. [FakeFolderMail] holds the mail.
 */
class FakeFolderServer {
    /** Every API request body, in order. The client adds to it while tests read it. */
    val calls: MutableList<String> = CopyOnWriteArrayList()
    val folders =
        mutableListOf(
            """{"id": "a", "name": "Inbox", "role": "inbox", "unreadEmails": 1, "totalEmails": 1}""",
            """{"id": "r", "name": "Receipts", "unreadEmails": 2, "totalEmails": 3}""",
            """{"id": "z", "name": "Amazon", "parentId": "r"}""",
        )

    /** Mailboxes grace@example.org shares with Ada, in the session account `g1`; none leaves `g1` out. */
    val sharedFolders = mutableListOf<String>()

    /** Folders the message is in besides the one it was loaded from. */
    val alsoIn = mutableListOf<String>()

    /** The subject of the one message in each folder; any other folder's message is "Welcome". */
    val subjects = mutableMapOf("r" to "Order shipped")

    /** The folder of each earlier message from the sender, newest first, for folder suggestions. */
    val senderFolders = mutableListOf<String>()

    /** Ids of older inbox messages from the sender that only a rule's inbox check sees. */
    val olderMail = mutableListOf<String>()

    /** Whether the account offers JMAP for Sieve; without it the app runs no rules. */
    var sieve = true

    /** When set, every `Email/set` update is refused as forbidden. */
    var refuseUpdates = false

    /** The id of the mailbox with role `drafts`; null when the account has none. */
    var draftsFolder: String? = "dr"

    /** The rules script as last uploaded and saved, or the one a test starts with. */
    var script: String? = null
    var scriptActive = false
    private var uploaded = ""
    private var state = 1
    private val mail = FakeFolderMail(this)

    /** Signs in as Ada, and also as Bob when [withBob], who shares Ada's mailboxes and identity. */
    fun services(
        instantPush: InstantPush? = null,
        folderColorStore: FolderColorStore? = null,
        withBob: Boolean = false,
    ): MailServices {
        val store = InMemoryLoginStore()
        store.save(JmapLogin("ada", "https://mail.example.org/jmap/session", "ada@example.org", "Ada"), "secret")
        if (withBob) {
            store.save(JmapLogin("bob", "https://mail.example.org/jmap/session", "bob@example.org", "Bob"), "secret")
        }
        return MailServices(
            store,
            stores = MailDeviceStores(folderColors = folderColorStore),
            instantPush = instantPush,
        ) {
            JmapClient(engine)
        }
    }

    private val engine =
        MockEngine { request ->
            check(request.headers[HttpHeaders.Authorization] in SIGN_INS)
            when (request.url.encodedPath) {
                "/jmap/session" -> {
                    json(session(adaSignedIn = request.headers[HttpHeaders.Authorization] == SIGN_INS.first()))
                }

                "/upload/d1/" -> {
                    upload(request)
                }

                "/download/S1" -> {
                    respond(
                        script.orEmpty(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/sieve"),
                    )
                }

                else -> {
                    json(api((request.body as TextContent).text))
                }
            }
        }

    private fun MockRequestHandleScope.upload(request: HttpRequestData): HttpResponseData {
        uploaded = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
        return json("""{"accountId": "d1", "blobId": "B$state", "type": "application/sieve", "size": 1}""")
    }

    private fun api(body: String): String {
        calls += body
        return jmapReply(body, ::answer)
    }

    private fun answer(
        method: String,
        arguments: JsonObject,
    ): JsonObject? =
        when (method) {
            "Mailbox/get" -> mailboxes(arguments)
            "Mailbox/query" -> buildJsonObject { put("ids", listOfNotNull(draftsFolder).toJsonArray()) }
            "Mailbox/set" -> setFolders(arguments)
            "Identity/get" -> IDENTITIES
            "SieveScript/get" -> scripts()
            "SieveScript/set" -> saveScript(arguments)
            "SieveScript/validate" -> buildJsonObject { put("error", JsonNull) }
            else -> mail.answer(method, arguments)
        }

    private fun mailboxes(arguments: JsonObject): JsonObject {
        val shared = arguments["accountId"] == JsonPrimitive(SHARED_ACCOUNT)
        return listResult("m", (if (shared) sharedFolders else folders).map(Json::parseToJsonElement))
    }

    /**
     * Creates folders as `n<count>` and deletes them like a server that found them all empty.
     * Accepts every update without applying it; tests read what was sent from [calls].
     */
    private fun setFolders(arguments: JsonObject): JsonObject =
        buildJsonObject {
            put("newState", "m2")
            (arguments["create"] as? JsonObject)?.let { creates ->
                put("created", JsonObject(creates.mapValues { (_, folder) -> createFolder(folder.jsonObject) }))
            }
            (arguments["update"] as? JsonObject)?.let { put("updated", JsonObject(it.mapValues { JsonNull })) }
            (arguments["destroy"] as? JsonArray)?.let { ids ->
                folders.removeAll { Json.parseToJsonElement(it).jsonObject.id in ids.strings() }
                put("destroyed", ids)
            }
        }

    private fun createFolder(folder: JsonObject): JsonObject {
        val id = "n${folders.size}"
        folders +=
            buildJsonObject {
                put("id", id)
                put("name", folder.getValue("name"))
            }.toString()
        return buildJsonObject { put("id", id) }
    }

    private fun session(adaSignedIn: Boolean): String {
        val shared = if (adaSignedIn && sharedFolders.isNotEmpty()) SHARED_SESSION_ACCOUNT else ""
        val withSieve = if (sieve) SESSION else SESSION.replace(SIEVE_SESSION, "").replace(SIEVE_ACCOUNT, "")
        return withSieve.replace(SHARED_SLOT, shared)
    }

    private fun scripts(): JsonObject {
        val list = script?.let { """[{"id": "S1", "name": "Otus Mail", "blobId": "S1", "isActive": $scriptActive}]""" }
        return listResult("s$state", Json.parseToJsonElement(list ?: "[]").jsonArray)
    }

    private fun saveScript(arguments: JsonObject): JsonObject {
        val creation = (arguments["create"] as? JsonObject)?.keys?.firstOrNull() ?: "rules"
        // Like Stalwart, which ties `:mailboxid` to the "mailbox" extension.
        if (":mailboxid" in uploaded && "\"mailbox\"" !in uploaded) {
            return buildJsonObject {
                put("newState", "s$state")
                putJsonObject("notCreated") {
                    putJsonObject(creation) {
                        put("type", "invalidScript")
                        put("description", "Undeclared capability 'mailbox'")
                    }
                }
            }
        }
        val created = script == null
        script = uploaded
        scriptActive = scriptActive || "onSuccessActivateScript" in arguments
        state++
        return buildJsonObject {
            put("newState", "s$state")
            if (created) {
                putJsonObject("created") { putJsonObject(creation) { put("id", "S1") } }
            } else {
                putJsonObject("updated") { put("S1", JsonNull) }
            }
        }
    }

    private fun MockRequestHandleScope.json(text: String): HttpResponseData =
        respond(text, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private companion object {
        val SIGN_INS = setOf("Basic YWRhQGV4YW1wbGUub3JnOnNlY3JldA==", "Basic Ym9iQGV4YW1wbGUub3JnOnNlY3JldA==")
        const val SHARED_ACCOUNT = "g1"
        const val SHARED_SLOT = "/*shared*/"
        const val SIEVE_SESSION = """, "urn:ietf:params:jmap:sieve": {"implementation": "Stalwart"}"""
        const val SIEVE_EXTENSIONS = """["fileinto", "mailbox", "mailboxid", "include"]"""
        const val SIEVE_ACCOUNT =
            """,
              "urn:ietf:params:jmap:sieve": {"sieveExtensions": """ + SIEVE_EXTENSIONS + "}"
        const val SHARED_SESSION_ACCOUNT = """, "g1": {"name": "grace@example.org", "isPersonal": false,
              "accountCapabilities": {"urn:ietf:params:jmap:mail": {}}}"""
        val IDENTITIES =
            Json
                .parseToJsonElement(
                    """{"state": "i", "list": [{"id": "i1", "name": "Ada Lovelace", "email": "ada@example.org"}]}""",
                ).jsonObject
        const val SESSION = """{"capabilities": {"urn:ietf:params:jmap:core": {}, "urn:ietf:params:jmap:mail": {},
            "urn:ietf:params:jmap:submission": {}, "urn:ietf:params:jmap:sieve": {"implementation": "Stalwart"}},
            "accounts": {"d1": {"name": "Ada", "accountCapabilities": {"urn:ietf:params:jmap:mail": {},
              "urn:ietf:params:jmap:submission": {},
              "urn:ietf:params:jmap:sieve": {"sieveExtensions": ["fileinto", "mailbox", "mailboxid", "include"]}}}/*shared*/},
            "primaryAccounts": {"urn:ietf:params:jmap:mail": "d1"}, "username": "ada@example.org", "apiUrl": "/jmap/",
            "downloadUrl": "/download/{blobId}", "uploadUrl": "/upload/{accountId}/", "state": "x"}"""
    }
}

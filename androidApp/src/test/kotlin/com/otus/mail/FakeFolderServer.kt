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
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A JMAP server shaped like Stalwart that remembers changes: folders created with `Mailbox/set`
 * and the rules script uploaded and activated with `SieveScript/set`. Drafts saved with `Email/set`
 * get the ids `draft1`, `draft2`, and so on, in the [draftsFolder] that `Mailbox/query` finds by role.
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
    private var draftCount = 0

    /** The rules script as last uploaded and saved, or the one a test starts with. */
    var script: String? = null
    var scriptActive = false
    private var uploaded = ""
    private var state = 1

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
        val reply =
            when {
                "Mailbox/" in body -> mailbox(body)
                "Email/set" in body -> setEmail(body)
                "Identity/get" in body -> IDENTITIES
                "Email/query" in body -> queryEmails(body)
                "SieveScript/" in body -> sieve(body)
                else -> error("Unexpected call: $body")
            }
        return """{"methodResponses": [$reply]}"""
    }

    private fun mailbox(body: String): String =
        when {
            "Mailbox/set" in body && "\"update\":" in body -> updateFolders(body)
            "Mailbox/set" in body && "\"destroy\":" in body -> destroyFolders(body)
            "Mailbox/set" in body -> createFolder(body)
            "Mailbox/get" in body && SHARED_ACCOUNT in body -> mailboxes(sharedFolders)
            "Mailbox/get" in body -> mailboxes(folders)
            else -> draftsQuery()
        }

    private fun draftsQuery(): String {
        val ids = listOfNotNull(draftsFolder).joinToString { "\"$it\"" }
        return """["Mailbox/query", {"ids": [$ids]}, "c0"]"""
    }

    private fun setEmail(body: String): String {
        val outcomes =
            buildList {
                if ("\"create\":" in body) add(""""created": {"draft": {"id": "draft${++draftCount}"}}""")
                if ("\"update\":" in body) add(if (refuseUpdates) REFUSED_UPDATE else updatedEmails(body))
                DESTROY.find(body)?.let { add(""""destroyed": [${it.groupValues[1]}]""") }
            }
        return """["Email/set", {"newState": "e2", ${outcomes.joinToString()}}, "c0"]"""
    }

    /** Echoes every updated id; older mail a rule moves leaves [olderMail]. */
    private fun updatedEmails(body: String): String {
        val ids = UPDATED_EMAIL.findAll(body.substringAfter("\"update\":")).map { it.groupValues[1] }.toList()
        olderMail -= ids.toSet()
        return """"updated": {${ids.joinToString { "\"$it\": null" }}}"""
    }

    private fun mailboxes(list: List<String>) =
        """["Mailbox/get", {"state": "m", "list": ${list.joinToString(prefix = "[", postfix = "]")}}, "c0"]"""

    /** Accepts every update without applying it; tests read what was sent from [calls]. */
    private fun updateFolders(body: String): String {
        val ids =
            UPDATED_ID
                .findAll(
                    body.substringAfter("\"update\":"),
                ).joinToString { "\"${it.groupValues[1]}\": null" }
        return """["Mailbox/set", {"newState": "m2", "updated": {$ids}}, "c0"]"""
    }

    /** Deletes the folders and answers like a server that found them all empty. */
    private fun destroyFolders(body: String): String {
        val ids = DESTROY.find(body)?.let { it.groupValues[1] }.orEmpty()
        folders.removeAll { folder -> FOLDER_ID.find(folder)?.let { "\"${it.groupValues[1]}\"" in ids } == true }
        return """["Mailbox/set", {"newState": "m2", "destroyed": [$ids]}, "c0"]"""
    }

    private fun session(adaSignedIn: Boolean): String {
        val shared = if (adaSignedIn && sharedFolders.isNotEmpty()) SHARED_SESSION_ACCOUNT else ""
        val withSieve = if (sieve) SESSION else SESSION.replace(SIEVE_SESSION, "").replace(SIEVE_ACCOUNT, "")
        return withSieve.replace(SHARED_SLOT, shared)
    }

    private fun createFolder(body: String): String {
        val name =
            Regex("\"name\":\"([^\"]+)\"")
                .find(body)
                ?.groupValues
                ?.get(1)
                .orEmpty()
        val id = "n${folders.size}"
        folders += """{"id": "$id", "name": "$name"}"""
        return """["Mailbox/set", {"newState": "m2", "created": {"folder": {"id": "$id"}}}, "c0"]"""
    }

    private fun sieve(body: String): String =
        when {
            "SieveScript/get" in body -> scripts()
            "SieveScript/set" in body -> saveScript(body)
            else -> VALID_SCRIPT
        }

    private fun scripts(): String {
        val list = script?.let { """[{"id": "S1", "name": "Otus Mail", "blobId": "S1", "isActive": $scriptActive}]""" }
        return """["SieveScript/get", {"state": "s$state", "list": ${list ?: "[]"}}, "c0"]"""
    }

    private fun saveScript(body: String): String {
        // Like Stalwart, which ties `:mailboxid` to the "mailbox" extension.
        if (":mailboxid" in uploaded && "\"mailbox\"" !in uploaded) {
            return """["SieveScript/set", {"newState": "s$state", "notCreated": {"rules": {"type": "invalidScript",
                "description": "Undeclared capability 'mailbox'"}}}, "c0"]"""
        }
        val outcome = if (script == null) """"created": {"rules": {"id": "S1"}}""" else """"updated": {"S1": null}"""
        script = uploaded
        scriptActive = scriptActive || "onSuccessActivateScript" in body
        state++
        return """["SieveScript/set", {"newState": "s$state", $outcome}, "c0"]"""
    }

    private fun emails(mailbox: String): String {
        val subject = subjects[mailbox] ?: "Welcome"
        val mailboxIds = (listOf(mailbox) + alsoIn).distinct().joinToString { "\"$it\": true" }
        return """["Email/query", {"ids": ["m1"]}, "c0"], ["Email/get", {"state": "e", "list": [{"id": "m1",
           "mailboxIds": {$mailboxIds}, "from": [{"name": "Grace Hopper", "email": "grace@example.org"}],
           "subject": "$subject", "receivedAt": "2026-10-04T09:30:00Z", "preview": "Hello"}]}, "c1"]"""
    }

    private fun queryEmails(body: String): String =
        when {
            FROM_FILTER in body -> senderMail()
            "calculateTotal" in body -> """["Email/query", {"ids": [], "total": ${olderMail.size}}, "c0"]"""
            RULE_FILTER in body -> ruleMatches(body)
            else -> emails(IN_MAILBOX.find(body)?.groupValues?.get(1) ?: "a")
        }

    /** The inbox mail a rule's filter finds: [olderMail], which the moved message already left. */
    private fun ruleMatches(body: String): String {
        val ids = if ("\"inMailbox\":\"a\"" in body) olderMail else emptyList()
        return """["Email/query", {"ids": ${ids.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }}}, "c0"]"""
    }

    private fun senderMail(): String {
        val list =
            senderFolders
                .mapIndexed { index, folder -> """{"id": "h$index", "mailboxIds": {"$folder": true}}""" }
                .joinToString(prefix = "[", postfix = "]")
        return """["Email/query", {"ids": []}, "c0"], ["Email/get", {"state": "e", "list": $list}, "c1"]"""
    }

    private fun MockRequestHandleScope.json(text: String): HttpResponseData =
        respond(text, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private companion object {
        val SIGN_INS = setOf("Basic YWRhQGV4YW1wbGUub3JnOnNlY3JldA==", "Basic Ym9iQGV4YW1wbGUub3JnOnNlY3JldA==")
        val IN_MAILBOX = Regex("\"inMailbox\":\"([^\"]+)\"")
        const val VALID_SCRIPT = """["SieveScript/validate", {"accountId": "d1", "error": null}, "c0"]"""
        const val REFUSED_UPDATE = """"notUpdated": {"m1": {"type": "forbidden"}}"""
        val FOLDER_ID = Regex("\"id\": \"([^\"]+)\"")
        val DESTROY = Regex("\"destroy\":\\[([^\\]]*)]")
        const val RULE_FILTER = "\"filter\":{\"operator\":\"AND\""
        val UPDATED_EMAIL = Regex("\"([^\"]+)\":\\{\"(?:mailboxIds|keywords)")
        val UPDATED_ID = Regex("\"([^\"]+)\":\\{\"(?:sortOrder|parentId|name)\"")
        const val SHARED_ACCOUNT = "\"accountId\":\"g1\""
        const val SHARED_SLOT = "/*shared*/"
        const val SIEVE_SESSION = """, "urn:ietf:params:jmap:sieve": {"implementation": "Stalwart"}"""
        const val SIEVE_EXTENSIONS = """["fileinto", "mailbox", "mailboxid", "include"]"""
        const val SIEVE_ACCOUNT =
            """,
              "urn:ietf:params:jmap:sieve": {"sieveExtensions": """ + SIEVE_EXTENSIONS + "}"
        const val SHARED_SESSION_ACCOUNT = """, "g1": {"name": "grace@example.org", "isPersonal": false,
              "accountCapabilities": {"urn:ietf:params:jmap:mail": {}}}"""
        const val IDENTITIES = """["Identity/get", {"state": "i", "list": [
            {"id": "i1", "name": "Ada Lovelace", "email": "ada@example.org"}]}, "c0"]"""
        const val FROM_FILTER = "\"filter\":{\"from\":"
        const val SESSION = """{"capabilities": {"urn:ietf:params:jmap:core": {}, "urn:ietf:params:jmap:mail": {},
            "urn:ietf:params:jmap:submission": {}, "urn:ietf:params:jmap:sieve": {"implementation": "Stalwart"}},
            "accounts": {"d1": {"name": "Ada", "accountCapabilities": {"urn:ietf:params:jmap:mail": {},
              "urn:ietf:params:jmap:submission": {},
              "urn:ietf:params:jmap:sieve": {"sieveExtensions": ["fileinto", "mailbox", "mailboxid", "include"]}}}/*shared*/},
            "primaryAccounts": {"urn:ietf:params:jmap:mail": "d1"}, "username": "ada@example.org", "apiUrl": "/jmap/",
            "downloadUrl": "/download/{blobId}", "uploadUrl": "/upload/{accountId}/", "state": "x"}"""
    }
}

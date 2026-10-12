package com.otus.mail

import com.otus.jmap.JmapClient
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.app.MailServices
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

    /** Folders the message is in besides the one it was loaded from. */
    val alsoIn = mutableListOf<String>()

    /** The subject of the one message in each folder; any other folder's message is "Welcome". */
    val subjects = mutableMapOf("r" to "Order shipped")

    /** The folder of each earlier message from the sender, newest first, for folder suggestions. */
    val senderFolders = mutableListOf<String>()

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

    fun services(): MailServices {
        val store = InMemoryLoginStore()
        store.save(JmapLogin("ada", "https://mail.example.org/jmap/session", "ada@example.org", "Ada"), "secret")
        return MailServices(store) { JmapClient(engine) }
    }

    private val engine =
        MockEngine { request ->
            check(request.headers[HttpHeaders.Authorization] == "Basic YWRhQGV4YW1wbGUub3JnOnNlY3JldA==")
            when (request.url.encodedPath) {
                "/jmap/session" -> {
                    json(SESSION)
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
                "Mailbox/set" in body -> createFolder(body)
                "Mailbox/get" in body -> """["Mailbox/get", {"state": "m", "list": $folderList}, "c0"]"""
                "Mailbox/query" in body -> draftsQuery()
                "Email/set" in body -> setEmail(body)
                "Identity/get" in body -> IDENTITIES
                "Email/query" in body && FROM_FILTER in body -> senderMail()
                "Email/query" in body -> emails(IN_MAILBOX.find(body)?.groupValues?.get(1) ?: "a")
                "SieveScript/get" in body -> scripts()
                "SieveScript/set" in body -> saveScript(body)
                else -> error("Unexpected call: $body")
            }
        return """{"methodResponses": [$reply]}"""
    }

    private fun draftsQuery(): String {
        val ids = listOfNotNull(draftsFolder).joinToString { "\"$it\"" }
        return """["Mailbox/query", {"ids": [$ids]}, "c0"]"""
    }

    private fun setEmail(body: String): String {
        val outcomes =
            buildList {
                if ("\"create\":" in body) add(""""created": {"draft": {"id": "draft${++draftCount}"}}""")
                if ("\"update\":" in body) add(if (refuseUpdates) REFUSED_UPDATE else """"updated": {"m1": null}""")
                DESTROY.find(body)?.let { add(""""destroyed": [${it.groupValues[1]}]""") }
            }
        return """["Email/set", {"newState": "e2", ${outcomes.joinToString()}}, "c0"]"""
    }

    private val folderList: String
        get() = folders.joinToString(prefix = "[", postfix = "]")

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

    private fun scripts(): String {
        val list = script?.let { """[{"id": "S1", "name": "Otus Mail", "blobId": "S1", "isActive": $scriptActive}]""" }
        return """["SieveScript/get", {"state": "s$state", "list": ${list ?: "[]"}}, "c0"]"""
    }

    private fun saveScript(body: String): String {
        script = uploaded
        scriptActive = scriptActive || "onSuccessActivateScript" in body
        state++
        return """["SieveScript/set", {"newState": "s$state", "created": {"rules": {"id": "S1"}}}, "c0"]"""
    }

    private fun emails(mailbox: String): String {
        val subject = subjects[mailbox] ?: "Welcome"
        val mailboxIds = (listOf(mailbox) + alsoIn).distinct().joinToString { "\"$it\": true" }
        return """["Email/query", {"ids": ["m1"]}, "c0"], ["Email/get", {"state": "e", "list": [{"id": "m1",
           "mailboxIds": {$mailboxIds}, "from": [{"name": "Grace Hopper", "email": "grace@example.org"}],
           "subject": "$subject", "receivedAt": "2026-10-04T09:30:00Z", "preview": "Hello"}]}, "c1"]"""
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
        val IN_MAILBOX = Regex("\"inMailbox\":\"([^\"]+)\"")
        const val REFUSED_UPDATE = """"notUpdated": {"m1": {"type": "forbidden"}}"""
        val DESTROY = Regex("\"destroy\":\\[([^\\]]*)]")
        const val IDENTITIES = """["Identity/get", {"state": "i", "list": [
            {"id": "i1", "name": "Ada Lovelace", "email": "ada@example.org"}]}, "c0"]"""
        const val FROM_FILTER = "\"filter\":{\"from\":"
        const val SESSION = """{"capabilities": {"urn:ietf:params:jmap:core": {}, "urn:ietf:params:jmap:mail": {},
            "urn:ietf:params:jmap:submission": {}, "urn:ietf:params:jmap:sieve": {"implementation": "Stalwart"}},
            "accounts": {"d1": {"name": "Ada", "accountCapabilities": {"urn:ietf:params:jmap:mail": {},
              "urn:ietf:params:jmap:submission": {},
              "urn:ietf:params:jmap:sieve": {"sieveExtensions": ["fileinto", "mailboxid", "imap4flags", "include"]}}}},
            "primaryAccounts": {"urn:ietf:params:jmap:mail": "d1"}, "username": "ada@example.org", "apiUrl": "/jmap/",
            "downloadUrl": "/download/{blobId}", "uploadUrl": "/upload/{accountId}/", "state": "x"}"""
    }
}

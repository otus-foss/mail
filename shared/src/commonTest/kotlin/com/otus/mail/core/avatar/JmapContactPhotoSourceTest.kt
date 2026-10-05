package com.otus.mail.core.avatar

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JmapContactPhotoSourceTest {
    private val request = AvatarRequest("ada", "grace@example.org", "Grace Hopper")

    @Test
    fun downloadsAContactBlobFromTheSeparateContactsAccount() =
        runTest {
            val fixture = ContactFixture()
            val result = fixture.source.photo(request, false)
            assertContentEquals(byteArrayOf(1, 2, 3), result?.bytes)
            assertEquals(1, fixture.downloads)
            assertTrue(fixture.apiBodies.all { "\"accountId\":\"contacts\"" in it })
            assertTrue(fixture.apiBodies.all { "urn:ietf:params:jmap:contacts" in it })
            assertTrue(fixture.apiBodies.last().contains("\"email\":\"grace@example.org\""))
        }

    @Test
    fun missingContactsCapabilityDoesNotCallAnyContactMethods() =
        runTest {
            val fixture = ContactFixture(session = MAIL_ONLY_SESSION)
            assertNull(fixture.source.photo(request, false))
            assertEquals(0, fixture.apiBodies.size)
        }

    @Test
    fun unknownLoginDoesNotSearchAnotherAccountsContacts() =
        runTest {
            val fixture = ContactFixture()
            assertNull(fixture.source.photo(request.copy(accountKey = "missing"), false))
            assertEquals(0, fixture.apiBodies.size)
        }

    @Test
    fun serverEmailSearchResultsMustMatchTheAddressIgnoringCase() =
        runTest {
            for (address in listOf("grace+tag@example.org", "g.race@example.org", "other@example.org")) {
                val fixture = ContactFixture(cardEmail = address)
                assertNull(fixture.source.photo(request, false))
                assertEquals(0, fixture.downloads)
            }
            for (address in listOf("grace@EXAMPLE.ORG", "Grace@example.org")) {
                assertContentEquals(
                    byteArrayOf(1, 2, 3),
                    ContactFixture(cardEmail = address).source.photo(request, false)?.bytes,
                    address,
                )
            }
        }

    @Test
    fun unsubscribedOrUnreadableBooksAreExcluded() =
        runTest {
            for (book in listOf("hidden", "denied")) {
                val fixture = ContactFixture(cardBook = book)
                assertNull(fixture.source.photo(request, false))
                assertEquals(0, fixture.downloads)
            }
        }

    @Test
    fun inlineContactImageNeedsNoExternalPermission() =
        runTest {
            val fixture = ContactFixture(media = """{"kind":"photo","uri":"data:image/png;base64,AQID"}""")
            assertContentEquals(byteArrayOf(1, 2, 3), fixture.source.photo(request, false)?.bytes)
            assertEquals(0, fixture.downloads)
        }

    @Test
    fun externalContactImagesRequirePermissionAndUseThePublicLoader() =
        runTest {
            var calls = 0
            val fixture =
                ContactFixture(
                    media = """{"kind":"photo","uri":"https://people.example/avatar.png"}""",
                    images =
                        AvatarImageLoader {
                            calls++
                            AvatarImage(byteArrayOf(9))
                        },
                )
            assertNull(fixture.source.photo(request, false))
            assertEquals(0, calls)
            assertContentEquals(byteArrayOf(9), fixture.source.photo(request, true)?.bytes)
            assertEquals(1, calls)
        }

    @Test
    fun deniedBlobAndMalformedInlineDataRetainTheFallback() =
        runTest {
            assertNull(ContactFixture(downloadStatus = HttpStatusCode.Forbidden).source.photo(request, false))
            assertNull(
                ContactFixture(media = """{"kind":"photo","uri":"data:image/png;base64,***"}""")
                    .source
                    .photo(request, false),
            )
        }

    @Test
    fun photoIsPreferredOverLogoAndPreferenceSelectsThePhoto() =
        runTest {
            val media = """{"a":{"kind":"logo","uri":"data:image/png;base64,CQ=="},
            "b":{"kind":"photo","pref":2,"uri":"data:image/png;base64,Ag=="},
            "c":{"kind":"photo","pref":1,"uri":"data:image/png;base64,AQ=="}}"""
            assertContentEquals(byteArrayOf(1), ContactFixture(mediaMap = media).source.photo(request, false)?.bytes)
        }
}

private class ContactFixture(
    session: String = CONTACT_SESSION,
    cardEmail: String = "grace@example.org",
    cardBook: String = "visible",
    media: String = """{"kind":"photo","blobId":"photo","mediaType":"image/png"}""",
    mediaMap: String = """{"p":$media}""",
    downloadStatus: HttpStatusCode = HttpStatusCode.OK,
    images: AvatarImageLoader = AvatarImageLoader { error("No external requests") },
) {
    val apiBodies = mutableListOf<String>()
    var downloads = 0
    private val engine =
        MockEngine { request ->
            assertEquals(ADA_SECRET_HEADER, request.headers[HttpHeaders.Authorization])
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when (request.url.encodedPath) {
                "/jmap/session" -> {
                    respond(session, headers = json)
                }

                "/jmap/" -> {
                    val body = (request.body as TextContent).text
                    apiBodies += body
                    val response =
                        if ("AddressBook/get" in body) {
                            """{"methodResponses":[["AddressBook/get",{"list":[
                        {"id":"visible","isSubscribed":true,"myRights":{"mayRead":true}},
                        {"id":"hidden","isSubscribed":false,"myRights":{"mayRead":true}},
                        {"id":"denied","isSubscribed":true,"myRights":{"mayRead":false}}]},"c0"]]}"""
                        } else {
                            """{"methodResponses":[["ContactCard/query",{"ids":["c"]},"c0"],
                        ["ContactCard/get",{"list":[{"id":"c","addressBookIds":{"$cardBook":true},
                        "emails":{"e":{"address":"$cardEmail"}},"media":$mediaMap}]},"c1"]]}"""
                        }
                    respond(response, headers = json)
                }

                "/download/contacts/photo/avatar" -> {
                    downloads++
                    respond(byteArrayOf(1, 2, 3), downloadStatus, headersOf(HttpHeaders.ContentType, "image/png"))
                }

                else -> {
                    error("Unexpected request ${request.url}")
                }
            }
        }
    private val store =
        InMemoryLoginStore().apply {
            save(JmapLogin("ada", "https://mail.example.org/jmap/session", "ada@example.org", "Ada"), "secret")
        }
    val source = JmapContactPhotoSource(JmapConnections(JmapClient(engine), store), store, images)
}

private const val CONTACT_SESSION = """
{"capabilities":{"urn:ietf:params:jmap:core":{},"urn:ietf:params:jmap:mail":{},"urn:ietf:params:jmap:contacts":{}},
 "accounts":{"mail":{"accountCapabilities":{"urn:ietf:params:jmap:mail":{}}},
   "contacts":{"accountCapabilities":{"urn:ietf:params:jmap:contacts":{}}}},
 "primaryAccounts":{"urn:ietf:params:jmap:contacts":"contacts"},"apiUrl":"/jmap/",
 "downloadUrl":"/download/{accountId}/{blobId}/{name}?type={type}"}
"""

/** Basic auth for the synthetic test login, ada@example.org with password secret. */
private const val ADA_SECRET_HEADER = "Basic YWRhQGV4YW1wbGUub3JnOnNlY3JldA=="

private const val MAIL_ONLY_SESSION = """
{"capabilities":{"urn:ietf:params:jmap:core":{},"urn:ietf:params:jmap:mail":{}},
 "accounts":{"mail":{"accountCapabilities":{"urn:ietf:params:jmap:mail":{}}}},
 "primaryAccounts":{"urn:ietf:params:jmap:mail":"mail"},"apiUrl":"/jmap/"}
"""

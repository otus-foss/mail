package com.otus.mail.feature.updates.data

import com.otus.mail.feature.updates.domain.NightlyBuild
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GitHubNightlyFeedTest {
    private fun feed(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = GitHubNightlyFeed(HttpClient(MockEngine { respond(body, status) }), "owner/nightly")

    @Test
    fun releasesGiveTheirNumbersApksAndNotesNewestFirst() =
        runTest {
            val body =
                """
                [
                  {"tag_name": "nightly-141", "body": "- fix(inbox): b", "published_at": "2026-10-13T22:47:55Z", "assets": [
                    {"name": "otus-mail-nightly-141.apk", "browser_download_url": "https://x/141.apk"}
                  ]},
                  {"tag_name": "nightly-142", "body": "- feat(sidebar): a\n- ci: c", "assets": [
                    {"name": "notes.txt", "browser_download_url": "https://x/notes.txt"},
                    {"name": "otus-mail-nightly-142.apk", "browser_download_url": "https://x/142.apk"}
                  ]}
                ]
                """.trimIndent()
            assertEquals(
                listOf(
                    NightlyBuild(142, "https://x/142.apk", listOf("- feat(sidebar): a", "- ci: c")),
                    NightlyBuild(141, "https://x/141.apk", listOf("- fix(inbox): b"), "2026-10-13"),
                ),
                feed(body).releases(),
            )
        }

    @Test
    fun noRepositoryOrAnUnknownTagGivesNothing() =
        runTest {
            assertTrue(feed("{}", HttpStatusCode.NotFound).releases().isEmpty())
            assertTrue(feed("""[{"tag_name": "v1.0", "assets": []}]""").releases().isEmpty())
        }

    @Test
    fun serverErrorFailsTheCheck() =
        runTest {
            assertFailsWith<IllegalStateException> { feed("", HttpStatusCode.InternalServerError).releases() }
        }
}

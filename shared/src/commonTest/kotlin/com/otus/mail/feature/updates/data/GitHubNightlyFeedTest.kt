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
import kotlin.test.assertNull

class GitHubNightlyFeedTest {
    private fun feed(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = GitHubNightlyFeed(HttpClient(MockEngine { respond(body, status) }), "owner/nightly")

    @Test
    fun latestReleaseGivesItsNumberAndApk() =
        runTest {
            val body =
                """
                {"tag_name": "nightly-142", "assets": [
                  {"name": "notes.txt", "browser_download_url": "https://x/notes.txt"},
                  {"name": "otus-mail-nightly-142.apk", "browser_download_url": "https://x/142.apk"}
                ]}
                """.trimIndent()
            assertEquals(NightlyBuild(142, "https://x/142.apk"), feed(body).latest())
        }

    @Test
    fun noReleaseOrAnUnknownTagGivesNothing() =
        runTest {
            assertNull(feed("{}", HttpStatusCode.NotFound).latest())
            assertNull(feed("""{"tag_name": "v1.0", "assets": []}""").latest())
        }

    @Test
    fun serverErrorFailsTheCheck() =
        runTest {
            assertFailsWith<IllegalStateException> { feed("", HttpStatusCode.InternalServerError).latest() }
        }
}

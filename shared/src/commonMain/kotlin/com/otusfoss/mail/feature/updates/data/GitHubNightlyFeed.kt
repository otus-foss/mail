package com.otusfoss.mail.feature.updates.data

import com.otusfoss.mail.feature.updates.domain.NightlyBuild
import com.otusfoss.mail.feature.updates.domain.NightlyFeed
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Reads the recent releases of the public [repository] that the nightly workflow publishes to. Each
 * release is tagged `nightly-<number>`, carries one APK, and lists its commits in its notes. The
 * repository must be public, because the installed app holds no GitHub token.
 */
class GitHubNightlyFeed(
    private val http: HttpClient,
    private val repository: String = REPOSITORY,
) : NightlyFeed {
    override suspend fun releases(): List<NightlyBuild> {
        val response =
            http.get("https://api.github.com/repos/$repository/releases?per_page=$PAGE") {
                header("Accept", "application/vnd.github+json")
            }
        if (response.status == HttpStatusCode.NotFound) return emptyList()
        check(response.status.isSuccess()) { "GitHub releases answered ${response.status}" }
        return json
            .decodeFromString<List<Release>>(response.bodyAsText())
            .mapNotNull { it.build() }
            .sortedByDescending { it.number }
    }

    @Serializable
    private class Release(
        @SerialName("tag_name") val tag: String,
        val body: String? = null,
        @SerialName("published_at") val published: String? = null,
        val assets: List<Asset> = emptyList(),
    ) {
        fun build(): NightlyBuild? {
            val number =
                tag
                    .removePrefix(TAG_PREFIX)
                    .takeIf { it != tag }
                    ?.toIntOrNull()
            val apk = assets.firstOrNull { it.name.endsWith(".apk") }
            if (number == null || apk == null) return null
            return NightlyBuild(number, apk.url, body.orEmpty().lines(), published.orEmpty().substringBefore('T'))
        }
    }

    @Serializable
    private class Asset(
        val name: String,
        @SerialName("browser_download_url") val url: String,
    )

    private companion object {
        // The nightly workflow in .github/workflows/nightly.yml publishes here, as prereleases.
        const val REPOSITORY = "otus-foss/otus-mail"
        const val TAG_PREFIX = "nightly-"

        /** About a month of nightlies; a phone further behind sees only these builds' notes. */
        const val PAGE = 30
        val json = Json { ignoreUnknownKeys = true }
    }
}

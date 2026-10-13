package com.otus.mail.feature.updates.data

import com.otus.mail.feature.updates.domain.NightlyBuild
import com.otus.mail.feature.updates.domain.NightlyFeed
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
 * Reads the latest release of the public [repository] that the nightly workflow publishes to. Each
 * release is tagged `nightly-<number>` and carries one APK. The repository must be public, because
 * the installed app holds no GitHub token.
 */
class GitHubNightlyFeed(
    private val http: HttpClient,
    private val repository: String = REPOSITORY,
) : NightlyFeed {
    override suspend fun latest(): NightlyBuild? {
        val response =
            http.get("https://api.github.com/repos/$repository/releases/latest") {
                header("Accept", "application/vnd.github+json")
            }
        if (response.status == HttpStatusCode.NotFound) return null
        check(response.status.isSuccess()) { "GitHub releases answered ${response.status}" }
        val release = json.decodeFromString<Release>(response.bodyAsText())
        val number =
            release.tag
                .removePrefix(TAG_PREFIX)
                .takeIf { it != release.tag }
                ?.toIntOrNull()
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") }
        return if (number != null && apk != null) NightlyBuild(number, apk.url) else null
    }

    @Serializable
    private class Release(
        @SerialName("tag_name") val tag: String,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private class Asset(
        val name: String,
        @SerialName("browser_download_url") val url: String,
    )

    private companion object {
        // The nightly workflow in .github/workflows/nightly.yml publishes here.
        const val REPOSITORY = "reazndev/otus-mail-nightly"
        const val TAG_PREFIX = "nightly-"
        val json = Json { ignoreUnknownKeys = true }
    }
}

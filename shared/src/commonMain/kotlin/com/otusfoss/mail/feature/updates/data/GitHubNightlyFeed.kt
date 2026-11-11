package com.otusfoss.mail.feature.updates.data

import com.otusfoss.mail.feature.updates.domain.NightlyBuild
import com.otusfoss.mail.feature.updates.domain.NightlyFeed
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
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
            .mapNotNull { it.build(repository) }
            .sortedByDescending { it.number }
    }

    @Serializable
    private class Release(
        @SerialName("tag_name") val tag: String,
        val body: String? = null,
        @SerialName("published_at") val published: String? = null,
        val assets: List<Asset> = emptyList(),
    ) {
        /** Null unless the APK is a `https` download of [repository]'s release with a SHA-256. */
        fun build(repository: String): NightlyBuild? {
            val number =
                tag
                    .removePrefix(TAG_PREFIX)
                    .takeIf { it != tag }
                    ?.toIntOrNull()
            val apk = assets.firstOrNull { it.name.endsWith(".apk") }?.takeIf { isReleaseDownload(it.url, repository) }
            val sha256 =
                apk
                    ?.digest
                    ?.removePrefix(SHA256_PREFIX)
                    ?.lowercase()
                    ?.takeIf { SHA256.matches(it) }
            if (number == null || apk == null || sha256 == null) return null
            return NightlyBuild(
                number,
                apk.url,
                body.orEmpty().lines(),
                published.orEmpty().substringBefore('T'),
                sha256,
            )
        }
    }

    @Serializable
    private class Asset(
        val name: String,
        @SerialName("browser_download_url") val url: String,
        /** GitHub's hash of the uploaded file, such as `sha256:8534…`. */
        val digest: String? = null,
    )

    private companion object {
        // The nightly workflow in .github/workflows/nightly.yml publishes here, as prereleases.
        const val REPOSITORY = "otus-foss/mail"
        const val TAG_PREFIX = "nightly-"
        const val SHA256_PREFIX = "sha256:"
        val SHA256 = Regex("[0-9a-f]{64}")

        /** About a month of nightlies; a phone further behind sees only these builds' notes. */
        const val PAGE = 30
        val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Whether [url] is a `https` download of a release asset of [repository] on GitHub, which redirects
 * to its own file storage. A feed could otherwise send the installer to any address.
 */
internal fun isReleaseDownload(
    url: String,
    repository: String,
): Boolean =
    runCatching { Url(url) }
        .getOrNull()
        ?.takeIf { it.protocol == URLProtocol.HTTPS && it.host == GITHUB_HOST && it.port == HTTPS_PORT }
        ?.encodedPath
        ?.let { path ->
            Regex("/${Regex.escape(repository)}/releases/download/$PATH_SEGMENT/$PATH_SEGMENT").matches(path)
        } == true

private const val GITHUB_HOST = "github.com"
private const val HTTPS_PORT = 443

/** A tag or file name; a leading dot is refused, so `..` cannot climb out of the repository. */
private const val PATH_SEGMENT = "[A-Za-z0-9_-][A-Za-z0-9._-]*"

package com.otus.mail.core.avatar

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom

/** Prefer large raster icons advertised by the website over small legacy favicons. */
class HttpWebsiteIconSource(
    private val images: HttpAvatarImageLoader,
) : DomainIconSource {
    override suspend fun icon(domain: String): AvatarImage? =
        avatarOrNull {
            val page = images.websitePage("https://$domain/") ?: return@avatarOrNull null
            websiteIconUrls(page).firstNotNullOfOrNull { avatarOrNull { images.load(it) } }
        }
}

internal fun websiteIconUrls(page: AvatarWebsitePage): List<String> {
    val links = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
    val attributes = Regex("([\\w-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
    val head = Regex("</head\\s*>", RegexOption.IGNORE_CASE).split(page.html, limit = 2).first()
    return links
        .findAll(head.replace(Regex("<!--[\\s\\S]*?-->"), ""))
        .mapNotNull { link ->
            val attrs =
                attributes.findAll(link.value).associate { match ->
                    match.groupValues[1].lowercase() to
                        match.groupValues
                            .drop(2)
                            .firstOrNull { it.isNotEmpty() }
                            .orEmpty()
                }
            val rel = attrs["rel"]?.lowercase()?.split(Regex("\\s+")) ?: return@mapNotNull null
            val touch = "apple-touch-icon" in rel || "apple-touch-icon-precomposed" in rel
            if (!touch && "icon" !in rel) return@mapNotNull null
            if (attrs["type"]?.contains("svg", ignoreCase = true) == true) return@mapNotNull null
            val href = attrs["href"]?.replace("&amp;", "&") ?: return@mapNotNull null
            if (href.isBlank()) return@mapNotNull null
            val url =
                runCatching {
                    URLBuilder()
                        .takeFrom(Url(page.url))
                        .apply {
                            parameters.clear()
                            fragment = ""
                            takeFrom(href)
                        }.buildString()
                }.getOrNull()
                    ?: return@mapNotNull null
            if (Url(url).encodedPath.endsWith(".svg", ignoreCase = true)) return@mapNotNull null
            WebsiteIcon(url, iconSize(attrs["sizes"].orEmpty(), touch))
        }.sortedByDescending { it.size }
        .map { it.url }
        .distinct()
        .take(MAX_WEBSITE_ICONS)
        .toList()
}

private fun iconSize(
    sizes: String,
    touch: Boolean,
): Int =
    Regex("(\\d+)x(\\d+)", RegexOption.IGNORE_CASE)
        .findAll(sizes)
        .map { minOf(it.groupValues[1].toIntOrNull() ?: 0, it.groupValues[2].toIntOrNull() ?: 0) }
        .maxOrNull() ?: if (touch) DEFAULT_TOUCH_ICON_SIZE else 0

private class WebsiteIcon(
    val url: String,
    val size: Int,
)

private const val MAX_WEBSITE_ICONS = 8
private const val DEFAULT_TOUCH_ICON_SIZE = 180

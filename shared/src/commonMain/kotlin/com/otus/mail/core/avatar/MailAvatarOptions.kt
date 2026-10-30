package com.otus.mail.core.avatar

import com.otus.avatar.AvatarOptions
import com.otus.avatar.IconProxy
import com.otus.avatar.isPublicAvatarUrl

/**
 * Sender avatars always look up contact photos on the user's own servers. [websiteIcons] also lets
 * them load photos that contact cards link to and website icons from [source]. A Bulwark source
 * without a usable [bulwarkAddress] shows no icons rather than asking the websites, even for a
 * contact styled to show its website.
 */
fun mailAvatarOptions(
    websiteIcons: Boolean,
    source: IconSource = IconSource.Websites,
    bulwarkAddress: String = "",
): AvatarOptions {
    val proxy =
        when (source) {
            IconSource.Websites -> null
            IconSource.DuckDuckGo -> DUCKDUCKGO
            IconSource.Bulwark -> bulwarkProxy(bulwarkAddress)
        }
    return AvatarOptions(externalContactImages = websiteIcons, domainIcons = websiteIcons, iconProxy = proxy)
}

/**
 * [address] without a trailing slash, adding `https://` when it names no scheme, or null unless
 * avatars may load from it: HTTPS on the default port at a public host, with no query or fragment.
 */
fun bulwarkBase(address: String): String? {
    val trimmed = address.trim().trimEnd('/')
    val url = if ("://" in trimmed) trimmed else "https://$trimmed"
    return url.takeIf { trimmed.isNotEmpty() && it.none { char -> char in URL_PARTS } && isPublicAvatarUrl(it) }
}

/**
 * Bulwark's sender icon and BIMI logo routes, which fetch from its server rather than the device.
 * An unusable address still names a proxy, one that loads nothing.
 */
private fun bulwarkProxy(address: String): IconProxy =
    bulwarkBase(address)?.let { IconProxy("$it/api/favicon?domain={domain}", "$it/api/bimi?domain={domain}") }
        ?: IconProxy(iconUrl = null)

private val DUCKDUCKGO = IconProxy("https://icons.duckduckgo.com/ip3/{domain}.ico")

// A query, fragment, or `{domain}` in the address would change where the routes point.
private const val URL_PARTS = "?#{}"

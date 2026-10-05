# Sender and attendee avatars

## Implemented API

`core/avatar/AvatarService` is common Kotlin, independent of mail, calendar, Compose, and platform
image types. It resolves an `AvatarRequest(accountKey, email, name)` in this order:

1. A contact photo or logo through the injected `ContactPhotoSource`.
2. A website-domain icon through `AvatarImageLoader`, when `AvatarOptions.domainIcons` is enabled.
   An injected `DomainIconSource` can supply a cached favicon when `domainIconProxy` is also enabled.
3. Generated initials, with an email-derived index for the app's named theme colors.

Every result includes the generated fallback. Image decoding failures also display this fallback.
Email matching trims whitespace and lowercases the domain. The local part remains case-sensitive;
dots and plus tags are preserved. Names do not determine contact identity. Changing a display name
changes initials without changing the address's color.

`AvatarOptions.externalContactImages`, `domainIcons`, and `domainIconProxy` default to false. Embedded contact
images and authenticated JMAP blobs still work. Public image requests use a separate HTTP client
that has no account credentials. Mail enables all three options for inbox senders, so contacts with
external photos and senders without a contact photo can display images automatically. Callers of
the reusable service can keep remote requests disabled. No appearance-setting switch exists yet.

```kotlin
val images = HttpAvatarImageLoader()
val service = AvatarService(contacts = contactPhotos, images = images)
val result = service.resolve(
    AvatarRequest(loginId, "ada@company.example", "Ada Lovelace"),
    AvatarOptions(domainIcons = true),
)
```

Use an app-private, stable login ID for `accountKey`, never the display name or a password. Call
`service.clear()` after account changes or a contact refresh. Create one service per app process;
close an explicitly owned `HttpAvatarImageLoader` when its owning scope ends.

## Mail integration

`MailServices` wires `JmapContactPhotoSource` to the existing JMAP connections and login store.
It checks both session and account contacts capabilities, selects the primary contacts account or
the first supported one within that login, and reads subscribed, readable address books. A filtered
`ContactCard/query` feeds `ContactCard/get` through a result reference. Results are checked locally
for exact email membership and address-book membership. The query inspects at most 32 candidates.
It does not synchronize an entire address book.

Photos precede logos; lower JSContact `pref` values precede higher ones. Blob IDs use the session's
advertised `downloadUrl` template and trusted origins. Embedded Base64 data URIs are also supported.
External contact URLs require the separate option. PNG, JPEG, and WebP contact images are accepted.
Missing capabilities, permissions, images, or optional request failures leave the generated avatar.
Cancellation propagates and stops work from an obsolete inbox refresh.

`InboxFeedState` publishes messages before resolving avatars, deduplicates sender lookups within
each login, and updates both inbox rows and the people picker. UI components receive values only.
The design-system `Avatar` draws a supplied bitmap or initials; it never fetches an image.
Android decoding downsamples to at most 256 pixels on the longest edge and refuses dimensions above
4096. iOS decoding also refuses dimensions above 4096. Unsupported or malformed images retain initials.
Generated backgrounds use `accentDark`, `bgLight`, and `bgDark`, with `text1` initials.

## Caching and domain icons

The service keeps up to 64 account/email/options entries for five minutes, including misses. It
coalesces concurrent requests for the same key and bounds lookups to four concurrent operations.
Clearing the cache prevents in-flight results from repopulating it. Names are excluded from cache
keys so callers always get initials for the current display name. Images are capped at 512 KiB each,
so the default encoded-image cache is bounded to 32 MiB. Callers must not mutate returned image bytes.

Mail tries HTTPS `apple-touch-icon.png`, `favicon.png`, and `favicon.ico` on the sender's exact
hostname first. If none succeeds, it tries the same paths on the registrable website domain from
the bundled Public Suffix List. For example, `email.openai.com` falls back to `openai.com`. An exact
root hostname is fetched only once. Multi-label suffixes such as `co.uk`, private hosting suffixes
such as `github.io`, wildcards, and exceptions are respected. Bare public suffixes, IP literals,
internal hosts, and malformed hostnames retain initials. Shared mail providers such as `proton.me`
are eligible for a website logo; this identifies the provider, not the individual sender.

When direct website images fail, Mail's injected fallback requests
`https://icons.duckduckgo.com/ip3/{domain}.ico`. This public favicon cache receives only the website
domain, never a complete email address, saved login ID, password, or account authorization header.
The service has no baked-in cache provider: callers inject `DomainIconSource`, and must enable
`domainIconProxy` as well as `domainIcons`. Contacts always take priority. No message-body images or
email-hash avatar services are used. This fallback also covers websites that block direct requests.

`PublicSuffixDomainResolver` accepts an injected suspend loader for the rules. Mail wiring reads
`files/public_suffix_list.dat` from packaged Compose resources. The parser is common Kotlin,
loads once off the UI thread, and never downloads rules at runtime. The service defaults to using
an exact hostname unless a resolver is injected. Calendar includes the same parser and resource
for its future account wiring. IDNs require an ASCII hostname supplied by the caller.

The list snapshot is `2026-10-01_23-02-52_UTC`, upstream commit
`6cd82aff889e3d64e5e03bc5c1f43da1934a960a`, from
[publicsuffix.org](https://publicsuffix.org/list/public_suffix_list.dat). Source SHA256 is
`e0fe072d26b0536525badea237953ff451c9f8e64c9d02c6daa81a4491d2fc66`.
Rules are lowercased and converted label by label to ASCII IDNA while retaining wildcard and
exception markers. Original comments and the MPL 2.0 notice remain; the license ships as
`public_suffix_list_MPL-2.0.txt`. Refresh both copies together when updating this bundled data.
ICO display depends on the platform decoder; Android tests cover PNG and bitmap-encoded entries.

The public loader follows at most three redirects, checking each target for HTTPS, a public
hostname, and absence of URL credentials. Relative paths and public CDN redirects are supported.
It refuses credential-bearing URLs, SVG/HTML responses, oversized bodies, and non-HTTPS URLs.
The entire load, including redirects, times out after five seconds. Domain icons represent a website or organization;
they are not proof that a sender is authentic. There is no persistent image cache or offline contact sync.

## Reuse across repositories

Mail and Calendar contain aligned copies of `Avatar.kt`, `AvatarService.kt`, `AvatarDomains.kt`, and
`HttpAvatarImageLoader.kt`, and `PublicSuffixDomainResolver.kt` under their own `core/avatar`
package, with the same common behavior tests and Public Suffix List resources.
Only package names differ. Each app also owns its bitmap decoder and avatar element. Both repositories
build independently, with no sibling-checkout dependency, publishing pipeline, or shared credentials.
Keep the portable files and tests aligned when changing the service contract.

Mail supplies the JMAP adapter. Calendar can inject its future contact repository through
`ContactPhotoSource`; calendar accounts and attendee screens remain unimplemented. The Mail adapter
uses only this contract and the portable `com.otus.jmap` module, so it can move with that module once
cross-repository library distribution is chosen.

## Protocol references and validation

- [RFC 9610, contacts capability and ContactCards](https://www.rfc-editor.org/rfc/rfc9610.html).
- [RFC 9553, contact media](https://www.rfc-editor.org/rfc/rfc9553.html#section-2.6.4).
- [RFC 8620, blob downloads](https://www.rfc-editor.org/rfc/rfc8620.html#section-6.2).

Common tests cover resolution priority, account/options isolation, missing capabilities, exact email
matching, address-book permissions, blobs/data URIs/external URLs, website-domain resolution,
provider icons, icon-cache options and domain-only requests, cache expiry/eviction/invalidation,
cancellation, credential isolation, redirects, and response-size limits. Inbox tests verify that
messages appear before photos, repeated senders share a result, and Mail enables domain fallbacks.
Android rendering tests cover downsampled PNG photos, PNG- and bitmap-encoded ICO favicons, and
malformed data. Physical Android validation confirmed images on both Proton sender rows, the OpenAI
logo, and two other live sender-domain icons in the inbox. The Proton images' source was not
instrumented; real-server contact-photo source verification and iOS runtime validation remain open.

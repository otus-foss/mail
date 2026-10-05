# Sender and attendee avatars

## Implemented API

`core/avatar/AvatarService` is common Kotlin, independent of mail, calendar, Compose, and platform
image types. It resolves an `AvatarRequest(accountKey, email, name)` in this order:

1. A contact photo or logo through the injected `ContactPhotoSource`.
2. A website-domain icon through `AvatarImageLoader`, when `AvatarOptions.domainIcons` is enabled.
   An injected `DomainIconSource` can supply a cached favicon when `domainIconProxy` is also enabled.
3. Generated initials, with an email-derived index for the app's named theme colors.

Every result includes the generated fallback. Image decoding failures also display this fallback.
Email matching trims whitespace and ignores case in the whole address, because real mail servers
treat `John@` and `john@` as one mailbox. Dots and plus tags are preserved. Names do not determine contact identity. Changing a display name
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
`service.removeAccount(loginId)` after logout or reconnect, and `service.clear()` for an explicit contact refresh. Ordinary inbox reloads retain the cache. Create one service per app process;
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

`InboxFeedState` restores local avatars before publishing refreshed messages, then resolves new
senders progressively. Reloading does not clear avatars or replace known photos with initials.
Lookups are deduplicated by normalized email within each login, and results update both inbox rows
and the people picker. New senders at a known domain immediately reuse its icon while their own
contact lookup runs. UI components receive values only.
The design-system `Avatar` draws a supplied bitmap or initials; it never fetches an image.
`rememberAvatarImage` decodes off the main thread and keeps the 64 most recently decoded images.
Concurrent rows share a pending decode, and every row adopts its result even if the cache fills
before that row's effect starts. Remaining rows retry when the decoding row leaves composition.
Rows scrolling back into view reuse their bitmap. Android and iOS (ImageIO thumbnails) decode at most
256 pixels on the longest edge and refuse dimensions above 4096. Unsupported or malformed images
retain initials.
Generated backgrounds use `accentDark`, `bgLight`, and `bgDark`, with `text1` initials.

## Caching and domain icons

The service keeps up to 64 recently used contact/domain records in memory. Contact keys include the
login, normalized email and external-image policy. Website icons use the exact hostname and proxy
policy, shared across senders and accounts. Two addresses at `proton.me` fetch the website icon once;
their individual contact photos still take priority and remain separate. Display names never enter
cache keys, so initials always follow the current name.

Android and iOS entry points supply `FileAvatarStore` in the app's private cache directory. It survives
process restarts and uses hashed filenames, atomic record replacement, a 32 MiB disk limit and at most
256 records. Every image is capped at 512 KiB. Successful images are reused for 30 days; missing
images are retried after five minutes. Existing images remain visible during an expired/offline
lookup. The OS may reclaim cache files. Removing or reconnecting an account deletes its private
contact records while retaining public website icons. Callers must not mutate image bytes.

Concurrent lookups share a pending result for each contact or domain key. At most eight fetches run
at once. Cancellation of the owner lets waiting callers retry. Cache invalidation also covers
lookups waiting for a fetch permit and prevents old results from repopulating memory or disk.

Mail first reads a bounded portion of the website's HTML and tries advertised raster icon links,
prioritizing larger declared sizes and Apple touch icons. Relative links use the final website URL
after redirects. This finds Proton's 180 px touch icon rather than enlarging the 32 px proxy favicon.
If discovery fails, it tries HTTPS `apple-touch-icon.png`, `favicon.png`, and `favicon.ico` on the
sender's exact hostname, then on its registrable website domain from the bundled Public Suffix List.
All direct website attempts together have an 8-second budget. For example, `email.openai.com` falls
back to `openai.com`. An exact root hostname is fetched only once. Multi-label suffixes such as `co.uk`, private hosting suffixes
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
Image loads refuse credential-bearing URLs, SVG/HTML responses, oversized bodies, and non-HTTPS
URLs. Website discovery reads at most 64 KiB of HTML; it never executes scripts or displays HTML.
The entire load, including redirects, times out after five seconds. Domain icons represent a website or organization;
they are not proof that a sender is authentic. The image cache supports offline display; it does not
synchronize contacts offline.

## Reuse across repositories

Mail and Calendar each own their `core/avatar` implementation and Public Suffix List resource.
Mail additionally persists contact/domain images, discovers advertised website icons, and restores
images before inbox reloads. These changes have not been copied into Calendar, whose attendee
integration is still deferred. Each app also owns its bitmap decoder and avatar element. Both repositories
build independently, with no sibling-checkout dependency, publishing pipeline, or shared credentials.
Keep shared contract changes explicit when maintaining the repository copies.

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
cancellation, credential isolation, redirects, and response-size limits. Persistent-cache tests cover
process recreation, bounded disk usage, corrupt records and account removal. Inbox tests verify local
restoration before network work, sharing icons with new senders, and retaining image identity when
bytes are restored from disk after memory eviction.
Android rendering tests cover downsampled PNG photos, PNG- and bitmap-encoded ICO favicons, and
malformed data. Pixel 9a validation confirmed that the stored Proton domain icon is 180 × 180 px,
remains sharp in the inbox, and survives a process restart. A full pull-to-refresh recording kept
avatars visible throughout the reload, and the stored domain images were reused without rewriting
their files. iOS compilation and runtime validation require macOS/Xcode and remain unrun.

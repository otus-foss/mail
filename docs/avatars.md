# Sender avatars

Mail uses the `com.otus:avatar` library from otus-meta to resolve sender avatars. The service, its
caching, website-icon rules, and privacy limits are documented in
[otus-meta's avatar guide](https://github.com/reazndev/otus-meta/blob/main/docs/avatar.md). This
guide covers how Mail wires and uses it.

`MailServices` enables `externalContactImages`, `domainIcons`, and `domainIconProxy` for inbox
senders. Its injected `DomainIconSource` requests `https://icons.duckduckgo.com/ip3/{domain}.ico`,
which receives only the website domain. Mail reads the Public Suffix List from
`files/public_suffix_list.dat` in its Compose resources, with its MPL 2.0 notice in
`public_suffix_list_MPL-2.0.txt`. Android and iOS entry points supply a `FileAvatarStore` in the
app's private cache directory.

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

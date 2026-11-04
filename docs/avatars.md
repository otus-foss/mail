# Sender avatars

Mail uses the `com.otus:avatar` library from otus-meta to resolve sender avatars. The service, its
caching, website-icon rules, and privacy limits are documented in
[otus-meta's avatar guide](https://github.com/reazndev/otus-meta/blob/main/docs/avatar.md). This
guide covers how Mail wires and uses it.

Mail reads the Public Suffix List from `files/public_suffix_list.dat` in its Compose resources,
with its MPL 2.0 notice in `public_suffix_list_MPL-2.0.txt`. Android and iOS entry points supply a
`FileAvatarStore` in the app's private cache directory.

## Website icons

The **Website icons** switch under Settings › Privacy & security decides whether
avatars may load images from servers other than the user's own. It is off by default and is saved
with the other behavior settings in `MailBehavior.websiteIcons`, under a key of its own since it
turned off by default: the earlier key held on for anyone who saved any setting. `mailAvatarOptions`
turns it into `AvatarOptions`: while it is on, `externalContactImages` and `domainIcons` are
enabled for inbox senders, account avatars, and the senders of folder rules. Contacts with linked
photos and senders without a contact photo then show images automatically. Linked photo hosts and
sender websites each see the device's IP address, and a sender's website learns that its mail was
listed.

While Website icons is on, **Icons from** (`MailBehavior.iconSource`) picks where website icons
come from. `mailAvatarOptions` turns the choice into `AvatarOptions.iconProxy`:

- **Sender websites**, the default, asks each sender's registrable domain directly.
- **DuckDuckGo** asks `https://icons.duckduckgo.com/ip3/{domain}.ico` instead. DuckDuckGo receives
  the website domain and the device's IP address. Sender websites receive nothing.
- **Bulwark** asks the user's own [Bulwark](https://github.com/bulwarkmail/webmail) webmail server
  at `MailBehavior.bulwarkAddress`: `/api/favicon?domain={domain}` for icons and
  `/api/bimi?domain={domain}` for brand logos. The server fetches them from DuckDuckGo and the
  sender's BIMI record, so only it sees the device's IP address. An address without a scheme gets
  `https://`. `bulwarkBase` accepts only what the public image loader may request (HTTPS on port
  443 at a public host) and no query, fragment, or `{`. Any other address becomes a proxy that
  loads nothing, so no icons show, not even for a contact styled to show its website. The
  routes need no login, so Otus sends no credentials. A Bulwark admin can turn them off with its
  no-third-party-traffic mode, which leaves initials.

A proxy miss never falls back to the sender's website. Anyone who turned on the earlier **Ask
DuckDuckGo** switch reads as the DuckDuckGo choice. `IconSourceSheet` saves the source and address only on
Done, and only with a usable Bulwark address, so a half-typed address is never asked. Changing the choice or the saved address
reloads the inbox's avatars.

### Brand logos

The inbox requests every `Authentication-Results` field (`header:Authentication-Results:asText:all`).
`dmarcPassDomain` reads only the topmost one, which the receiving server adds; a sender can add
fields further down. That field counts only when its server id (RFC 8601 `authserv-id`) is the
login's JMAP host, its parent domain, or another host of that domain, such as `mx.example.net`
beside `mail.example.net`. A server that added no field would otherwise leave the sender's own on
top. A server whose id names another domain shows no brand logos. A message counts when every
`dmarc=` result in that field is `pass` and names the From domain in `header.from`. A `;` or space
inside a comment or quoted string does not split, so an envelope address or a `reason` cannot
smuggle in a pass. The domain goes to `MailSender.brandDomain` and on
to `AvatarRequest.brandDomain`; inbox lookups keep a sender's mail with and without a pass apart.
Email objects cached before this
change lack the header, so their senders show the icon until the message changes.

Turned off, Mail loads only contact photos stored as JMAP blobs or embedded data URIs, which come
from the user's own JMAP server; other senders show initials. Contact cache keys include the
external-image option, and website icons are looked up only while `domainIcons` is on, so icons
saved earlier are neither shown nor fetched again while the switch is off. Turning it back on
reuses them. Each inbox or folder load reads the switch once. Changing the switch reloads the
inbox at once, which stops the lookups still running from its previous load. Folder pages and the
Folders page look up avatars only while they are open. Avatars shown under the old setting are not
carried into the next load. Folder rule senders keep their avatars per setting and resolve again
when the Folders page opens.

## Mail integration

`MailServices` wires `JmapContactPhotoSource` to the existing JMAP connections and login store.
It checks both session and account contacts capabilities, selects the primary contacts account or
the first supported one within that login, and reads subscribed, readable address books. A filtered
`ContactCard/query` feeds `ContactCard/get` through a result reference. Results are checked locally
for exact email membership and address-book membership. The query inspects at most 32 candidates.
It does not synchronize an entire address book.

Photos precede logos; lower JSContact `pref` values precede higher ones. Blob IDs use the session's
advertised `downloadUrl` template and trusted origins. Embedded Base64 data URIs are also supported.
External contact URLs load only while Website icons is on. PNG, JPEG, and WebP contact images are
accepted. Missing capabilities, permissions, images, or optional request failures leave the
generated avatar. Cancellation propagates and stops work from an obsolete inbox refresh.

`InboxFeedState` restores local avatars before publishing refreshed messages, then resolves new
senders progressively. Reloading with the same Website icons setting does not clear avatars or
replace known photos with initials.
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

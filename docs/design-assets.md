# Design assets

Material Symbols Rounded is the selected icon family. Individual Android XML vectors are bundled
in common Compose resources for Android and iOS; no icon font or full icon-library dependency.
Icons use the upstream default weight, unfilled, 24 px optical size, displayed in compact 20 dp slots.

- Source: https://github.com/google/material-design-icons/tree/737e3324305806514d7909874fa1818ae1808232/symbols/android
- License: Apache 2.0, bundled as `material_symbols_LICENSE.txt`.
- Icons: chevron_left, search, notifications, person, calendar_add_on, inbox, alternate_email, filter_list, attach_file, close, mark_email_unread, settings, chevron_right, tune, unfold_more.

Figtree Regular is used by the context panel elements, matching the Figma typography.

- Source: https://github.com/google/fonts/tree/9710da1eacb3be272583c3224dcb70f9da6eadbb/ofl/figtree
- License: SIL Open Font License, bundled as `figtree_OFL.txt`.

Both licenses ship in the app's Compose resource files directory. Resource names retain their
source identity. Android theme-tint references are removed and the white path fill is made
explicit for the multiplatform XML parser; the `Icon` composable applies the named theme tint. The original Figma icons were intentionally replaced by Material Symbols Rounded
at the user's request. Theme colors remain the supplied Otus palette.

The bundled Public Suffix List supports website-domain avatar lookup. Its ASCII-normalized snapshot
and MPL 2.0 license ship in the Compose resource files directory. This is protocol data, not artwork;
see [avatars](avatars.md) for the upstream version, source hash, and update requirements.

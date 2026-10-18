---
title: "Check JMAP capabilities and keep providers out of common code"
scope: "file"
path: ["**/jmap/**", "**/data/**", "**/core/mail/**", "**/push/**", "**/accounts/**", "**/avatar/**"]
severity_min: "high"
---

## Instructions

Otus works with any JMAP server. Check protocol code against RFC 8620, RFC 8621, and the JMAP
Calendars and Contacts specifications. Flag these:

- A call to a JMAP method or a use of a property without checking that the session advertises the
  capability that defines it.
- Behavior that assumes one provider, such as a hard-coded host, a vendor-only property, or a
  mailbox found by name instead of by `role`.
- JMAP wire types, such as serializable request or response classes, leaking into domain models or
  UI state. Map them at the data boundary.
- Errors that a method response can return, such as `stateMismatch` or `cannotCalculateChanges`,
  left unhandled where the spec says the client must recover.
- Requests that fetch every property or every object when the caller needs a few. Use
  `properties`, paging, and `/changes`.

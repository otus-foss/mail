---
title: "Keep domain, data, and presentation separate"
scope: "file"
path: ["**/*.kt"]
severity_min: "high"
---

## Instructions

The architecture checker reads imports only. Review what it cannot see, including type aliases,
re-exports, and dependencies passed through other types. Flag these:

- Presentation code that uses a data implementation instead of a domain contract.
- Domain code that touches Compose, Android, UIKit, Ktor, SQLDelight, or other networking,
  storage, or UI APIs.
- One feature reaching into another feature's internals. A shared contract belongs in `core`, or
  app wiring composes the two features.
- `commonMain` code that depends on Android, JVM, or Apple-only APIs. Platform code belongs in the
  matching platform source set.
- New catch-all `utils`, `helpers`, or `manager` packages, and files whose name does not match
  their main responsibility.
- New wrapper types, factories, generic base classes, or interfaces with a single implementation
  and no caller that needs the indirection.

---
title: "Write fluent, immutable Kotlin"
scope: "file"
path: ["**/*.kt"]
severity_min: "medium"
---

## Instructions

Flag these:

- A mutable list, map, or set built up in a loop when a chain such as `filter`, `map`,
  `associateBy`, or `groupBy`, or a builder such as `buildList`, `buildMap`, or `buildJsonObject`
  reads as well.
- An index loop over a collection where `forEach`, `mapIndexed`, or `withIndex` fits.
- A mutable state class, or a `var` field on UI state, where an immutable value changed with `copy`
  works.
- Java-style setters that return `this`. Use a type-safe builder lambda with a receiver, named
  arguments, or `copy`.
- Side effects chained through `let` or `also`.
- Composable configuration passed as a settings object instead of a `Modifier` extension that
  returns its receiver.

Do not flag a loop or a local `var` that handles early exits, retries, or cycle tracking more
clearly than a chain.

## Examples

### Bad example

```kotlin
val unread = mutableListOf<Email>()
for (email in emails) {
    if (!email.isSeen) unread.add(email)
}
```

### Good example

```kotlin
val unread = emails.filterNot { it.isSeen }
```

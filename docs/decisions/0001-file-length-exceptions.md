# File-length enforcement and exceptions

Status: accepted by the user.

Keep the existing 300-line Kotlin and 200-line Swift defaults. Count physical lines, including
comments and blank lines, so formatting cannot conceal file size. Prefer cohesive files around
100–200 lines.

The user requested an explicit escape hatch for exceptional files. A first-line
`// otus-file-length: <limit> -- <reason>` raises only the file-size limit. It requires a finite
numeric budget and a meaningful rationale, and fails when malformed, duplicated, misplaced,
unnecessary, or exceeded. Other architecture and complexity checks remain active.

Use one implementation for Kotlin and Swift in the architecture gate. Disable SwiftLint's duplicate
file-length check to avoid rejecting valid exceptions, while retaining its function/type limits. The
new checker has boundary, malformed-exception, stale-exception, and isolation regression tests.

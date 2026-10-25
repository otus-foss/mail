---
title: "Do not weaken checks to make a change pass"
scope: "pull-request"
severity_min: "critical"
---

## Instructions

Flag any change in this pull request that loosens quality policy without a matching decision
record in `docs/decisions/`. That includes:

- `@Suppress`, `@file:Suppress`, `ktlint-disable`, formatter-off markers, or new lint baselines.
- Skipped, ignored, or deleted tests that the pull request does not replace.
- CI steps marked `continue-on-error`, removed checks, or hook changes that skip the Spotless,
  Ruff, or commit message checks.
- Changes to `tools/` checkers, `config/detekt.yml`, or Spotless settings that accept more than
  before.
- Unpinned or ranged dependency and tool versions.
- A new or raised `// otus-file-length:` exception when the file could be split by
  responsibility instead.

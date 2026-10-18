---
title: "Reusable UI lives in Meta; screens are complete and plain"
scope: "file"
path: ["**/presentation/**"]
severity_min: "medium"
---

## Instructions

Every element, reusable component, visual token, and symbol comes from otus-meta's
`com.otus.designsystem`, even when only this app uses it. The `ui-ownership` check catches files in
the wrong folder. Review what it misses:

- A composable in `presentation/components` that has no feature knowledge and could serve another
  screen or app. Suggest moving it to Meta.
- Sizes, radii, paddings, or colors written as literals instead of Meta tokens.
- Touch targets smaller than 48 dp.
- A new screen or list without an empty, loading, and error state.
- UI copy in title case, or button labels that do not start with a verb.
- More than one primary action on a screen. Secondary actions belong in a menu or sheet.

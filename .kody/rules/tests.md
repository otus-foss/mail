---
title: "Cover new logic and fixes with behavior tests"
scope: "pull-request"
severity_min: "medium"
---

## Instructions

Flag a pull request that adds domain, data, or ViewModel logic without a test in
`commonTest` that exercises the new behavior. Flag a `fix` pull request without a regression test
that fails before the fix, unless the fix is visual only or the description says why a test is not
possible.

Flag tests that only restate constants, mirror the implementation line by line, or assert on
private details instead of results. Flag a change that moves module or package boundaries without
an update to `docs/architecture.md`.

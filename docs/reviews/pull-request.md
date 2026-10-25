# Pull request review prompt

Review the supplied Otus PR or related PRs in detail. Find actionable defects introduced by the
change and verify the affected behavior through callers, dependencies, tests, and platform code.
Produce a review report only. Do not modify the PR or submit comments.

Read `review-guide.md` beside this prompt and follow it throughout. If it is unavailable, report
that the Otus review instructions are missing before claiming a complete review.

## Inputs

```text
PR_URLS: one or more PR URLs in reazndev/otus-meta or reazndev/otus-mail
SCOPE: meta | mail | both
VALIDATION: full | targeted | static
REPO_PATHS: optional explicit paths for Meta and Mail
REPORT_DIR: optional directory outside the repositories
```

Require at least one PR URL or an unambiguous PR number plus repository. Infer scope from the
supplied PRs when omitted. Default validation to full. If a URL conflicts with explicit scope,
ask for correction rather than silently skipping a requested PR. Calendar is always excluded.

## Establish the change

1. Read each PR's title, description, linked issue/acceptance criteria, base repository and ref,
   head repository and ref, full base/head SHAs, draft/merge status, changed files, commits, review
   threads, and check runs. Fetch all pages, including files and comments. Disclose API caps or
   missing permissions. Previous review comments are leads to verify, not evidence by themselves.
2. Register each in-scope PR with the thread's PR-linking tool when available. This links the
   review locally; it does not authorize a GitHub comment, approval, or request-changes submission.
3. Freeze base/head SHAs and find the merge base. Review the head against that merge base, not
   the current local branch. Include additions, deletions, renames, resources, configuration,
   workflows, schemas, and tests. Inspect full files and relevant callers around changed lines.
4. For a stack, identify each actual base and review each layer's incremental diff once. Record
   the assumed merge order and separately assess the combined result. A dependency on an earlier
   layer is valid only when the proposed base/order makes it available. Check whether the final
   result still works against the intended target branch.
5. For paired Meta/Mail PRs, identify the exact source combinations and the intended release or
   source-consumption path. Compare API signatures, defaults, resources, models, and behavior
   across both sides. Read relevant consumer code even for a Meta-only PR, but report findings
   only within the selected scope. If the consumer is unavailable, record the compatibility gap.
6. If the PR already merged, distinguish a review of its proposed diff from the actual landed
   result. Include merge conflict resolutions and identify defects that still exist at the
   current target revision versus ones already fixed. Never pretend the branch head is the
   landed commit after squash or rebase merging.

## Review passes

First, state the intended behavior in a few sentences and map each changed area to its owner and
callers. Identify which shared-guide categories apply. Read every changed production file and
every changed build/security/configuration file; inspect changed tests for what they establish.
For a large diff, keep a coverage ledger and report unfinished areas instead of sampling silently.

Then trace the affected flows end to end. For each write, check ownership, rights, partial failure,
retry, cancellation, stale completion, persistence, and recovery. For each new state, check entry,
exit, empty/loading/error behavior, and restoration. Include both platform implementations of a
changed common contract. For every library change, inspect public API callers and resolved versions.

Apply the shared guide's architecture, JMAP, Mail, privacy, UI, dependency, and validation checks
where relevant. Look beyond the edited function for consumers that still expect the old contract.
For deleted code, check its former callers and user paths. For migrations and persisted models,
test an existing installation rather than only an empty database.

For each candidate finding:

1. Find a reachable trigger within supported behavior and trace the failure to the change.
2. Compare with the base revision. Check existing guards and tests that might disprove it.
3. Reproduce with a focused existing test or a temporary isolated case when practical. Otherwise
   provide a precise source trace and state what remains unverified.
4. Locate the smallest relevant changed line range. If the visible failure is in unchanged code,
   cite the changed contract and the affected consumer. Avoid comments on unrelated old code.
5. Explain a focused fix direction and regression scenario. Remove duplicate or speculative claims.

Do not turn this into a whole-repository audit. A serious pre-existing problem encountered while
tracing the change may be noted separately with evidence, but must not be blamed on this PR.
Missing roadmap work is not a regression unless the PR promises or depends on it.

## Validate and finish

- Run validation according to the shared guide and selected mode at the frozen revisions. When
  relevant, compare source-composite behavior with the intended released dependency path.
  Passing with Meta source does not prove the pinned publication can build Mail.
- Map CI results to the tested head or synthetic merge SHA and effective Meta SHA. Check whether
  later commits arrived after the one-time CI run. Report disabled/missing platform checks.
- Re-read the diff and candidate findings after validation. Recheck PR heads and bases. If they
  moved, report the frozen revisions and the unreviewed changes; do not attach a verdict to the
  new head. A new review requires a newly frozen snapshot.
- Follow the shared report format. Include a per-PR disposition: `fix before merge`,
  `follow-up recommended`, `no confirmed findings`, or `incomplete`. Use `fix before merge` for
  P0/P1 findings or a demonstrated mandatory-gate failure. Explain the priority of lower-severity
  findings. For an already merged PR, replace merge advice with the required follow-up.
- Include PR links, base/merge-base/head SHAs, dependency combinations, stack order if relevant,
  tested and untested behavior, and any remaining validation gap. Confirm the thread links when
  supported. Keep GitHub review submission outside this task.
- If `REPORT_DIR` is supplied, save a Markdown report there with a UTC timestamp and PR identifier,
  then return its path. Otherwise return the report in the conversation.

# Commit message format

## Decision

Use `type(scope): short action` followed by a blank line and 1–8 bullets for authored commits
and squash commit messages. The full rules and example live in [the agent guide](../../AGENTS.md#commit-messages).
Both app repositories carry the same rules so standalone clones retain the guidance.

## Reason

The user requested titles such as `feat(inbox): add avatars` and short bullet descriptions.
The type identifies the purpose, the scope identifies the affected area, and the body explains
the changes. Apply the unslop skill to keep the entire message concrete and concise.

## Review

Before committing, check the title's type, scope, and action. Check that the body contains
1–8 bullets describing the actual diff. Check any claims about validation against completed
checks. Review breaking changes for an impact statement and migration instructions.

## Automatic checks

The user requested documented guidance and an automatic commit check. A versioned `commit-msg`
hook invokes a Python checker. Each checkout enables it through the local `core.hooksPath`
setting; new clones must do the same. No extra Python package is needed for the hook.

The checker validates supported types, lowercase kebab-case scopes, a lowercase subject opening,
the blank separator, and 1–8 nonempty body bullets. It rejects a final period in the title.
It accepts indented bullet continuations and optional footers after a blank line. Git's
`stripspace` removes editor comments using the configured comment character before validation.

Clear wording, imperative verbs, accurate claims, breaking-change impact and migration details,
and the 72-character title target remain manual checks. The existing `checkAll` and portable CI
test discovery run checker unit tests and a Git hook test in a temporary repository. CI does
not inspect commit history. Local hooks can be bypassed and do not replace code review.

This policy does not rewrite existing history. Git-generated merge messages are exempt only
during a merge; temporary autosquash messages are exempt. Final squash messages follow the rules.

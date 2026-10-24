"""Validate Otus commit messages for the commit-msg hook and drop AI attribution lines."""

import argparse
import re
import subprocess
import sys
from pathlib import Path

TYPES = "feat|fix|refactor|perf|docs|test|build|ci|chore|revert"
TITLE = re.compile(rf"(?:{TYPES})\([a-z0-9]+(?:-[a-z0-9]+)*\)!?: ([a-z].*\S|[a-z])")
FOOTER = re.compile(r"(?:BREAKING CHANGE|[A-Za-z][A-Za-z-]*)(?:: | #)\S.*")
# Coding agents append these on their own; Otus commits credit only their human authors.
ATTRIBUTION = re.compile(
    r"\s*co-authored-by:.*\b(?:claude|anthropic|cursor)\b.*|.*generated with \[?claude code\b.*",
    re.IGNORECASE,
)


def strip_attribution(message: str) -> str:
    lines = message.splitlines(keepends=True)
    return "".join(line for line in lines if not ATTRIBUTION.fullmatch(line.rstrip("\r\n")))


def violations(message: str, *, allow_merge: bool = False) -> list[str]:
    lines = message.splitlines()
    if not lines:
        return ["The commit message is empty."]
    title = lines[0]
    if any(
        title.startswith(prefix) and title[len(prefix) :].strip()
        for prefix in ("fixup! ", "squash! ", "amend! ")
    ):
        return []
    if allow_merge and title.startswith("Merge ") and title[6:].strip():
        return []
    if not TITLE.fullmatch(title):
        return [
            "Use type(scope): short action, with a supported type and lowercase kebab-case scope."
        ]
    errors = []
    if title.endswith("."):
        errors.append("Omit the final period in the title.")
    if len(lines) < 2 or lines[1] != "":
        errors.append("Leave a blank line between the title and the body.")
    bullets = 0
    in_footer = False
    previous_blank = False
    for line in lines[2:]:
        if not line.strip():
            previous_blank = True
            continue
        if line.startswith("- ") and not in_footer:
            if not line[2:].strip():
                errors.append("Each bullet must describe a change.")
            bullets += 1
        elif FOOTER.fullmatch(line) and bullets and (previous_blank or in_footer):
            in_footer = True
        elif (
            line.startswith("  ")
            and bullets
            and not in_footer
            or in_footer
            and not line.startswith("- ")
        ):
            pass
        else:
            errors.append("Use '- ' body bullets; put optional footers after a blank line.")
        previous_blank = False
    if not 1 <= bullets <= 8:
        errors.append(f"Use 1–8 body bullets; found {bullets}.")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("message_file", type=Path)
    args = parser.parse_args()
    try:
        written = args.message_file.read_text(encoding="utf-8")
        message = strip_attribution(written)
        if message != written:
            args.message_file.write_text(message, encoding="utf-8")
        message = subprocess.run(
            ["git", "stripspace", "--strip-comments"],
            input=message,
            text=True,
            capture_output=True,
            check=True,
        ).stdout
        merge_path = subprocess.run(
            ["git", "rev-parse", "--git-path", "MERGE_HEAD"],
            text=True,
            capture_output=True,
            check=True,
        ).stdout.strip()
        errors = violations(message, allow_merge=Path(merge_path).is_file())
    except (OSError, UnicodeError, subprocess.CalledProcessError) as error:
        print(f"Cannot check the commit message: {error}", file=sys.stderr)
        return 1
    if not errors:
        return 0
    print("Commit message rejected:", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    print(
        "\nExample:\nfeat(inbox): add avatars\n\n"
        "- Show contact photos beside inbox senders.\n"
        "- Fall back to initials when a photo is unavailable.\n\n"
        "See AGENTS.md for the commit rules.",
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())

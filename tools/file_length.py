"""Physical source-line limits with explicit, bounded per-file exceptions."""

import re
from pathlib import Path

LIMITS = {".kt": 300, ".swift": 200}
DIRECTIVE = re.compile(r"// otus-file-length: ([1-9][0-9]*) -- (.+)")


def file_length_violations(path: Path, text: str) -> list[str]:
    default = LIMITS[path.suffix]
    limit = default
    lines = text.splitlines()
    errors = []
    directives = [
        (number, line.strip())
        for number, line in enumerate(lines, start=1)
        if re.match(r"\s*//\s*otus-file-length\b", line)
    ]
    if directives:
        if len(directives) != 1 or directives[0][0] != 1:
            errors.append("otus-file-length must appear exactly once, on the first line")
        else:
            match = DIRECTIVE.fullmatch(directives[0][1])
            if not match or len(match.group(2).strip()) < 20:
                errors.append(
                    "use '// otus-file-length: <limit> -- <reason of at least 20 characters>'"
                )
            elif len(match.group(1)) > 6:
                errors.append("otus-file-length limit must be at most six digits")
            elif int(match.group(1)) <= default:
                errors.append(f"otus-file-length exception must exceed the default {default}")
            elif len(lines) <= default:
                errors.append("remove the unnecessary otus-file-length exception")
            else:
                limit = int(match.group(1))
    if len(lines) > limit:
        errors.append(
            f"file has {len(lines)} lines; limit is {limit} (including comments and blanks). "
            "Split by responsibility; use a justified otus-file-length exception "
            "only as a last resort"
        )
    return errors

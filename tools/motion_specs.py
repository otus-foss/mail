"""Animations take their timing from Meta's Motion: no raw spec and no Compose default spec."""

import re

# `Motion.tween(...)` and `motionTween(...)` are Meta's and stay allowed.
RAW_SPEC = re.compile(r"(?<![\w.])(tween|spring|CubicBezierEasing)\s*(?:<[^()]*>)?\s*\(")
DEFAULTED = re.compile(
    r"(?<![\w])(animateScrollToPage|animateScrollBy|animate\w*AsState)\s*(?:<[^()]*>)?\s*\("
)
# How many positional arguments come before the spec; animate*AsState takes it second.
SPEC_POSITION = {"animateScrollToPage": 2, "animateScrollBy": 1}
NAMED = re.compile(r"\s*(\w+)\s*=(?!=)")
OPEN = "([{"
CLOSE = ")]}"


def motion_violations(code: str) -> list[str]:
    """Raw specs, and calls left on their default spring, in [code] without comments or strings."""
    errors = [
        f"animate through Meta's Motion, not {match.group(1)}(...)"
        for match in RAW_SPEC.finditer(code)
    ]
    for match in DEFAULTED.finditer(code):
        name = match.group(1)
        arguments = _arguments(code, match.end())
        if arguments is not None and not _has_spec(arguments, SPEC_POSITION.get(name, 1)):
            errors.append(f"give {name} an animationSpec from Meta's Motion")
    return errors


def _has_spec(arguments: list[str], position: int) -> bool:
    names = [NAMED.match(argument) for argument in arguments]
    if any(name and name.group(1) == "animationSpec" for name in names):
        return True
    return sum(1 for name in names if name is None) > position


def _arguments(code: str, start: int) -> list[str] | None:
    """The top-level arguments of the call whose `(` ends just before [start]; None if unclosed."""
    depth = 0
    arguments: list[str] = []
    current = start
    for i in range(start, len(code)):
        char = code[i]
        if char in OPEN:
            depth += 1
        elif char in CLOSE and depth > 0:
            depth -= 1
        elif char == ")":
            arguments.append(code[current:i])
            return [argument for argument in arguments if argument.strip()]
        elif char == "," and depth == 0:
            arguments.append(code[current:i])
            current = i + 1
    return None

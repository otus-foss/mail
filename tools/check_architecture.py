"""Fail on explicit Otus source-layout and dependency-policy violations."""

import re
import sys
from pathlib import Path

from file_length import file_length_violations
from motion_specs import motion_violations

ROOT = Path(__file__).resolve().parents[1]
SKIP = {"build", ".gradle", ".git", ".venv"}
LAYERS = {"app", "core", "designsystem", "feature"}
PLATFORM_APIS = ("android.", "java.", "javax.", "platform.")
DATA_APIS = ("io.ktor.", "app.cash.sqldelight.", "androidx.room.", "okhttp3.")


SUPPRESSION = re.compile(
    r"@(?:\w+:)?\[?\s*(?:[\w.]+\.)?Suppress(?:Lint|Warnings)?\b"
    r"|ktlint-disable|detekt:|swiftlint:disable|swiftformat:disable|tools:ignore\b"
)
DRAWABLE = re.compile(r"drawable(?:-[\w-]+)?")


def source_code(text: str) -> str:
    """Blank literals and comments, keeping `${...}` template code (not type resolution)."""
    out: list[str] = []
    _code(text, 0, out, template=False)
    return "".join(out)


def _blank(text: str) -> str:
    return re.sub(r"[^\n]", " ", text)


def _code(text: str, i: int, out: list[str], *, template: bool) -> int:
    """Copy code from [i]; inside a template, stop before its closing brace."""
    depth = 0
    while i < len(text):
        char = text[i]
        if text.startswith("//", i):
            end = text.find("\n", i)
            end = len(text) if end < 0 else end
        elif text.startswith("/*", i):
            end = text.find("*/", i + 2)
            end = len(text) if end < 0 else end + 2
        elif char == "'":
            literal = re.compile(r"'(?:\\.|[^'\\\n])*'").match(text, i)
            end = literal.end() if literal else i + 1
        elif char == '"':
            quote = '"""' if text.startswith('"""', i) else '"'
            out.append(" " * len(quote))
            i = _string(text, i + len(quote), out, quote)
            continue
        else:
            if char == "{":
                depth += 1
            elif char == "}":
                if template and depth == 0:
                    return i
                depth -= 1
            out.append(char)
            i += 1
            continue
        out.append(_blank(text[i:end]))
        i = end
    return i


def _string(text: str, i: int, out: list[str], quote: str) -> int:
    """Blank a string body after its opening [quote], keeping the code of `${...}` templates."""
    while i < len(text):
        if quote == '"' and text[i] == "\\":
            out.append(_blank(text[i : i + 2]))
            i += 2
        elif text.startswith(quote, i):
            while quote == '"""' and text.startswith('""""', i):
                out.append(" ")
                i += 1
            out.append(" " * len(quote))
            return i + len(quote)
        elif text.startswith("${", i):
            out.append("  ")
            i = _code(text, i + 2, out, template=True)
            if i < len(text):
                out.append(" ")
                i += 1
        else:
            out.append(_blank(text[i]))
            i += 1
    return i


def suppression_violations(text: str) -> list[str]:
    if SUPPRESSION.search(text):
        return ["inline lint suppression is prohibited; fix the violation"]
    return []


def drawable_violations(root: Path) -> list[str]:
    """Drawable resources in any source set of the shared or Android module belong in otus-meta."""
    return [
        f"{path.relative_to(root)}: visual drawable resources belong in otus-meta/designsystem"
        for folder in ("shared/src", "androidApp/src")
        for path in sorted((root / folder).rglob("*"))
        if path.is_file()
        and not set(path.relative_to(root).parts) & SKIP
        and DRAWABLE.fullmatch(path.parent.name)
    ]


def uses_api(code: str, apis: tuple[str, ...]) -> bool:
    """Whether [code] names any package in [apis], not as part of a longer name."""
    return any(re.search(r"(?<![\w.])" + re.escape(api), code) for api in apis)


def color_names(code: str) -> str:
    """`Color` and any name it is imported as, for spotting literal colors."""
    aliases = re.findall(r"^import\s+[\w.]*\.Color\s+as\s+(\w+)", code, re.MULTILINE)
    return "|".join(["Color", *aliases])


def violations(path: Path, text: str, base: str, *, central_ui: bool = False) -> list[str]:
    errors = file_length_violations(path, text) + suppression_violations(text)
    code = source_code(text)
    parts = path.parts
    production = any(part.endswith("Main") or part == "main" for part in parts)
    if path.suffix != ".kt":
        return errors
    package = re.search(r"^package\s+([\w.]+)", code, re.MULTILINE)
    if not package:
        return [*errors, "Kotlin file must declare its package"]
    if "kotlin" not in parts:
        return [*errors, "Kotlin source must live under a kotlin source directory"]
    expected = ".".join(parts[parts.index("kotlin") + 1 : -1])
    if package.group(1) != expected or not expected.startswith(base + ".") and expected != base:
        errors.append("package must match source path and app namespace")
    imports = re.findall(r"^import\s+([\w.*]+)", code, re.MULTILINE)
    if any(name.endswith(".*") for name in imports):
        errors.append("wildcard imports are prohibited")
    if not production:
        return errors
    errors += motion_violations(code)
    suffix = package.group(1).removeprefix(base).strip(".")
    location = suffix.split(".") if suffix else []
    if "shared" in parts and "commonMain" in parts:
        if not location or location[0] not in LAYERS:
            errors.append("common production code belongs in app/core/designsystem/feature")
        if uses_api(code, PLATFORM_APIS):
            errors.append("platform-only API in commonMain")
    if any(part in {"utils", "helpers", "manager"} for part in location):
        errors.append("use responsibility-specific packages instead of catch-all packages")
    if location[:1] == ["feature"] and (
        len(location) < 3 or location[2] not in {"domain", "data", "presentation"}
    ):
        errors.append("feature code must live in domain/data/presentation")
    if location[:1] == ["designsystem"] and (
        len(location) < 2 or location[1] not in {"elements", "components", "theme"}
    ):
        errors.append("designsystem code belongs in elements/components/theme")
    if central_ui and location[:1] == ["designsystem"]:
        errors.append("visual elements, components, and tokens belong in otus-meta")
    # Includes fully qualified code references as well as imported types.
    references = re.findall(r"\b" + re.escape(base) + r"\.([A-Za-z_][\w.]*)", code)
    for reference in references:
        if reference == suffix:
            continue
        target = reference.split(".")
        if location[:1] == ["core"] and target[0] in {"app", "feature", "designsystem"}:
            errors.append("core cannot depend on app/features/designsystem")
        if location[:1] == ["designsystem"] and target[0] in {"app", "feature", "core"}:
            errors.append("designsystem cannot depend on app/features/core")
        if location[:2] == ["designsystem", "elements"] and target[:2] == [
            "designsystem",
            "components",
        ]:
            errors.append("elements cannot depend on components")
        if "components" in location and "screens" in target:
            errors.append("components cannot depend on screens")
        if location[:1] == ["feature"]:
            if target[0] == "app":
                errors.append("features cannot depend on app wiring")
            if target[0] == "feature" and len(target) > 1 and target[1] != location[1]:
                errors.append("features cannot import other features directly")
            if "domain" in location and any(
                x in target for x in ("data", "presentation", "designsystem")
            ):
                errors.append("domain cannot depend on data/presentation/designsystem")
            if "presentation" in location and "data" in target:
                errors.append("presentation cannot depend on data implementations")
            if "data" in location and any(x in target for x in ("presentation", "designsystem")):
                errors.append("data cannot depend on presentation/designsystem")
    if "domain" in location:
        forbidden = (
            *PLATFORM_APIS,
            *DATA_APIS,
            "androidx.compose.",
            "androidx.lifecycle.",
            "org.jetbrains.compose.",
        )
        if uses_api(code, forbidden):
            errors.append("domain contains UI/platform/network/database dependencies")
    ui = "presentation" in location or location[:1] == ["designsystem"]
    if ui and uses_api(code, DATA_APIS):
        errors.append("UI must not access network or database APIs")
    if ("elements" in location or "components" in location) and re.search(
        r"\b\w*(?:ViewModel|Repository|NavController)\b|\b(?:viewModel|koinInject|hiltViewModel)\s*[(<]",
        code,
    ):
        errors.append("elements/components take values and callbacks, not services or navigation")
    if "screens" in location and not path.stem.endswith("Screen"):
        errors.append("screen files must end in Screen.kt")
    if re.search(r"@(?:androidx\.compose\.runtime\.)?Composable\b", code):
        allowed = location[:1] in (["app"], ["designsystem"]) or (
            "presentation" in location and any(x in location for x in ("screens", "components"))
        )
        if not allowed:
            errors.append(
                "composables belong in app/designsystem/presentation screens or components"
            )
    names = color_names(code)
    if re.search(
        rf"(?<![\w.])(?:androidx\.compose\.ui\.graphics\.)?(?:{names})"
        r"(?:\s*\(\s*(?:\w+\s*=\s*)?(?:0[xX]|\d|\.\d)|\.(?:hsv|hsl)\s*\()",
        code,
    ):
        errors.append("literal colors belong in otus-meta designsystem/theme")
    return sorted(set(errors))


def main() -> int:
    settings = (ROOT / "settings.gradle.kts").read_text()
    app = re.search(r'rootProject.name\s*=\s*"otus-(mail|calendar)"', settings)
    if not app:
        print("Cannot determine Otus application namespace", file=sys.stderr)
        return 1
    failures = drawable_violations(ROOT) if app.group(1) == "mail" else []
    for folder in ("shared/src", "androidApp/src"):
        for path in sorted((ROOT / folder).rglob("*.xml")):
            if not set(path.parts) & SKIP:
                errors = suppression_violations(path.read_text())
                failures += [f"{path.relative_to(ROOT)}: {error}" for error in errors]
    for folder in ("shared/src", "androidApp/src", "iosApp"):
        for path in sorted((ROOT / folder).rglob("*")):
            if path.suffix not in {".kt", ".swift"} or set(path.parts) & SKIP:
                continue
            relative = path.relative_to(ROOT)
            for error in violations(
                relative,
                path.read_text(),
                f"com.otus.{app.group(1)}",
                central_ui=app.group(1) == "mail",
            ):
                failures.append(f"{relative}: {error}")
    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1
    print("Architecture checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())

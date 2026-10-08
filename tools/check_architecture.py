"""Fail on explicit Otus source-layout and dependency-policy violations."""

import re
import sys
from pathlib import Path

from file_length import file_length_violations

ROOT = Path(__file__).resolve().parents[1]
SKIP = {"build", ".gradle", ".git", ".venv"}
LAYERS = {"app", "core", "designsystem", "feature"}
PLATFORM_APIS = ("android.", "java.", "javax.", "platform.")
DATA_APIS = ("io.ktor.", "app.cash.sqldelight.", "androidx.room.", "okhttp3.")


def source_code(text: str) -> str:
    """Remove literals/comments before checking code references (not type resolution)."""
    return re.sub(
        r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*[\s\S]*?\*/|//[^\n]*',
        lambda match: "\n" * match.group().count("\n"),
        text,
    )


def violations(path: Path, text: str, base: str) -> list[str]:
    errors = file_length_violations(path, text)
    code = source_code(text)
    parts = path.parts
    production = any(part.endswith("Main") or part == "main" for part in parts)
    if re.search(
        r"@(?:file:)?Suppress\b|ktlint-disable|detekt:|swiftlint:disable|swiftformat:disable", text
    ):
        errors.append("inline lint suppression is prohibited; fix the violation")
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
    suffix = package.group(1).removeprefix(base).strip(".")
    location = suffix.split(".") if suffix else []
    if "shared" in parts and "commonMain" in parts:
        if not location or location[0] not in LAYERS:
            errors.append("common production code belongs in app/core/designsystem/feature")
        if any(re.search(r"(?<![\w.])" + re.escape(api), code) for api in PLATFORM_APIS):
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
    # Includes fully qualified code references as well as imported types.
    references = re.findall(r"\b" + re.escape(base) + r"\.([A-Za-z_][\w.]*)", code)
    current_package = suffix
    for reference in references:
        if reference == current_package:
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
        forbidden = (*PLATFORM_APIS, *DATA_APIS, "androidx.compose.", "androidx.lifecycle.")
        if any(re.search(r"(?<![\w.])" + re.escape(api), code) for api in forbidden):
            errors.append("domain contains UI/platform/network/database dependencies")
    if ("presentation" in location or location[:1] == ["designsystem"]) and any(
        re.search(r"(?<![\w.])" + re.escape(api), code) for api in DATA_APIS
    ):
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
    if "theme" not in location and re.search(r"\bColor\s*\(\s*0[xX]", code):
        errors.append("literal colors belong in designsystem/theme")
    return sorted(set(errors))


def main() -> int:
    settings = (ROOT / "settings.gradle.kts").read_text()
    app = re.search(r'rootProject.name\s*=\s*"otus-(mail|calendar)"', settings)
    if not app:
        print("Cannot determine Otus application namespace", file=sys.stderr)
        return 1
    failures = []
    for folder in ("shared/src", "androidApp/src", "iosApp"):
        for path in sorted((ROOT / folder).rglob("*")):
            if path.suffix not in {".kt", ".swift"} or set(path.parts) & SKIP:
                continue
            relative = path.relative_to(ROOT)
            for error in violations(relative, path.read_text(), f"com.otus.{app.group(1)}"):
                failures.append(f"{relative}: {error}")
    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1
    print("Architecture checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())

"""Summarize a Kover (JaCoCo-format) XML report as Markdown for a pull request comment.

Usage: coverage_report.py REPORT.xml [--root DIR] [--base REF]

Only handwritten main Kotlin sources count: a class or line counts when its source file is in
the repository outside build output and test source sets. Generated code, such as SQLDelight
queries and Compose resources, is left out.
"""

import argparse
import re
import subprocess
import sys
import xml.etree.ElementTree as ElementTree
from dataclasses import dataclass, field
from pathlib import Path

COUNTER_LABELS = {
    "LINE": "lines",
    "BRANCH": "branches",
    "INSTRUCTION": "instructions",
    "METHOD": "methods",
    "CLASS": "classes",
}
COUNTERS = list(COUNTER_LABELS)
SKIPPED_DIRS = {"build", ".git", ".gradle", ".venv", "node_modules"}
SOURCE_SET = re.compile(r"(?:^|/)src/([^/]+)/kotlin/")
PACKAGE = re.compile(r"^package\s+([\w.]+)", re.MULTILINE)
JVM_NAME = re.compile(r'^@file:JvmName\("([^"]+)"\)', re.MULTILINE)
COMPOSABLE = re.compile(
    r"^(?P<indent>[ \t]*)@Composable\b(?P<between>(?:[^\n]*\n)*?)"
    r"[ \t]*(?P<modifiers>(?:[a-z]+ )*)fun (?:<[^>]+> )?(?:[\w.<>, ?]+\.)?(?P<name>[A-Z]\w*)\(",
    re.MULTILINE,
)
UI_KINDS = [
    ("screens", "Screens", re.compile(r"/presentation/screens/")),
    ("components", "Presentation components", re.compile(r"/presentation/components/")),
    ("elements", "Design-system elements", re.compile(r"/designsystem/[^/]+/[^/]+/elements/")),
    (
        "ds-components",
        "Design-system components",
        re.compile(r"/designsystem/[^/]+/[^/]+/components/"),
    ),
]
# Each pattern labels the files it matches; {module} is the Gradle module. The first match wins.
LAYERS = [
    (re.compile(r"/feature/([^/]+)/presentation/(screens|components)/"), "{0} · presentation/{1}"),
    (re.compile(r"/feature/([^/]+)/(domain|data|presentation)/"), "{0} · {1}"),
    (re.compile(r"/designsystem/[^/]+/[^/]+/(elements|components)/"), "designsystem · {0}"),
    (re.compile(r"/com/otus/mail/(app|core)/"), "{0}"),
    (re.compile(r"/presentation/(screens|components)/"), "{module} · presentation/{0}"),
    (re.compile(r"/(domain|data|presentation)/"), "{module} · {0}"),
]
LEAST_COVERED = 15
# A share at or above a limit gets its dot, so readers see weak spots before reading numbers.
RATINGS = [(80, "🟢"), (50, "🟡"), (0, "🔴")]


@dataclass
class Counter:
    missed: int = 0
    covered: int = 0

    def add(self, missed: int, covered: int) -> None:
        self.missed += missed
        self.covered += covered

    @property
    def total(self) -> int:
        return self.missed + self.covered


@dataclass
class SourceFile:
    path: str
    lines: dict[int, tuple[int, int, int, int]] = field(default_factory=dict)
    methods: dict[str, Counter] = field(default_factory=dict)
    counters: dict[str, Counter] = field(default_factory=lambda: {k: Counter() for k in COUNTERS})


@dataclass
class Composable:
    name: str
    path: str
    first: int
    last: int
    public: bool
    owner: str | None
    rendered: bool | None = None


def percent(counter: Counter) -> str:
    return f"{100 * counter.covered / counter.total:.1f}%" if counter.total else "–"


def dot(counter: Counter) -> str:
    share = 100 * counter.covered / counter.total if counter.total else 0
    return next(mark for limit, mark in RATINGS if share >= limit)


def rated(counter: Counter) -> str:
    return f"{dot(counter)} {percent(counter)}" if counter.total else "–"


def is_main_source(path: str) -> bool:
    match = SOURCE_SET.search(path)
    return bool(match) and "test" not in match.group(1).lower() and "/build/" not in f"/{path}"


def kotlin_sources(root: Path) -> dict[str, str]:
    """Each main Kotlin source file's text by its path relative to the root."""
    found = {}
    for path in root.rglob("*.kt"):
        relative = path.relative_to(root)
        if SKIPPED_DIRS.isdisjoint(relative.parts) and is_main_source(relative.as_posix()):
            found[relative.as_posix()] = path.read_text(encoding="utf-8")
    return found


def source_key(path: str, text: str) -> tuple[str, str]:
    package = PACKAGE.search(text)
    return (package.group(1).replace(".", "/") if package else "", Path(path).name)


def parse_report(xml_path: Path, sources: dict[str, str]) -> dict[str, SourceFile]:
    """The report's per-file data, keyed by repository path, for handwritten sources only."""
    paths = {source_key(path, text): path for path, text in sources.items()}
    files: dict[str, SourceFile] = {}
    for package in ElementTree.parse(xml_path).getroot().iter("package"):
        directory = package.get("name", "")
        for element in package.findall("sourcefile"):
            path = paths.get((directory, element.get("name", "")))
            if path is None:
                continue
            source = files.setdefault(path, SourceFile(path))
            for line in element.findall("line"):
                numbers = tuple(int(line.get(key, "0")) for key in ("mi", "ci", "mb", "cb"))
                source.lines[int(line.get("nr", "0"))] = numbers
        for element in package.findall("class"):
            path = paths.get((directory, element.get("sourcefilename", "")))
            if path is not None:
                add_class(files.setdefault(path, SourceFile(path)), element)
    return files


def add_class(source: SourceFile, element: ElementTree.Element) -> None:
    owner = element.get("name", "")
    for counter in element.findall("counter"):
        kind = counter.get("type", "")
        if kind in source.counters:
            source.counters[kind].add(int(counter.get("missed", 0)), int(counter.get("covered", 0)))
    # Kover leaves out the class counter that JaCoCo writes; a class counts once any code in it ran.
    if element.find("counter[@type='CLASS']") is None:
        ran = element.find("counter[@type='INSTRUCTION']")
        covered = int(ran is not None and int(ran.get("covered", 0)) > 0)
        source.counters["CLASS"].add(1 - covered, covered)
    for method in element.findall("method"):
        instructions = method.find("counter[@type='INSTRUCTION']")
        if instructions is None:
            continue
        key = f"{owner}#{method.get('name')}"
        counter = source.methods.setdefault(key, Counter())
        counter.add(int(instructions.get("missed", 0)), int(instructions.get("covered", 0)))


def composables(path: str, text: str) -> list[Composable]:
    """The named @Composable functions in a file, skipping previews and lambdas."""
    lines = text.splitlines()
    package, _ = source_key(path, text)
    jvm_name = JVM_NAME.search(text)
    file_class = jvm_name.group(1) if jvm_name else Path(path).stem + "Kt"
    found = []
    for match in COMPOSABLE.finditer(text):
        first = text.count("\n", 0, match.start("name")) + 1
        if match.group("between").count("\n") > 3 or is_preview(lines, match, first):
            continue
        top_level = match.group("indent") == ""
        found.append(
            Composable(
                name=match.group("name"),
                path=path,
                first=first,
                last=function_end(lines, first - 1, len(match.group("indent"))),
                public="private" not in match.group("modifiers"),
                owner=f"{package}/{file_class}" if top_level else None,
            )
        )
    return found


def is_preview(lines: list[str], match: re.Match[str], first: int) -> bool:
    """Whether the function's annotations, above or below @Composable, include @Preview."""
    above = []
    for line in reversed(lines[: first - 1 - match.group("between").count("\n")]):
        if not line.strip().startswith("@"):
            break
        above.append(line)
    return any("@Preview" in line for line in [*above, match.group("between")])


def function_end(lines: list[str], start: int, indent: int) -> int:
    """The last line of the function starting at a zero-based index, read from indentation."""
    for index in range(start + 1, len(lines)):
        line = lines[index]
        stripped = line.strip()
        if not stripped or len(line) - len(line.lstrip()) > indent or stripped.startswith(")"):
            continue
        return index + 1 if stripped.startswith("}") else index
    return len(lines)


def method_matches(name: str, method: str) -> bool:
    """Whether a JVM method compiles from a Kotlin function, allowing for name mangling."""
    return re.fullmatch(rf"{re.escape(name)}(-[\w-]+)?(\$default)?", method) is not None


def mark_rendered(items: list[Composable], files: dict[str, SourceFile]) -> None:
    """Sets `rendered` from the matching methods, leaving it None when no method matches."""
    for item in items:
        source = files.get(item.path)
        if source is None:
            continue
        counters = [
            counter
            for key, counter in source.methods.items()
            if (item.owner is None or key.split("#")[0] == item.owner)
            and method_matches(item.name, key.split("#")[1])
        ]
        if counters:
            item.rendered = any(counter.covered for counter in counters)


def changed_lines(root: Path, base: str) -> dict[str, set[int]]:
    """Added or changed lines per main Kotlin source since the merge base with `base`."""
    diff = subprocess.run(
        ["git", "diff", "-U0", "--no-color", "--no-ext-diff", f"{base}...HEAD", "--", "*.kt"],
        cwd=root,
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    return parse_diff(diff)


def parse_diff(diff: str) -> dict[str, set[int]]:
    changed: dict[str, set[int]] = {}
    current: set[int] | None = None
    for line in diff.splitlines():
        if line.startswith("+++ "):
            path = line[4:].removeprefix("b/")
            current = changed.setdefault(path, set()) if is_main_source(path) else None
        elif line.startswith("@@") and current is not None:
            match = re.match(r"@@ -\S+ \+(\d+)(?:,(\d+))? @@", line)
            if match:
                start, count = int(match.group(1)), int(match.group(2) or "1")
                current.update(range(start, start + count))
    return {path: lines for path, lines in changed.items() if lines}


def line_ranges(lines: list[int], instrumented: set[int]) -> str:
    """Joins uncovered lines into ranges that skip lines without bytecode."""
    ranges: list[list[int]] = []
    for line in sorted(lines):
        if ranges and instrumented.isdisjoint(range(ranges[-1][1] + 1, line)):
            ranges[-1][1] = line
        else:
            ranges.append([line, line])
    return ", ".join(str(a) if a == b else f"{a}–{b}" for a, b in ranges)


def layer(path: str) -> str:
    module = path.split("/src/")[0]
    for pattern, label in LAYERS:
        match = pattern.search(path)
        if match:
            return label.format(*match.groups(), module=module)
    return module


def table(header: list[str], rows: list[list[str]]) -> list[str]:
    lines = ["| " + " | ".join(header) + " |", "|" + "---|" * len(header)]
    return lines + ["| " + " | ".join(row) + " |" for row in rows]


def details(summary: str, body: list[str]) -> list[str]:
    return ["<details>", f"<summary>{summary}</summary>", "", *body, "", "</details>", ""]


def totals_section(files: dict[str, SourceFile]) -> list[str]:
    rows = []
    for kind in COUNTERS:
        total = Counter()
        for source in files.values():
            total.add(source.counters[kind].missed, source.counters[kind].covered)
        rows.append([COUNTER_LABELS[kind], str(total.covered), str(total.total), rated(total)])
    return table(["", "Covered", "Total", "%"], rows) + [""]


def cells(counter: Counter) -> list[str]:
    return [str(counter.covered), str(counter.total), percent(counter)]


def grouped_section(title: str, files: dict[str, SourceFile], group) -> list[str]:
    groups: dict[str, Counter] = {}
    for source in files.values():
        line = source.counters["LINE"]
        groups.setdefault(group(source.path), Counter()).add(line.missed, line.covered)
    rows = [[name, *cells(counter)] for name, counter in sorted(groups.items())]
    return details(title, table(["", "Lines covered", "Lines", "%"], rows))


def least_covered_section(files: dict[str, SourceFile]) -> list[str]:
    ranked = sorted(files.values(), key=lambda s: (-s.counters["LINE"].missed, s.path))
    rows = [
        [f"`{s.path}`", str(s.counters["LINE"].missed), percent(s.counters["LINE"])]
        for s in ranked[:LEAST_COVERED]
        if s.counters["LINE"].missed
    ]
    title = f"Least-covered files ({len(rows)})"
    return details(title, table(["File", "Uncovered lines", "Line %"], rows))


def ui_section(items: list[Composable]) -> list[str]:
    out = ["### UI", ""]
    unrendered_screens = []
    for kind, label, pattern in UI_KINDS:
        group = [item for item in items if item.public and pattern.search(item.path)]
        if not group:
            continue
        rendered = sum(1 for item in group if item.rendered)
        unmatched = sum(1 for item in group if item.rendered is None)
        note = f" ({unmatched} not found in the bytecode)" if unmatched else ""
        mark = dot(Counter(len(group) - rendered, rendered))
        out.append(f"- {mark} {label} rendered by a test: {rendered} of {len(group)}{note}")
        if kind == "screens":
            unrendered_screens = [item for item in group if item.rendered is False]
    out.append("")
    if unrendered_screens:
        names = [f"- `{item.name}` in `{item.path}`" for item in unrendered_screens]
        out += details(f"Screens with no coverage ({len(names)})", names)
    return out


def pull_request_section(
    files: dict[str, SourceFile], changed: dict[str, set[int]], items: list[Composable]
) -> list[str]:
    out = ["### This pull request", ""]
    lines_total, branches_total, rows = Counter(), Counter(), []
    for path in sorted(changed):
        source = files.get(path)
        data = source.lines if source else {}
        instrumented = {nr for nr, (mi, ci, _, _) in data.items() if mi + ci}
        touched = sorted(changed[path] & instrumented)
        if not touched:
            continue
        covered = [nr for nr in touched if data[nr][1]]
        uncovered = [nr for nr in touched if not data[nr][1]]
        file_lines = Counter(len(uncovered), len(covered))
        lines_total.add(file_lines.missed, file_lines.covered)
        for nr in touched:
            branches_total.add(data[nr][2], data[nr][3])
        ranges = line_ranges(uncovered, instrumented) or "–"
        rows.append([f"`{path}`", f"{len(covered)} of {len(touched)}", rated(file_lines), ranges])
    if not rows:
        return out + ["No changed line in main Kotlin sources has bytecode to cover.", ""]
    out.append(
        f"{dot(lines_total)} Changed lines covered: {lines_total.covered} of {lines_total.total} "
        f"({percent(lines_total)}) · changed branches covered: {branches_total.covered} of "
        f"{branches_total.total} ({percent(branches_total)})"
    )
    out.append("")
    header = ["File", "Changed lines covered", "%", "Uncovered changed lines"]
    out += details(f"Touched files ({len(rows)})", table(header, rows))
    unrendered = [
        item
        for item in items
        if item.rendered is False
        and changed.get(item.path, set()) & set(range(item.first, item.last + 1))
    ]
    if unrendered:
        out.append("Touched composables that no test renders:")
        out += [f"- `{item.name}` in `{item.path}`" for item in unrendered]
        out.append("")
    return out


def report(root: Path, xml_path: Path, base: str | None) -> str:
    sources = kotlin_sources(root)
    files = parse_report(xml_path, sources)
    items = [item for path, text in sorted(sources.items()) for item in composables(path, text)]
    mark_rendered(items, files)
    out = ["## Coverage", ""]
    out += totals_section(files)
    if base:
        out += pull_request_section(files, changed_lines(root, base), items)
    out += ui_section(items)
    out += grouped_section("By module", files, lambda path: path.split("/src/")[0])
    out += grouped_section("By layer", files, layer)
    out += least_covered_section(files)
    return "\n".join(out).rstrip() + "\n"


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("xml", type=Path, help="Kover XML report")
    parser.add_argument("--root", type=Path, default=Path.cwd(), help="repository root")
    parser.add_argument("--base", help="base ref; adds the pull request section")
    args = parser.parse_args(argv)
    sys.stdout.write(report(args.root.resolve(), args.xml, args.base))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

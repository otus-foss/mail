"""Check the coverage summary against a small Kover report and a throwaway repository."""

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import coverage_report

REPORT = Path(__file__).resolve().parent / "fixtures/coverage_report.xml"
MAIN = "shared/src/commonMain/kotlin/com/otus/mail"
SCREEN = f"{MAIN}/feature/inbox/presentation/screens/InboxScreen.kt"
COUNT = f"{MAIN}/feature/inbox/domain/Count.kt"
ACTIVITY = "androidApp/src/main/kotlin/com/otus/mail/app/MainActivity.kt"
TEST = "shared/src/commonTest/kotlin/com/otus/mail/feature/inbox/domain/CountTest.kt"
SOURCES = {
    SCREEN: """package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.runtime.Composable

@Composable
fun InboxScreen(count: Int) {
    Text("$count")
}

@Composable
fun EmptyScreen(color: Color = Color.Red) {
    Text("empty")
}

@Preview
@Composable
private fun InboxScreenPreview() {
    InboxScreen(1)
}
""",
    COUNT: """package com.otus.mail.feature.inbox.domain

fun count(a: Int) = if (a > 0) a else 0
""",
    ACTIVITY: """package com.otus.mail.app

class MainActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContent { App() }
    }
}
""",
    TEST: """package com.otus.mail.feature.inbox.domain

class CountTest
""",
}


class CoverageReportTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name)
        self.write(SOURCES)

    def tearDown(self):
        self.directory.cleanup()

    def write(self, sources):
        for path, text in sources.items():
            (self.root / path).parent.mkdir(parents=True, exist_ok=True)
            (self.root / path).write_text(text)

    def git(self, *args):
        subprocess.run(["git", *args], cwd=self.root, check=True, capture_output=True)

    def commit_change(self, base_sources):
        """Commits base_sources, then the fixture sources on top, and returns the base commit."""
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.email", "test@example.com")
        self.git("config", "user.name", "Test")
        self.write(base_sources)
        self.git("add", ".")
        self.git("commit", "-q", "-m", "base")
        self.write(SOURCES)
        self.git("commit", "-q", "-am", "change")
        return "HEAD~1"

    def report(self, base=None):
        return coverage_report.report(self.root, REPORT, base)

    def test_totals_count_only_handwritten_sources(self):
        text = self.report()
        self.assertIn("| lines | 3 | 9 | 🔴 33.3% |", text)
        self.assertIn("| branches | 1 | 2 | 🟡 50.0% |", text)
        self.assertIn("| instructions | 10 | 30 | 🔴 33.3% |", text)
        self.assertIn("| methods | 3 | 7 | 🔴 42.9% |", text)
        self.assertIn("| classes | 2 | 3 | 🟡 66.7% |", text)
        self.assertNotIn("MailDatabaseImpl", text)

    def test_ui_counts_rendered_screens_and_skips_previews(self):
        text = self.report()
        self.assertIn("- 🟡 Screens rendered by a test: 1 of 2", text)
        self.assertIn(f"- `EmptyScreen` in `{SCREEN}`", text)
        self.assertNotIn("InboxScreenPreview", text)
        self.assertNotIn("This pull request", text)

    def test_layers_and_modules(self):
        text = self.report()
        self.assertIn("| inbox · presentation/screens | 2 | 6 | 33.3% |", text)
        self.assertIn("| inbox · domain | 1 | 1 | 100.0% |", text)
        self.assertIn("| app | 0 | 2 | 0.0% |", text)
        self.assertIn("| androidApp | 0 | 2 | 0.0% |", text)
        self.assertIn("| shared | 3 | 7 | 42.9% |", text)

    def test_layers_fall_back_to_the_module(self):
        meta = "designsystem/src/commonMain/kotlin/com/otus/designsystem"
        self.assertEqual(
            "designsystem · elements", coverage_report.layer(f"{meta}/shared/rows/elements/Row.kt")
        )
        self.assertEqual("designsystem", coverage_report.layer(f"{meta}/theme/OtusColors.kt"))
        settings = "settings/src/commonMain/kotlin/com/otus/settings/presentation/screens/Screen.kt"
        self.assertEqual("settings · presentation/screens", coverage_report.layer(settings))
        self.assertEqual(
            "jmap", coverage_report.layer("jmap/src/commonMain/kotlin/com/otus/jmap/Session.kt")
        )

    def test_least_covered_files_rank_by_uncovered_lines(self):
        text = self.report()
        least = text[text.index("Least-covered files") :]
        self.assertLess(least.index(SCREEN), least.index(ACTIVITY))
        self.assertNotIn(COUNT, least)

    def test_pull_request_reports_changed_lines_and_unrendered_composables(self):
        base = self.commit_change(
            {
                SCREEN: SOURCES[SCREEN].replace('"empty"', '"old"'),
                COUNT: SOURCES[COUNT].replace("a else 0", "a else 1"),
                TEST: SOURCES[TEST] + "// old\n",
            }
        )
        text = self.report(base)
        self.assertIn(
            "🟡 Changed lines covered: 1 of 2 (50.0%) · changed branches covered: 1 of 2 (50.0%)",
            text,
        )
        self.assertIn(f"| `{SCREEN}` | 0 of 1 | 🔴 0.0% | 12 |", text)
        self.assertIn(f"| `{COUNT}` | 1 of 1 | 🟢 100.0% | – |", text)
        self.assertNotIn(TEST, text)
        touched = text[text.index("Touched composables") : text.index("### UI")]
        self.assertIn("`EmptyScreen`", touched)
        self.assertNotIn("`InboxScreen`", touched)

    def test_pull_request_without_instrumented_changes(self):
        base = self.commit_change(
            {
                SCREEN: SOURCES[SCREEN].replace(
                    "\n\n@Composable\nfun E", "\n// x\n@Composable\nfun E"
                )
            }
        )
        self.assertIn("No changed line in main Kotlin sources has bytecode", self.report(base))

    def test_parse_diff_keeps_added_lines_of_main_sources(self):
        diff = "\n".join(
            [
                f"+++ b/{SCREEN}",
                "@@ -3,0 +4,2 @@",
                "@@ -9 +11 @@",
                "@@ -20,2 +22,0 @@",
                f"+++ b/{TEST}",
                "@@ -1 +1 @@",
            ]
        )
        self.assertEqual({SCREEN: {4, 5, 11}}, coverage_report.parse_diff(diff))

    def test_line_ranges_skip_lines_without_bytecode(self):
        ranges = coverage_report.line_ranges([3, 4, 7, 12], instrumented={3, 4, 7, 9, 12})
        self.assertEqual("3–7, 12", ranges)

    def test_method_names_allow_mangling_but_not_lambdas(self):
        self.assertTrue(coverage_report.method_matches("Row", "Row"))
        self.assertTrue(coverage_report.method_matches("Row", "Row-8_81llA$default"))
        self.assertFalse(coverage_report.method_matches("Row", "Row$lambda$0"))
        self.assertFalse(coverage_report.method_matches("Row", "RowItem"))


if __name__ == "__main__":
    unittest.main()

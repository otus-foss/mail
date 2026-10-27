"""Check the release-note filter used by the nightly workflow."""

import shlex
import subprocess
import unittest
from pathlib import Path

WORKFLOW = Path(__file__).resolve().parents[2] / ".github/workflows/nightly.yml"


class NightlyNotesTests(unittest.TestCase):
    def filter_notes(self, lines):
        step = next(line.strip() for line in WORKFLOW.read_text().splitlines() if "| sed " in line)
        command = step.split("|", 1)[1].split(" > notes.md", 1)[0]
        return subprocess.check_output(
            shlex.split(command), input="\n".join(lines) + "\n", text=True
        ).splitlines()

    def test_keeps_features_fixes_and_performance_changes(self):
        notes = [
            "- feat(inbox): add filters (#177)",
            "- fix: close the sheet",
            "- perf(design-system): draw less",
            "- feat(accounts)!: change account setup",
            "- fix!: change defaults",
        ]
        self.assertEqual(notes, self.filter_notes(notes))

    def test_omits_internal_changes_and_untyped_commits(self):
        notes = [
            f"- {kind}(inbox): mention feat(inbox): in internal work"
            for kind in ("refactor", "docs", "test", "build", "ci", "chore", "revert")
        ] + ["- Merge pull request #10", "- update documentation", "- feature(inbox): add filters"]
        self.assertEqual([], self.filter_notes(notes))

    def test_mixed_notes_keep_only_user_facing_changes(self):
        self.assertEqual(
            ["- fix(mail): open messages", "- feat(avatar): show photos"],
            self.filter_notes(
                [
                    "- docs(mail): describe messages",
                    "- fix(mail): open messages",
                    "- refactor(avatar): split resolver",
                    "- feat(avatar): show photos",
                ]
            ),
        )


if __name__ == "__main__":
    unittest.main()

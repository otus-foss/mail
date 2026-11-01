"""Commit format boundaries and rejection through an installed Git hook."""

import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_commit_message import violations

VALID = "feat(inbox): add avatars\n\n- Show sender photos.\n"
ROOT = Path(__file__).resolve().parents[2]


class CommitMessageTests(unittest.TestCase):
    def test_types_and_open_scopes(self):
        for kind in [
            "feat",
            "fix",
            "refactor",
            "perf",
            "docs",
            "test",
            "build",
            "ci",
            "chore",
            "revert",
        ]:
            with self.subTest(kind=kind):
                self.assertFalse(violations(f"{kind}(new-area): add a check\n\n- Add a check.\n"))

    def test_bullet_boundaries(self):
        for count in (0, 1, 8, 9):
            with self.subTest(count=count):
                message = "feat(inbox): add avatars\n\n" + "- Show a photo.\n" * count
                self.assertEqual(bool(violations(message)), count not in (1, 8))

    def test_invalid_titles(self):
        for title in (
            "add avatars",
            "feature(inbox): add avatars",
            "feat: add avatars",
            "Feat(inbox): add avatars",
            "feat(Inbox): add avatars",
            "feat(inbox_ui): add avatars",
            "feat(inbox): Add avatars",
            "feat(inbox): ",
            "feat(inbox):  add avatars",
            "feat(inbox): add avatars.",
        ):
            with self.subTest(title=title):
                self.assertTrue(violations(title + "\n\n- Show a photo.\n"))

    def test_missing_body_and_separator(self):
        for message in (
            "",
            "feat(inbox): add avatars",
            "feat(inbox): add avatars\n- Show a photo.",
        ):
            with self.subTest(message=message):
                self.assertTrue(violations(message))

    def test_body_must_contain_nonempty_bullets(self):
        for body in ("Show photos.", "* Show photos.", "- ", "- Show photos.\nExtra prose."):
            with self.subTest(body=body):
                self.assertTrue(violations("feat(inbox): add avatars\n\n" + body))

    def test_wrapped_bullets_and_crlf(self):
        message = VALID + "  when a photo is available.\n\n- Fall back to initials.\n"
        self.assertFalse(violations(message.replace("\n", "\r\n")))

    def test_footers_do_not_count_as_bullets(self):
        message = VALID.replace("feat(inbox)", "feat(inbox)!")
        self.assertFalse(
            violations(
                message + "\nBREAKING CHANGE: Replace the avatar API.\n"
                "Call resolve instead of load.\n\nRefs: #123\nCloses #456\n"
            )
        )
        self.assertTrue(violations(VALID + "Refs: #123\n"))
        self.assertTrue(violations(VALID + "\nRefs: #123\n- A misplaced bullet.\n"))

    def test_temporary_autosquash_messages(self):
        for prefix in ("fixup! ", "squash! ", "amend! "):
            with self.subTest(prefix=prefix):
                self.assertFalse(violations(prefix + "feat(inbox): add avatars\n"))
                self.assertTrue(violations(prefix))

    def test_merge_exemption_requires_merge_state(self):
        message = "Merge branch 'topic'\n"
        self.assertTrue(violations(message))
        self.assertFalse(violations(message, allow_merge=True))
        self.assertTrue(violations("update stuff\n", allow_merge=True))

    def test_title_length_is_only_a_target(self):
        self.assertFalse(violations("feat(inbox): add " + "a" * 80 + "\n\n- Add a field.\n"))


# Git exports GIT_DIR and similar variables to hooks. Dropping them keeps the test repository
# separate when checkAll runs inside another Git hook.
ISOLATED_GIT_ENV = {
    name: value for name, value in os.environ.items() if not name.startswith("GIT_")
}


class GitHookTests(unittest.TestCase):
    def test_git_rejects_invalid_message_and_accepts_valid_message(self):
        with tempfile.TemporaryDirectory(prefix="otus commit test ") as directory:
            repo = Path(directory)

            def git(*args, check=True):
                return subprocess.run(
                    ["git", *args],
                    cwd=repo,
                    env=ISOLATED_GIT_ENV,
                    text=True,
                    capture_output=True,
                    check=check,
                )

            git("init", "--quiet")
            git("config", "user.name", "Otus test")
            git("config", "user.email", "otus-test@example.invalid")
            git("config", "commit.gpgsign", "false")
            git("config", "core.hooksPath", ".githooks")
            (repo / ".githooks").mkdir()
            (repo / "tools").mkdir()
            hook = repo / ".githooks/commit-msg"
            shutil.copyfile(ROOT / ".githooks/commit-msg", hook)
            hook.chmod(0o755)
            shutil.copyfile(
                ROOT / "tools/check_commit_message.py", repo / "tools/check_commit_message.py"
            )
            for message in (
                "update stuff",
                "feat(inbox): add avatars",
                "feat(inbox): add avatars\n\n" + "- Show a photo.\n" * 9,
            ):
                rejected = git("commit", "--allow-empty", "-m", message, check=False)
                self.assertNotEqual(rejected.returncode, 0)
                self.assertIn("Commit message rejected", rejected.stderr)
                self.assertNotEqual(git("rev-parse", "--verify", "HEAD", check=False).returncode, 0)
            attributed = VALID + "\nCo-Authored-By: Claude <noreply@anthropic.com>\n"
            git("commit", "--allow-empty", "-m", attributed)
            self.assertEqual(git("log", "-1", "--format=%B").stdout.strip(), VALID.strip())
            git("config", "core.commentChar", ";")
            message_file = repo / "message.txt"
            message_file.write_text(VALID + "; Editor comment\n", encoding="utf-8")
            subprocess.run(
                [str(hook), str(message_file)],
                cwd=repo,
                env=ISOLATED_GIT_ENV,
                check=True,
                capture_output=True,
                text=True,
            )
            primary_branch = git("branch", "--show-current").stdout.strip()
            git("checkout", "-b", "topic")
            git("commit", "--allow-empty", "-m", "feat(calendar): add events\n\n- Add events.")
            git("checkout", primary_branch)
            git("merge", "--no-ff", "--no-commit", "topic")
            message_file.write_text("update stuff\n", encoding="utf-8")
            rejected = subprocess.run(
                [str(hook), str(message_file)],
                cwd=repo,
                env=ISOLATED_GIT_ENV,
                capture_output=True,
                text=True,
            )
            self.assertNotEqual(rejected.returncode, 0)
            git("commit", "--no-edit")
            self.assertTrue(git("log", "-1", "--format=%s").stdout.startswith("Merge "))


if __name__ == "__main__":
    unittest.main()

"""Secrets reach only the workflow steps that need them, never a Gradle build."""

import re
import unittest
from pathlib import Path

WORKFLOWS = Path(__file__).resolve().parents[2] / ".github/workflows"
STEP = re.compile(r" {6}- ")
SHARED_ENV = re.compile(r"(?: {4})?env:\s*$")


def steps(text: str) -> list[str]:
    """Each step's text, from its `- ` line to the next step or the end of its job."""
    found: list[list[str]] = []
    current: list[str] | None = None
    for line in text.splitlines():
        if STEP.match(line):
            current = [line]
            found.append(current)
        elif current is not None and (line.startswith(" " * 8) or not line.strip()):
            current.append(line)
        else:
            current = None
    return ["\n".join(step) for step in found]


def shared_env(text: str) -> list[str]:
    """The `env` blocks of whole workflows and jobs, which every step of theirs receives."""
    blocks: list[str] = []
    lines = text.splitlines()
    for i, line in enumerate(lines):
        if not SHARED_ENV.fullmatch(line):
            continue
        indent = len(line) - len(line.lstrip())
        block = []
        for following in lines[i + 1 :]:
            if following.strip() and len(following) - len(following.lstrip()) <= indent:
                break
            block.append(following)
        blocks.append("\n".join(block))
    return blocks


class WorkflowSecretTests(unittest.TestCase):
    def workflows(self):
        return sorted(WORKFLOWS.glob("*.yml"))

    def test_gradle_steps_get_no_secrets(self):
        for path in self.workflows():
            for step in steps(path.read_text()):
                if "gradlew" in step:
                    with self.subTest(workflow=path.name, step=step.splitlines()[0]):
                        self.assertNotIn("secrets.", step)

    def test_workflows_and_jobs_share_no_secrets(self):
        for path in self.workflows():
            for block in shared_env(path.read_text()):
                with self.subTest(workflow=path.name):
                    self.assertNotIn("secrets.", block)

    def test_only_the_signing_step_gets_the_nightly_key(self):
        text = (WORKFLOWS / "nightly.yml").read_text()
        holders = [step for step in steps(text) if "OTUS_NIGHTLY_KEYSTORE" in step]
        self.assertEqual(["Sign the APK"], [_name(step) for step in holders])

    def test_steps_are_found(self):
        text = (WORKFLOWS / "nightly.yml").read_text()
        self.assertIn("Build the unsigned APK", [_name(step) for step in steps(text)])
        shared = shared_env("env:\n  A: ${{ secrets.B }}\njobs:\n")
        self.assertEqual(["  A: ${{ secrets.B }}"], shared)


def _name(step: str) -> str:
    match = re.search(r"name: (.+)", step)
    return match.group(1).strip() if match else ""


if __name__ == "__main__":
    unittest.main()

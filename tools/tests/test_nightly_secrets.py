"""Only the signing step gets the nightly key.

otus-meta's architecture check covers the suite rule that no secret reaches Gradle. These tests use
its workflow reader, which architectureTest puts on PYTHONPATH.
"""

import unittest
from pathlib import Path

from workflow_secrets import step_name, steps

NIGHTLY = Path(__file__).resolve().parents[2] / ".github/workflows/nightly.yml"


class NightlySecretTests(unittest.TestCase):
    def test_only_the_signing_step_gets_the_nightly_key(self):
        holders = [step for step in steps(NIGHTLY.read_text()) if "OTUS_NIGHTLY_KEYSTORE" in step]
        self.assertEqual(["Sign the APK"], [step_name(step) for step in holders])

    def test_steps_are_found(self):
        names = [step_name(step) for step in steps(NIGHTLY.read_text())]
        self.assertIn("Build the unsigned APK", names)


if __name__ == "__main__":
    unittest.main()

"""Boundary cases and failure behavior for scoped file-length exceptions."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_architecture import violations
from file_length import file_length_violations

HEADER = "// otus-file-length: 420 -- Keep this protocol mapping together for review."


def source(lines, header="// Source file"):
    return header + "\n" + "\n" * (lines - 1)


class FileLengthTests(unittest.TestCase):
    def check(self, lines, header="// Source file", suffix=".kt"):
        return file_length_violations(Path("Example" + suffix), source(lines, header))

    def test_kotlin_boundary(self):
        self.assertFalse(self.check(300))
        self.assertTrue(self.check(301))

    def test_swift_boundary(self):
        self.assertFalse(self.check(200, suffix=".swift"))
        self.assertTrue(self.check(201, suffix=".swift"))

    def test_valid_kotlin_exception_and_its_boundary(self):
        self.assertFalse(self.check(420, HEADER))
        self.assertTrue(self.check(421, HEADER))

    def test_valid_swift_exception(self):
        self.assertFalse(self.check(250, HEADER, suffix=".swift"))

    def test_unused_exception_fails(self):
        self.assertTrue(self.check(300, HEADER))

    def test_reason_required(self):
        self.assertTrue(self.check(301, "// otus-file-length: 420"))
        self.assertTrue(self.check(301, "// otus-file-length: 420 -- too long"))

    def test_limit_must_be_larger_than_default(self):
        self.assertTrue(self.check(301, HEADER.replace("420", "300")))

    def test_unbounded_or_negative_limits_rejected(self):
        for limit in ["ignore", "-1", "0", "9999999"]:
            with self.subTest(limit=limit):
                self.assertTrue(self.check(301, HEADER.replace("420", limit)))

    def test_exception_must_be_on_first_line(self):
        self.assertTrue(self.check(301, "// license\n" + HEADER))

    def test_duplicate_directives_rejected(self):
        self.assertTrue(self.check(301, HEADER + "\n" + HEADER))

    def test_newline_styles_count_equally(self):
        text = source(301).replace("\n", "\r\n")
        self.assertTrue(file_length_violations(Path("Example.kt"), text))

    def test_exception_does_not_disable_architecture_checks(self):
        path = Path("shared/src/commonMain/kotlin/com/otus/mail/designsystem/elements/Example.kt")
        header = (
            HEADER
            + "\npackage com.otus.mail.designsystem.elements"
            + "\nimport com.otus.mail.feature.inbox.presentation.screens.InboxScreen"
        )
        errors = violations(path, source(310, header), "com.otus.mail")
        self.assertTrue(any("designsystem cannot depend" in error for error in errors))
        self.assertFalse(any("file has" in error for error in errors))


if __name__ == "__main__":
    unittest.main()

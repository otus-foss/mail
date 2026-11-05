"""Check the curated sender list that Inbox categories ship with."""

import re
import unittest
from pathlib import Path

LIST = (
    Path(__file__).resolve().parents[2]
    / "shared/src/commonMain/composeResources/files/sender_categories.txt"
)
CATEGORIES = {"transactions", "notifications", "newsletters"}
DOMAIN = re.compile(r"^(?:[a-z0-9-]+\.)+[a-z]{2,}$")


def entries():
    lines = (line.split("#", 1)[0].split() for line in LIST.read_text().splitlines())
    return [fields for fields in lines if fields]


class SenderCategoriesTests(unittest.TestCase):
    def test_the_list_has_one_numbered_version(self):
        versions = [fields for fields in entries() if fields[0] == "version"]
        self.assertEqual(1, len(versions))
        self.assertEqual(2, len(versions[0]))
        self.assertTrue(versions[0][1].isdigit(), versions[0])

    def test_each_entry_is_a_known_category_and_a_bare_domain(self):
        for fields in entries():
            if fields[0] == "version":
                continue
            with self.subTest(entry=" ".join(fields)):
                self.assertEqual(2, len(fields))
                self.assertIn(fields[0], CATEGORIES)
                self.assertRegex(fields[1], DOMAIN)

    def test_no_domain_is_listed_twice(self):
        domains = [fields[1] for fields in entries() if fields[0] != "version"]
        self.assertEqual(sorted(set(domains)), sorted(domains))


if __name__ == "__main__":
    unittest.main()

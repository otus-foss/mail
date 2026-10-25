"""Exercise nightly retries without accessing GitHub or publishing releases."""

import json
import subprocess
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import nightly_version

MAIL = "a" * 40
META = "b" * 40
OLD_MAIL = "c" * 40
OLD_META = "d" * 40


def release(number, *, draft=False, provenance=True):
    return {
        "tag_name": f"nightly-{number}",
        "draft": draft,
        "published_at": None if draft else f"2026-10-{number - 180:02d}T00:10:00Z",
        "assets": [{"name": "provenance.json"}] if provenance else [],
    }


class NightlyVersionTests(unittest.TestCase):
    def outputs(self, pages, provenance=None, error=None):
        def command(*args):
            if args == ("git", "rev-parse", "HEAD"):
                return MAIL
            if args == ("git", "-C", "otus-meta", "rev-parse", "HEAD"):
                return META
            if args == ("git", "rev-list", "--count", "HEAD"):
                return "200"
            if args[:2] == ("gh", "api"):
                self.assertIn("--paginate", args)
                self.assertIn("--slurp", args)
                if error == "api":
                    raise subprocess.CalledProcessError(1, args)
                return json.dumps(pages)
            self.assertEqual(args[:3], ("gh", "release", "download"))
            self.downloaded.append(args[3])
            if error == "download":
                raise subprocess.CalledProcessError(1, args)
            return json.dumps(provenance)

        self.downloaded = []
        with patch.object(nightly_version, "command", side_effect=command):
            return nightly_version.version_outputs("example/nightly")

    def test_first_release_uses_commit_count(self):
        self.assertEqual("200", self.outputs([[]])["number"])

    def test_legacy_release_without_provenance_rebuilds(self):
        result = self.outputs([[release(201, provenance=False)]])
        self.assertEqual("202", result["number"])
        self.assertEqual("", result["previous_mail"])
        self.assertEqual("2026-10-21T00:10:00Z", result["previous_date"])
        self.assertEqual([], self.downloaded)

    def test_first_release_has_no_previous_date(self):
        self.assertEqual("", self.outputs([[]])["previous_date"])

    def test_matching_published_sources_skip(self):
        result = self.outputs([[release(201)]], {"mail": MAIL, "meta": META})
        self.assertNotIn("number", result)

    def test_a_change_in_either_repository_rebuilds(self):
        for mail, meta in [(OLD_MAIL, META), (MAIL, OLD_META)]:
            with self.subTest(mail=mail, meta=meta):
                result = self.outputs([[release(201)]], {"mail": mail, "meta": meta})
                self.assertEqual("202", result["number"])
                self.assertEqual(mail, result["previous_mail"])
                self.assertEqual(meta, result["previous_meta"])
                self.assertEqual(MAIL, result["sha"])
                self.assertEqual(META, result["meta_sha"])

    def test_matching_draft_provenance_cannot_skip_the_first_publication(self):
        result = self.outputs([[release(201, draft=True)]], {"mail": MAIL, "meta": META})
        self.assertEqual("202", result["number"])
        self.assertEqual([], self.downloaded)

    def test_retry_skips_reserved_draft_numbers_and_reads_published_provenance(self):
        result = self.outputs(
            [[release(203, draft=True), release(202, draft=True)], [release(201)]],
            {"mail": OLD_MAIL, "meta": META},
        )
        self.assertEqual("204", result["number"])
        self.assertEqual(["nightly-201"], self.downloaded)
        self.assertEqual(OLD_MAIL, result["previous_mail"])
        self.assertEqual("2026-10-21T00:10:00Z", result["previous_date"])

    def test_matching_publication_on_a_later_page_skips_despite_newer_drafts(self):
        result = self.outputs(
            [[release(202, draft=True)], [release(201)]], {"mail": MAIL, "meta": META}
        )
        self.assertNotIn("number", result)
        self.assertEqual(["nightly-201"], self.downloaded)

    def test_release_numbers_do_not_depend_on_api_order(self):
        result = self.outputs(
            [[release(199)], [release(202, draft=True), release(201)]],
            {"mail": OLD_MAIL, "meta": META},
        )
        self.assertEqual("203", result["number"])
        self.assertEqual(["nightly-201"], self.downloaded)

    def test_api_or_asset_failures_stop_version_selection(self):
        for error in ("api", "download"):
            with self.subTest(error=error), self.assertRaises(subprocess.CalledProcessError):
                self.outputs([[release(201)]], error=error)

    def test_malformed_provenance_stops_version_selection(self):
        for sha in ("short", None, 42):
            with self.subTest(sha=sha), self.assertRaises(ValueError):
                self.outputs([[release(201)]], {"mail": sha, "meta": META})

    def test_unexpected_release_tag_stops_version_selection(self):
        with self.assertRaises(ValueError):
            self.outputs([[{"tag_name": "unexpected"}]])


if __name__ == "__main__":
    unittest.main()

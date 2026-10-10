"""Static inventory integrity checks; fixtures contain no upstream crypto material."""

import hashlib
import json
import unittest
from unittest.mock import patch

from scripts.audit_native_sources import inspect_file, inventory, license_signals, selected
from scripts.inspect_candidates import source_url


class NativeInventoryTests(unittest.TestCase):
    def setUp(self):
        self.repo = {"id": "fixture", "repository": "test/fixture", "commit": "a" * 40,
                     "prefixes": ["lib/"], "files": ["LICENSE"]}
        self.data = b"/* Synthetic inventory fixture, no application code. */\n"
        self.blob = {"path": "lib/test.c", "type": "blob",
                     "sha": hashlib.sha1(f"blob {len(self.data)}\0".encode() + self.data).hexdigest()}

    def test_matching_git_blob_is_recorded_without_source(self):
        with patch("scripts.audit_native_sources.fetch", return_value=self.data):
            result = inspect_file((self.repo, self.blob))
        self.assertEqual(result["git_blob_sha1"], self.blob["sha"])
        self.assertNotIn("Synthetic inventory", json.dumps(result))
        self.assertEqual(result["review_status"], "MANUAL_REVIEW_REQUIRED")

    def test_changed_remote_bytes_rejected(self):
        with patch("scripts.audit_native_sources.fetch", return_value=b"changed"):
            with self.assertRaisesRegex(ValueError, "Git blob mismatch"):
                inspect_file((self.repo, self.blob))

    def test_truncated_repository_tree_rejected(self):
        with patch("scripts.audit_native_sources.fetch", return_value=b'{"truncated":true}'):
            with self.assertRaisesRegex(ValueError, "Truncated"):
                inventory({"repositories": [self.repo]})

    def test_missing_required_file_rejected(self):
        with patch("scripts.audit_native_sources.fetch", return_value=b'{"tree":[],"truncated":false}'):
            with self.assertRaisesRegex(ValueError, "allowlist files missing"):
                inventory({"repositories": [self.repo]})

    def test_scope_excludes_unlisted_files(self):
        self.assertTrue(selected(self.repo, "LICENSE"))
        self.assertTrue(selected(self.repo, "lib/file.c"))
        self.assertFalse(selected(self.repo, "other/file.c"))
        self.assertFalse(selected(self.repo, "lib/blob.bin"))

    def test_license_signals_are_not_legal_approval(self):
        self.assertEqual(license_signals("No license found"), [])
        self.assertEqual(license_signals("GNU Lesser General Public License"), ["LGPL-text"])

    def test_cplusplus_include_directory_is_encoded(self):
        self.assertTrue(source_url(self.repo, "include/plist++/Node.h").endswith("include/plist%2B%2B/Node.h"))

    def test_url_query_cannot_escape_pinned_path(self):
        with self.assertRaises(ValueError):
            source_url(self.repo, "LICENSE?ref=main")

    def test_multiline_license_notice_detected(self):
        text = "/* GNU Lesser General Public\n * License, version 2.1 */"
        self.assertEqual(license_signals(text), ["LGPL-text"])


if __name__ == "__main__":
    unittest.main()

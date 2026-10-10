"""Preparation-tool regression tests. Synthetic fixtures are NOT mirroring evidence."""

import csv
import json
import shutil
import tempfile
import unittest
from pathlib import Path

from scripts.check_readiness import ROOT, check, check_evidence, digest, local_evidence
from scripts.environment_doctor import adb_counts, properties, sdk_report
from scripts.inspect_candidates import fingerprint, source_url


class ReadinessTests(unittest.TestCase):
    """Exercise false-positive paths in the acceptance bookkeeping."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.catalog = json.loads((ROOT / "docs/TEST_CATALOG.json").read_text(encoding="utf-8"))
        for name in ["REQUIREMENTS_TRACEABILITY.csv", self.catalog["spec_file"],
                     "docs/TEST_CATALOG.json", "docs/PHASE0_STATUS.json"]:
            destination = self.root / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / name, destination)

    def mutate_rows(self, edit):
        path = self.root / "REQUIREMENTS_TRACEABILITY.csv"
        with path.open(encoding="utf-8", newline="") as stream:
            reader = csv.DictReader(stream)
            fields, rows = reader.fieldnames, list(reader)
        edit(rows)
        with path.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=fields)
            writer.writeheader()
            writer.writerows(rows)

    def assert_invalid(self):
        result = check(self.root)
        self.assertFalse(result["ok"])
        self.assertTrue(result["errors"])

    def test_preparation_consistent_but_release_blocked(self):
        self.assertTrue(check(self.root)["ok"])
        self.assertFalse(check(self.root, release=True)["ok"])

    def test_missing_p0_cannot_be_removed_to_turn_release_green(self):
        self.mutate_rows(lambda rows: rows.pop(0))
        self.assert_invalid()

    def test_p0_priority_cannot_be_downgraded(self):
        self.mutate_rows(lambda rows: rows[0].update({"Öncelik": "P1"}))
        self.assert_invalid()

    def test_duplicate_requirement_rejected(self):
        self.mutate_rows(lambda rows: rows.append(rows[0].copy()))
        self.assert_invalid()

    def test_unregistered_test_rejected(self):
        self.mutate_rows(lambda rows: rows[0].update({"Test-ID": "MADE-UP"}))
        self.assert_invalid()

    def test_pass_without_evidence_rejected(self):
        self.mutate_rows(lambda rows: rows[0].update({"Test sonucu": "PASS"}))
        self.assert_invalid()

    def test_unknown_status_rejected(self):
        self.mutate_rows(lambda rows: rows[0].update({"Test sonucu": "DONE"}))
        self.assert_invalid()

    def test_changed_spec_requires_review(self):
        path = self.root / self.catalog["spec_file"]
        path.write_text(path.read_text(encoding="utf-8") + "\nChanged spec.\n", encoding="utf-8")
        self.assert_invalid()

    def test_wrong_source_line_rejected(self):
        self.mutate_rows(lambda rows: rows[0].update({"Kaynak satır": "1"}))
        self.assert_invalid()

    def test_empty_gate_list_rejected(self):
        path = self.root / "docs/PHASE0_STATUS.json"
        phase = json.loads(path.read_text(encoding="utf-8"))
        phase["gates"] = []
        path.write_text(json.dumps(phase), encoding="utf-8")
        self.assert_invalid()

    def test_product_complete_claim_rejected(self):
        path = self.root / "docs/PHASE0_STATUS.json"
        phase = json.loads(path.read_text(encoding="utf-8"))
        phase["product_complete"] = True
        path.write_text(json.dumps(phase), encoding="utf-8")
        self.assert_invalid()

    def test_evidence_outside_directory_rejected(self):
        (self.root / "outside.json").write_text("{}", encoding="utf-8")
        for path in ("artifacts/evidence/../../outside.json", "outside.json", "C:/outside.json", ""):
            with self.subTest(path=path), self.assertRaises(ValueError):
                local_evidence(self.root, path)

    def synthetic_evidence(self):
        """Create only temporary software-test fixtures, never product acceptance files."""
        folder = self.root / "artifacts/evidence"
        folder.mkdir(parents=True)
        sample = folder / "synthetic.txt"
        sample.write_text("Synthetic unit-test data. Not a device measurement.", encoding="utf-8")
        report = {"test_id": "P0-001", "kind": "physical", "status": "PASS",
                  "spec_sha256": self.catalog["spec_sha256"], "observed_at": "synthetic",
                  "reviewer": "unit-test-fixture", "build_sha256": "0" * 64,
                  "devices": {"iphone_model": "fixture", "ios_build": "fixture",
                              "android_tv_model": "fixture", "android_build": "fixture"},
                  "artifacts": [{"path": "artifacts/evidence/synthetic.txt", "sha256": digest(sample)}]}
        path = folder / "result.json"
        path.write_text(json.dumps(report), encoding="utf-8")
        return path, report, sample

    def verify_synthetic(self):
        check_evidence(self.root, "artifacts/evidence/result.json", "P0-001", "physical", self.catalog["spec_sha256"])

    def test_integrity_verifier_accepts_matching_record_structure(self):
        self.synthetic_evidence()
        self.verify_synthetic()

    def test_modified_artifact_rejected(self):
        _, _, sample = self.synthetic_evidence()
        sample.write_text("changed", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "hash mismatch"):
            self.verify_synthetic()

    def test_other_test_evidence_rejected(self):
        path, report, _ = self.synthetic_evidence()
        report["test_id"] = "P0-002"
        path.write_text(json.dumps(report), encoding="utf-8")
        with self.assertRaises(ValueError):
            self.verify_synthetic()

    def test_unit_evidence_cannot_satisfy_physical_test(self):
        path, report, _ = self.synthetic_evidence()
        report["kind"] = "unit"
        path.write_text(json.dumps(report), encoding="utf-8")
        with self.assertRaises(ValueError):
            self.verify_synthetic()

    def test_physical_test_requires_device_metadata(self):
        path, report, _ = self.synthetic_evidence()
        del report["devices"]
        path.write_text(json.dumps(report), encoding="utf-8")
        with self.assertRaises(ValueError):
            self.verify_synthetic()


class EnvironmentTests(unittest.TestCase):
    """Ensure absent devices or half-installed SDKs do not appear ready."""

    def test_empty_adb_list(self):
        self.assertEqual(adb_counts("List of devices attached\n")["authorized"], 0)

    def test_adb_counts_do_not_disclose_identifiers(self):
        result = adb_counts("List of devices attached\nsecret-device device product:test\nsecret2 unauthorized\nemulator-5554 device\nsecret3 offline\n")
        self.assertEqual(result, {"authorized": 2, "unauthorized": 1, "offline": 1, "emulators": 1})
        self.assertNotIn("secret", json.dumps(result))

    def test_properties_trim_ndk_whitespace(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "source.properties"
            path.write_text("Pkg.Revision = 27.2.12479018\n", encoding="utf-8")
            self.assertEqual(properties(path)["Pkg.Revision"], "27.2.12479018")

    def test_ndk_directory_alone_is_not_installed(self):
        with tempfile.TemporaryDirectory() as directory:
            sdk = Path(directory)
            (sdk / "ndk/29.0.14206865").mkdir(parents=True)
            result = sdk_report(sdk)
            self.assertFalse(result["ndks"][0]["complete_basics"])
            self.assertFalse(result["adb_query_ok"])


class ResearchTests(unittest.TestCase):
    """Keep research pinned and avoid retaining upstream source bytes."""

    def test_dynamic_ref_rejected(self):
        with self.assertRaises(ValueError):
            source_url({"repository": "owner/repo", "commit": "main"}, "LICENSE")

    def test_path_traversal_rejected(self):
        with self.assertRaises(ValueError):
            source_url({"repository": "owner/repo", "commit": "a" * 40}, "../LICENSE")

    def test_hash_normalization_without_source_retention(self):
        left, right = fingerprint(b"private-fixture\r\n"), fingerprint(b"private-fixture\n")
        self.assertNotEqual(left["sha256"], right["sha256"])
        self.assertEqual(left["lf_sha256"], right["lf_sha256"])
        self.assertNotIn("private-fixture", json.dumps(left))


if __name__ == "__main__":
    unittest.main()

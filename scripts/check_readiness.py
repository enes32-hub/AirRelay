"""Validate preparation records; --release fails until all evidence gates pass."""

import argparse
import csv
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STATUSES = {"NOT_RUN", "BLOCKED", "FAIL", "PASS"}
GATE_IDS = {"G0-LICENSE", "G0-SPIKE", "G0-TOOLCHAIN", "G0-SELECTION"}


def digest(path: Path) -> str:
    """Hash evidence bytes without loading media into memory."""
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def local_evidence(root: Path, name: str) -> Path:
    """Require evidence under this checkout's artifacts/evidence, including symlink resolution."""
    if not name or "\\" in name or ":" in name:
        raise ValueError("Evidence must use a repository-relative POSIX path")
    path = (root / name).resolve()
    allowed = (root / "artifacts/evidence").absolute()
    if not path.is_relative_to(allowed) or not path.is_file():
        raise ValueError("Evidence is missing or escapes artifacts/evidence")
    return path


def check_evidence(root: Path, name: str, test_id: str, kind: str, spec_hash: str) -> None:
    """Check evidence integrity, not the truth of a human's measurement or legal review."""
    report = json.loads(local_evidence(root, name).read_text(encoding="utf-8"))
    if (report.get("test_id"), report.get("kind"), report.get("status"), report.get("spec_sha256")) != (test_id, kind, "PASS", spec_hash):
        raise ValueError("Evidence test/kind/status/spec mismatch")
    if not report.get("observed_at") or not report.get("reviewer"):
        raise ValueError("Observation time and reviewer required")
    if not re.fullmatch(r"[0-9a-f]{64}", report.get("build_sha256", "")):
        raise ValueError("Evidence must identify the tested build")
    if kind == "physical":
        devices = report.get("devices", {})
        if not devices.get("iphone_model") or not devices.get("ios_build") or not devices.get("android_tv_model") or not devices.get("android_build"):
            raise ValueError("Physical iPhone and Android TV model/OS records required")
    artifacts = report.get("artifacts")
    if not isinstance(artifacts, list) or not artifacts:
        raise ValueError("PASS requires at least one hashed measurement/review artifact")
    for artifact in artifacts:
        if digest(local_evidence(root, artifact["path"])) != artifact["sha256"]:
            raise ValueError("Evidence artifact hash mismatch")


def check(root: Path, release: bool = False) -> dict:
    """Fail closed on malformed records, missing requirements or unsupported PASS claims."""
    # The requirement specification, the acceptance matrix and the test catalog are maintained with
    # the project rather than published in this repository: they are written in the language of the
    # original specification. A checkout without them still verifies everything else, and the release
    # gate stays blocked instead of silently passing.
    inputs = [root / "docs/SPECIFICATION.md", root / "REQUIREMENTS_TRACEABILITY.csv",
              root / "docs/TEST_CATALOG.json", root / "docs/PHASE0_STATUS.json"]
    missing = [str(item.relative_to(root)) for item in inputs if not item.is_file()]
    if missing:
        listed = ", ".join(missing)
        if release:
            return {"ok": False, "errors": ["Release gate cannot run: the acceptance inputs are "
                    f"maintained with the project and are not part of this checkout ({listed})"]}
        return {"ok": True, "skipped": True, "errors": [], "blockers": [],
                "scope": "Preparation records are maintained with the project and are not part of "
                         f"this checkout ({listed}); nothing was validated here."}
    errors, blockers = [], []
    with (root / "REQUIREMENTS_TRACEABILITY.csv").open(encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))
    catalog = json.loads((root / "docs/TEST_CATALOG.json").read_text(encoding="utf-8"))
    phase = json.loads((root / "docs/PHASE0_STATUS.json").read_text(encoding="utf-8"))
    spec_path = (root / catalog["spec_file"]).resolve()
    if not spec_path.is_relative_to(root.resolve()):
        raise ValueError("Spec path escapes checkout")
    spec_hash = digest(spec_path)
    lines = spec_path.read_text(encoding="utf-8").splitlines()
    if spec_hash != catalog["spec_sha256"]:
        errors.append("Specification changed; review traceability and evidence before updating hash")
    expected, inside = set(), False
    for index, line in enumerate(lines, 1):
        if line.startswith("## 3.1."):
            inside = True
        elif line.startswith("## 3.2."):
            inside = False
        if inside and line.startswith("- "):
            expected.add(str(index))
    if not expected:
        errors.append("P0 source section missing or empty")
    mapped = {r["Kaynak satır"] for r in rows if r["Kaynak bölüm"] == "3.1" and r["Öncelik"] == "P0"}
    if expected != mapped:
        errors.append("Section 3.1 P0 coverage is incomplete or unexpected")
    if len({r["REQ-ID"] for r in rows}) != len(rows):
        errors.append("Duplicate requirement ID")
    counts = {status: 0 for status in sorted(STATUSES)}
    for row in rows:
        req_id, test_id, status = row["REQ-ID"], row["Test-ID"], row["Test sonucu"]
        index = int(row["Kaynak satır"]) - 1
        if not 0 <= index < len(lines) or lines[index] != row["Kaynak metin"]:
            errors.append(f"{req_id}: source line mismatch")
        if row["Öncelik"] not in {"P0", "P1", "P2"}:
            errors.append(f"{req_id}: invalid priority")
        test = catalog["tests"].get(test_id)
        if not test or test.get("kind") not in {"physical", "build", "review"} or not test.get("procedure"):
            errors.append(f"{req_id}: missing/invalid acceptance test")
        if status not in STATUSES:
            errors.append(f"{req_id}: invalid status")
            continue
        counts[status] += 1
        if status == "PASS" and test:
            try:
                check_evidence(root, row["Kanıt dosyası"], test_id, test["kind"], spec_hash)
            except (ValueError, OSError, KeyError, TypeError) as error:
                errors.append(f"{req_id}: {error}")
        if row["Öncelik"] == "P0" and status != "PASS":
            blockers.append(f"{req_id}: {status}")
    gates = phase["gates"]
    if {g["id"] for g in gates} != GATE_IDS or len(gates) != len(GATE_IDS):
        errors.append("Missing, duplicate or unexpected phase gate")
    for gate in gates:
        if gate["status"] not in STATUSES:
            errors.append(f"{gate['id']}: invalid gate status")
        if gate["status"] != "PASS":
            blockers.append(f"{gate['id']}: {gate['status']}")
        else:
            try:
                check_evidence(root, gate.get("evidence", ""), gate["id"],
                               "physical" if gate["id"] == "G0-SPIKE" else "review", spec_hash)
            except (ValueError, OSError, KeyError, TypeError) as error:
                errors.append(f"{gate['id']}: {error}")
    if phase.get("traceability_complete") is not True:
        blockers.append("Detailed specification traceability is not complete")
    if not phase.get("selected_core"):
        blockers.append("No approved protocol core")
    if phase.get("product_complete") and (blockers or errors):
        errors.append("Product marked complete despite unmet acceptance gates")
    return {"mode": "release" if release else "preparation", "requirements": len(rows),
            "results": counts, "errors": errors, "blockers": blockers,
            "ok": not errors and (not release or not blockers),
            "scope": "Record consistency only; this tool does not certify mirroring, legal compliance or release fitness."}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        result = check(ROOT, args.release)
    except (ValueError, KeyError, TypeError, OSError) as error:
        result = {"ok": False, "errors": [f"Invalid preparation records: {error}"]}
    text = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text, encoding="utf-8")
    print(text)
    raise SystemExit(0 if result["ok"] else 2)


if __name__ == "__main__":
    main()

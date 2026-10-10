"""Run offline preparation-tool tests and write an explicitly scoped result."""

import json
import sys
import unittest
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))


def main() -> None:
    suite = unittest.defaultTestLoader.discover(str(ROOT / "tests"))
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    report = {"observed_at": datetime.now(timezone.utc).isoformat(),
              "scope": "preparation-tools-unit-tests-only", "physical_device_tests_run": 0,
              "tests_run": result.testsRun, "failures": len(result.failures),
              "errors": len(result.errors), "skipped": len(result.skipped),
              "ok": result.wasSuccessful(),
              "failure_details": [{"test": str(test), "trace": trace} for test, trace in result.failures + result.errors]}
    path = ROOT / "artifacts/preparation-tests.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    raise SystemExit(0 if result.wasSuccessful() else 1)


if __name__ == "__main__":
    main()

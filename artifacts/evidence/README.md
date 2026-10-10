# Evidence contract

`scripts/check_readiness.py` refuses to accept a `PASS` unless a matching record exists **in this
directory**. The gate checks integrity, not the truth of a measurement: it verifies that the
evidence is internally consistent, hashed and linked to the tested build. A human still owns the
measurement or review it records.

## Rules the gate enforces

1. **Location.** `Kanıt dosyası` in `REQUIREMENTS_TRACEABILITY.csv` must be a repository-relative
   POSIX path that resolves inside `artifacts/evidence/`. No backslashes, no drive letters.
2. **Match.** The record's `test_id`, `kind` and `status` must equal the row's test id, the
   catalog's test kind and `PASS`, and its `spec_sha256` must equal the hash of the specification
   file recorded in `docs/TEST_CATALOG.json`.
3. **Attribution.** `observed_at` and `reviewer` are required.
4. **Build linkage.** `build_sha256` must be a 64-character lowercase hex digest of the tested
   build. For `kind: physical` the record must also carry the device identity:

   ```json
   { "devices": { "iphone_model": "...", "ios_build": "...",
                  "android_tv_model": "...", "android_build": "..." } }
   ```

5. **Artifacts.** At least one hashed artifact must accompany the record, and every listed
   `sha256` must match the file on disk.

## Minimal record

```json
{
  "test_id": "P0-001",
  "kind": "physical",
  "status": "PASS",
  "spec_sha256": "<from docs/TEST_CATALOG.json>",
  "observed_at": "2026-10-10T20:00:00Z",
  "reviewer": "<who measured it>",
  "build_sha256": "<sha256 of the tested APK>",
  "devices": { "iphone_model": "...", "ios_build": "...",
               "android_tv_model": "...", "android_build": "..." },
  "artifacts": [ { "path": "artifacts/evidence/<file>", "sha256": "<sha256 of that file>" } ]
}
```

## What must never happen

* Do not write a `PASS` record for a run that did not happen, and do not copy a record between
  builds. The release gate is intentionally fail-closed: with the current state it exits 2 in
  `--release` mode, and that is the correct result until real acceptance evidence exists.
* Do not edit `spec_sha256` to make a stale record fit. A specification change invalidates the
  traceability review by design.

## Current state

This directory is intentionally empty of PASS records. `REQUIREMENTS_TRACEABILITY.csv` is seeded
with every requirement at `NOT_RUN`, so the preparation gate passes on record consistency while
the release gate stays blocked.

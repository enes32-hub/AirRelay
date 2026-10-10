# Test plan and Stage 0 connection attempt

**Status:** application and physical acceptance tests have not been run. The [test catalogue](TEST_CATALOG.json) contains the 3.1 P0 list, the section 19 measurements and the E2E-001..015 scenarios. [Traceability](../REQUIREMENTS_TRACEABILITY.csv) is the initial matrix; coverage of all detailed specification items is not complete yet.

## Stage 0 physical spike

Prerequisites: a pinned core that has passed review, verified build dependencies, one physical Android TV and one iPhone, the same user-controlled network, TV ADB authorisation. No candidate APK is installed before licence/provenance has passed. Before the test it is verified that there is no important active session on the device.

1. Report the model/OS/ABI and network conditions without recording personal identifiers.
2. Produce an isolated candidate build and SHA-256; do not label it as an AirRelay release or as P0 completed.
3. Open the receiver on the TV; verify visibility in the Control Center → Screen Mirroring list without installing any application on the iPhone.
4. Enter the PIN from the TV; try correct, wrong and expired PIN behaviour. Do not capture the PIN in a log or an evidence image.
5. Do a live home screen, fast scrolling, unprotected test video/audio and 20 orientation changes. Record the MediaCodec hardware name and the AudioTrack path.
6. Measure the 30-minute 1080p60 target; if the hardware is insufficient, do not hide the failed target. Success is not marked by silently dropping to a lower quality.
7. Stop from the sender and from the TV remote; measure resources over 20 connection cycles. Reconnect on a local network with the internet off.
8. Report the result with the [measurement method](PERFORMANCE_METHOD.md). Make the core decision with the licence and spike results; then move to Stage 1 main product code.

This short spike does not count as having passed a 2/8 hour soak or the entire physical matrix. The human operations required for physical measurement are not reported as if they had been automated.

## Evidence rules

During development `NOT_RUN`, `BLOCKED`, `FAIL` and `PASS` are clearly separated. Having no untested row in P0 under specification 44 is a **delivery gate**; making the currently untested ones PASS is forbidden. A green readiness check does not mean the product is ready.

`scripts/check_readiness.py` checks CSV coverage, source hashes/lines, test IDs and evidence integrity. In a PASS row the `Kanıt dosyası` field points to the real JSON report inside `artifacts/evidence/`. The report contains `test_id`, `kind` (`physical`, `build`, `review`), `status`, `spec_sha256`, `observed_at`, `reviewer`, `build_sha256` and an `artifacts` array. Each artifact carries the relative `path` and `sha256` values of the real file. Physical evidence additionally contains `iphone_model`, `ios_build`, `android_tv_model` and `android_build` under `devices`. G0 gate PASS records use the same schema as well; G0-SPIKE is physical and the other G0 gates are of type review.

The tool cannot by itself prove the accuracy of the metadata, every detailed threshold or legal suitability. The test owner is responsible for reviewing the measurements and the raw evidence. A sample PASS report is not presented to the user as real evidence. The temporary synthetic records under `tests/` are only regression tests for the audit tool; they are not acceptance evidence for the product.

## Running

```powershell
python scripts/run_checks.py
python scripts/check_readiness.py --output artifacts/readiness-report.json
python scripts/check_readiness.py --release --output artifacts/release-readiness.json
```

The first command is the test of the readiness tools. The second command audits the consistency of the records while open gaps exist. In the current state the third command must reject release readiness with exit code **2**. This rejection is not a test error but the expected gate behaviour. For the Python path see the [environment document](BUILD_ENVIRONMENT.md).

`seed_traceability.py` only produces the initial starting files; it stops on a second run so as not to delete existing results. When the specification changes, the source lines and test mappings must be reviewed by hand, and the hash must be updated only after this review.

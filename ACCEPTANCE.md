# Acceptance status

An honest record of what has been verified for this application and what has not. It exists because
the acceptance bookkeeping itself — the requirement specification, the traceability matrix and the
test catalog — is maintained with the project rather than published in this repository: those files
are written in the language of the original specification, and translating a 3,090-line
specification to publish its matrix would not have added information. The practice is unchanged and
still runs locally.

**Summary: 102 acceptance requirements are recorded, all of them `NOT_RUN`.** Nothing here is a
release-quality acceptance record, and the release gate stays blocked until it is.

## The bookkeeping

| Item | Where it lives | State |
|---|---|---|
| Requirement specification | project-held (3,090 lines) | referenced by line from the matrix |
| Traceability matrix | project-held | 102 requirements, one per specification line |
| Test catalog | project-held | 58 recorded tests over 10 modules |
| Evidence records | [`artifacts/evidence/`](artifacts/evidence/) | measurements and reports, hashes checked |

Every requirement is priority `P0` and currently `NOT_RUN`. `scripts/check_readiness.py` validates the
records when the inputs are present, and `--release` exits `2` until every requirement has evidence:
that is the correct, fail-closed result, not a defect. On a checkout without those inputs the
preparation check reports that it validated nothing instead of failing for the wrong reason, while
the release gate still refuses to run.

## Verified (with evidence in this repository)

| Observation | Evidence |
|---|---|
| Release APK signed with the release certificate, not debuggable, both ABIs, 16 KB page alignment | [`artifacts/evidence/release-apk-verification.txt`](artifacts/evidence/release-apk-verification.txt) |
| `arm64-v8a` and `armeabi-v7a` native libraries: ELF class/machine, complete JNI symbol set | [`artifacts/evidence/native-abi-verification.txt`](artifacts/evidence/native-abi-verification.txt) |
| Bonjour records measured from another host on the same LAN: PTR/SRV/TXT/A for `_airplay._tcp` and `_raop._tcp`, port 7000, receiver address; an iPhone browsed the receiver and was answered | [`artifacts/evidence/mdns-publication.txt`](artifacts/evidence/mdns-publication.txt) |

Also observed on real hardware and recorded with the delivery package rather than in this repository:

- a real iPhone mirroring session on a Xiaomi MiTV-AFMU0 (Android 14) using the hardware HEVC
  decoder, with the first frame submitted and no audio underrun, crash or ANR,
- a 20-cycle connect/disconnect resource-leak measurement (a graphics texture/program leak was found
  and fixed),
- re-binding port 7000 after a restart and after a fresh process,
- both themes rendering the same layout on a 1080p TV,
- the playback mode applying per stream: ending a stream returns the next connection to direct
  playback.

## Not verified

- **Every acceptance item is `NOT_RUN`**: PIN flows, 30-minute 720p/1080p sessions and the §19
  performance thresholds, resolution and rotation transitions, discovery latency, running on a 16 KB
  page device, and the per-iPhone-version / per-TV-model matrix.
- The `arm64-v8a` slice is verified structurally only; it has never been executed on a 64-bit
  device, because none was available.
- The Google Play upload flow has not been exercised.
- No test in the catalog has an evidence record, so no requirement can be claimed as passing.

## Recording evidence

1. Run the physical test described by the catalog entry for the requirement.
2. Write a report under [`artifacts/evidence/`](artifacts/evidence/) and hash it
   (`python -c "import hashlib,pathlib;print(hashlib.sha256(pathlib.Path('FILE').read_bytes()).hexdigest())"`).
3. Record it with `scripts/record_evidence.py`, which fills the matrix row and refuses a `PASS`
   without a matching, hashed, attributed report.
4. Re-run `scripts/check_readiness.py --release`; it only passes when nothing is left unproven.

This document describes the state of the repository, not a certification. The receiver is buildable,
signed, installable and observed working; it is not a release that has passed acceptance testing.

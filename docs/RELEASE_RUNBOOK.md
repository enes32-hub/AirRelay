# Release runbook

## Release identity — record and never lose this

| Item | Value |
|---|---|
| Application id | `com.airrelay.receiver` (frozen; changing it orphans every installation) |
| Current public release | versionCode 7, versionName `1.0.6` |
| First release-signed build (internal, never published) | versionCode 75, versionName `0.1.0-rc40-test` |
| Keystore | `keystore/airrelay-release.jks` (gitignored), alias `airrelay`, RSA 4096, validity 10000 days |
| Certificate DN | `CN=AirRelay, OU=Receiver, O=AirRelay, C=TR` |
| **Certificate SHA-256** | `c24b1ae32c1b12ad7461728a4d24675a41cda63a464f13eb3cbd7adfb7c77ef2` |
| Certificate SHA-1 | `e4ce9bd3fe78ca3aa80f46181c3303f410d16517` |
| First release-signed universal APK (internal) | 17.27 MB, SHA-256 `4C9160B291D2DA3552751E36F590DEF12ED109E226396167E5452A07DA56196B` |
| Current universal APK (1.0.6) | 17.29 MB, SHA-256 `6440a79f97a0d792a39e6a8d2e0a1ef3c3efe90ff109bf08e9d290b31663f924` |
| Current App Bundle (1.0.6) | 18.38 MB, SHA-256 `4fa329dc409a3abf067b7a019244d738a91003ff940c21cbbbd424e7b1197556` |

Verify every future artifact against the fingerprint above:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\<version>\apksigner.bat" verify --print-certs <apk> |
    Select-String 'SHA-256 digest'
```

A different digest means the wrong key was used — stop and fix it before publishing. The keystore
and its password exist in exactly one place today (this machine and `local.properties`); back both
up, or a disk failure makes future updates impossible. The source offer required by GPL-3.0 section 6
is recorded in `legal/NOTICE.md` and points at the public repository.

Everything a release needs, in the order it must happen. Steps marked **irreversible** decide the
identity of the application and cannot be undone later.

## 0. Preconditions

| Requirement | Why | Where |
|---|---|---|
| Release keystore exists and is backed up | **Irreversible.** A different key cannot update installations made with the old one; losing it means no existing install can ever be updated. | `scripts/create_release_keystore.ps1` |
| `local.properties` holds `storeFile`/`storePassword`/`keyAlias`/`keyPassword` | AGP only creates the `release` signing config when `storeFile` is present; otherwise `assembleRelease` produces an unsigned APK. | gitignored `local.properties` |
| Corresponding-source URL filled in `legal/NOTICE.md` | GPL-3.0 §6 obligation; gate `G0-LICENSE` stays blocked without it. | `legal/NOTICE.md` |
| `applicationId` frozen at `com.airrelay.receiver` | **Irreversible.** Changing it orphans every installation. | `app/build.gradle.kts` |
| ABI set chosen deliberately | A release build must never ship the emulator default (`x86_64`). Passing no ABIs now falls back to the real-TV ABIs, and the build script always passes them explicitly. | `-Abis` |

## 1. Build

```powershell
# Development / device test on a real TV (32-bit or 64-bit)
.\scripts\build_receiver.ps1 -Abis 'arm64-v8a,armeabi-v7a' -BuildRoot 'C:\AirRelayBuild'

# Release: R8 minification + resource shrinking. Always pass the shipped ABIs.
.\scripts\build_receiver.ps1 -Variant Release -Abis 'arm64-v8a,armeabi-v7a' -BuildRoot 'C:\AirRelayRelease'

# Native memory-safety check (arm64 only, HWASan + UBSan) — needs a 64-bit device
.\scripts\build_receiver.ps1 -Variant Sanitize -Abis 'arm64-v8a' -BuildRoot 'C:\AirRelaySanitize'
```

Output: `app/build/outputs/apk/<variant>/app-<variant>.apk` (and `.aab` for bundles).

## 2. Verify the artifact before installing anything

```powershell
$aapt = "$env:LOCALAPPDATA\Android\Sdk\build-tools\<version>\aapt2.exe"
& $aapt dump badging app-release.apk | Select-String 'package:|native-code|application-label|sdkVersion'
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\<version>\apksigner.bat" verify --print-certs app-release.apk
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\<version>\zipalign.exe" -c -P 16 -v 4 app-release.apk
```

Must hold:

* `native-code` lists **every** shipped ABI, and nothing else. An `x86_64`-only release is a bug.
* `apksigner verify` passes and the certificate is the release key, not the debug key.
* `zipalign -c -P 16` passes: Android 15+ devices with 16 KB pages require it, and the native build
  sets `ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` for exactly that reason.
* The release APK is **not** debuggable (`aapt2 dump badging` must not print `application-debuggable`).

## 3. Functional verification on a device

R8 minification is the step most likely to break the app silently, because the native core calls
26 Kotlin methods **by name** from `app/src/main/cpp/android_raop_callbacks.c` and `log_sink.h`
(`onVideoData`, `onAudioData`… `onVideoSessionPoll`, `onLog`). The keep rules in
`app/proguard-rules.pro` protect `bridge.RaopCallbackHandler`, its implementers, `bridge.LogListener`
and `bridge.NativeBridge`. A build that compiles proves nothing here: the failure appears as a
receiver that starts but never reacts to a connection.

So a release candidate must be exercised on a real TV:

1. Install and start: the receiver reports `Server started on port <n>` and registers RAOP + AIRPLAY.
2. Connect a real iPhone and mirror for at least a minute: first frame submitted, HEVC/H.264 codec
   started, audio underruns and decode errors at zero.
3. Stop and restart the receiver once.
4. Check `logcat` for `FATAL`, `Fatal signal`, `ANR in` and for `NoSuchMethodError` /
   `JNI DETECTED ERROR` — a stripped callback method surfaces as one of those.

## 4. What to publish together

| Artifact | Source |
|---|---|
| Signed APK and/or AAB | step 1 |
| `mapping.txt` (`app/build/outputs/mapping/release/`) | R8 output; needed to read future crash reports |
| SBOM | `python tools/generate_sbom.py` → `legal/SBOM.spdx.json` |
| Corresponding source | `python scripts/export_source_zip.py <out.zip>` — publish at the URL recorded in `legal/NOTICE.md` |
| NOTICE and LICENSE | `legal/NOTICE.md`, `LICENSE` |

## 5. Gate before publishing

```powershell
pwsh -File scripts/run_full_check.ps1          # provenance, bookkeeping, SBOM freshness, unit tests
python scripts/check_readiness.py --release    # must exit 0, not 2
```

`--release` exiting 2 is the correct, fail-closed result until the acceptance evidence exists.
Do not make it pass by editing records: `REQUIREMENTS_TRACEABILITY.csv` starts every requirement at
`NOT_RUN`, and a `PASS` row needs a hashed evidence record under `artifacts/evidence/`
(see the contract in `artifacts/evidence/README.md`).

## 6. Known blockers as of this revision

* **Signing**: no keystore yet — `G0-TOOLCHAIN` stays blocked until a release-signed artifact exists.
* **Source offer**: `legal/NOTICE.md` still carries `<SOURCE-URL-NOT-SET>`.
* **Acceptance evidence**: 102 requirements at `NOT_RUN`; `G0-LICENSE`, `G0-SPIKE`,
  `G0-TOOLCHAIN`, `G0-SELECTION` unresolved.
* **Version naming**: `1.0.6` / versionCode 7 is the current build (1.0.0, 1.0.1 and 1.0.2 were never published). Keep `versionCode` strictly
  increasing: a device only accepts an update whose code is higher than the installed one.
  before the first published build, and keep `versionCode` strictly increasing.

# AirRelay Receiver

An **AirPlay receiver for Android TV**. It lets an iPhone, iPad or Mac mirror its screen and stream
audio/video to your TV over the local network — no companion app on the Apple device, no account, no
cloud service.

| | |
|---|---|
| Application ID | `com.airrelay.receiver` |
| Version | `1.0.0` (versionCode 76) |
| Platform | Android TV, Android 7.0+ (API 24+), landscape, D-pad remote |
| Architectures | `arm64-v8a`, `armeabi-v7a` |
| Language | Kotlin + Jetpack Compose (Material 3), native C/C++ core |
| Licence | GPL-3.0 (see [Licence](#licence-and-third-party-components)) |

## Features

- Screen mirroring with hardware video decoding (MediaCodec → SurfaceView, HEVC and H.264)
- Audio playback with A/V synchronisation (AAC-LC, AAC-ELD, ALAC)
- PIN-protected pairing, trusted-device list, per-device access control
- Optional recording of the incoming stream, stored locally on the TV
- Fit / Fill / 1:1 picture modes, user-adjustable audio delay, network and playback statistics
- **Privacy-first:** no ads, no analytics, no telemetry, no developer server — everything stays on
  your local network

## Privacy

The full privacy policy is published at:

- **English (use this URL in the Play Console):** <https://enes32-hub.github.io/AirRelay/legal/privacy.html>
- **Türkçe:** <https://enes32-hub.github.io/AirRelay/legal/privacy-tr.html>

It is also available inside the app under *Settings → About*.

## Build

Requirements: **JDK 17**, **Android SDK** (platform 36, build-tools 36.0.0), **NDK 29.0.14206865**,
**CMake 3.22.1**, **Python 3.13** and, on Windows, **MSYS2** for the FFmpeg/OpenSSL build.
Details: [docs/BUILD_ENVIRONMENT.md](docs/BUILD_ENVIRONMENT.md).

Create `local.properties` with your own paths (see `local.properties.example`):

```properties
sdk.dir=C:/path/to/Android/Sdk
airrelay.python=C:/path/to/python.exe
airrelay.msys.root=C:/msys64
airrelay.cmake.command=C:/path/to/Android/Sdk/cmake/3.22.1/bin/cmake.exe
```

Build a signed release APK (both ABIs):

```powershell
.\scripts\build_receiver.ps1 -Variant Release -Abis 'arm64-v8a,armeabi-v7a' -BuildRoot C:\AirRelayBuild
```

Build a signed App Bundle (AAB) — run in the same build root, with the same `-Pairrelay.*` values:

```powershell
gradlew :app:bundleRelease
```

## Checks

Python 3.11+ with the standard library is enough (no pip dependencies):

```powershell
pwsh -File scripts/run_full_check.ps1
```

This runs source/provenance verification, acceptance bookkeeping, SBOM currency and the unit tests.
`python scripts/check_readiness.py --release` is expected to exit `2` (fail-closed) until the physical
acceptance work recorded in [REQUIREMENTS_TRACEABILITY.csv](REQUIREMENTS_TRACEABILITY.csv) is complete.

## Licence and third-party components

This application includes free and open-source software:

| Component | Licence |
|---|---|
| [UxPlay](https://github.com/FDH2/UxPlay) and android-airplay-server | GPL-3.0 |
| FFmpeg (audio decoding) | LGPL |
| libplist | LGPL |
| OpenSSL | Apache-2.0 |
| AndroidX, Jetpack Compose, Media3, Oboe, Kotlin, kotlinx.coroutines | Apache-2.0 |

Full licence texts and notices: [`legal/`](legal/), [`app/src/main/assets/OPEN_SOURCE_NOTICES.txt`](app/src/main/assets/OPEN_SOURCE_NOTICES.txt),
and inside the app under *Settings → About → Component licences*.

**Corresponding source.** The GPL-3.0 components are vendored in this repository together with the
patches applied to them ([`app/src/main/cpp/patches/UxPlay/`](app/src/main/cpp/patches/UxPlay/)), and
[`legal/REPRODUCE.md`](legal/REPRODUCE.md) plus [`docs/research/native-audit.lock.json`](docs/research/native-audit.lock.json)
record the exact upstream revisions and build instructions. If you distribute a built APK or AAB,
GPL-3.0 §6 requires you to make this corresponding source available to your recipients.

`tools/generate_sbom.py` produces [`legal/SBOM.spdx.json`](legal/SBOM.spdx.json) from the declared
dependency sources in this checkout.

## Verification status

Honest state of the 1.0.0 build:

- Verified: signed release APK and AAB (release key, not debuggable, both ABIs, 16 KB page alignment),
  a real iPhone mirroring session on a Xiaomi MiTV-AFMU0 (Android 14) with hardware HEVC decoding,
  audio/video start, and a 20-cycle connect/disconnect resource-leak measurement.
- **Not yet verified:** the physical acceptance items listed in
  [REQUIREMENTS_TRACEABILITY.csv](REQUIREMENTS_TRACEABILITY.csv) are all `NOT_RUN` — PIN flows,
  30-minute 720p/1080p sessions and the §19 performance thresholds, resolution/rotation measurements,
  discovery latency, running on a 16 KB page device, and the per-iPhone-version / per-TV-model matrix.
- The `arm64-v8a` slice is verified structurally (ELF64/AArch64, complete JNI symbols, alignment) but
  has not been executed on a 64-bit device.

## Contact

**Mahmut Enes Bozdoğan** — alquariz@yandex.com

Apple, AirPlay, iPhone, iPad and Mac are trademarks of Apple Inc. This application is not affiliated
with, endorsed by or sponsored by Apple Inc.

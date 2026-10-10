# Reproducible build and compliance handoff

This document records the current development build, not a release approval.

## Prerequisites

- Windows with Android SDK, CMake 3.22.1, NDK 29.0.14206865 and MSYS2 at
  `C:\msys64`
- A local Python interpreter. `scripts/build_receiver.ps1` resolves the user
  Python installation under `%LOCALAPPDATA%\Programs\Python` when no path is
  supplied.
- An ASCII-only temporary build path. The project directory itself may contain
  Turkish characters.

## Build

```powershell
# Android TV emulator
./scripts/build_receiver.ps1 -Abis x86_64 -BuildRoot C:\AynaTVBuild

# Android TV hardware packages
./scripts/build_receiver.ps1 -Abis arm64-v8a,armeabi-v7a -BuildRoot C:\AynaTVArmBuild
```

The native build compiles the checked-in UxPlay, FFmpeg, libplist and OpenSSL
sources. It does not copy a receiver APK.

## SBOM input and regeneration

```powershell
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
./gradlew.bat :app:dependencies --configuration debugRuntimeClasspath --no-daemon --console=plain |
  Tee-Object -FilePath artifacts/debugRuntimeClasspath.txt
python tools/generate_sbom.py
```

Before a release, regenerate from the release configuration and APK/AAB, attach
all Maven license metadata and notices, validate FFmpeg's exact configure flags,
and complete the provenance review. The current SBOM has `NOASSERTION` where
that evidence does not exist.

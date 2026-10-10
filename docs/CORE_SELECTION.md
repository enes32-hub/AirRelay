# AirPlay core selection

## Selected integration candidate

- Repository: [`jqssun/android-airplay-server`](https://github.com/jqssun/android-airplay-server)
- Pinned source: `c8defdd70d7e6a04f4f1b71d353653682d594106`
- License: GPL-3.0
- Target: a receiver that appears in the iPhone's built-in **Control Center → Screen Mirroring** flow on Android TV.

This candidate declares that it contains Android TV, JNI, UxPlay, MediaCodec/SurfaceView, audio and PIN structures. This declaration is not yet a device test by AirRelay. DRM-protected content such as the Apple TV app is outside the acceptance scope.

## Distribution outcome

No commercial SDK or paid license will be obtained. If AirRelay is distributed as a derivative of this candidate, the terms of GPL-3.0 apply: the corresponding source code, modifications, third-party notices and reproduction instructions are added to the delivery package.

## 31 August 2026 verification status

- The source and submodules were downloaded.
- The Android Gradle configuration was run for the x86_64 emulator.
- Compatibility adjustments for Windows path encoding, Python and patch tools were made only in the research/build copy.
- The Windows x86_64 spike was completed with Gradle, CMake, UxPlay, libplist, FFmpeg and OpenSSL. The OpenSSL 3.4.4 archive was verified with its official SHA-256 value; Windows/NDK r29 tool name differences were reconciled only in the temporary research build copy.
- A debug APK of `io.github.jqssun.airplay` was produced; it contains only the x86_64 emulator ABI and is not a package for a physical TV.
- The APK was installed on the Android TV emulator. The TV interface opened; the receiver was stopped and restarted with the remote's center button. The foreground service ran, the local receiver started, and AirPlay and RAOP mDNS records were published. No crash log was observed.
- The working core was moved into the main AirRelay `app` module. The old initial skeleton with no real functionality is kept under `prototype/initial-skeleton/`. The installed AirRelay package ID is `com.aynatv.receiver` and its display name is AirRelay Receiver.
- Windows-only native build adaptations are documented in the source. `scripts/build_receiver.ps1` copies the sources to an ASCII temporary working path and produces the x86_64 emulator APK there; this requirement is not a product or iPhone test.
- The main source integration was installed on the emulator as `com.aynatv.receiver`. The x86_64 native receiver consisting of UxPlay, FFmpeg, OpenSSL, libplist and JNI was built from clean sources with CMake/Ninja and this library was packaged into the APK. In the interface, the receiver standby state and the stop/start toggle on the D-pad center button were observed; after starting, AirPlay and RAOP mDNS records were advertised on port 7000. ARM32/ARM64 and 16 KB packages are again a separate verification gate.
- `make` is available in WSL Ubuntu, but Java and the Android SDK are not installed. Therefore a native Linux build could not be started in this session.
- The iPhone 14 Pro → Xiaomi TV Box 4K acceptance test will be performed in a single final stage after the daily emulator work, as per the user's preference.

This file is a record of selection and trials; it is not proof of real iPhone screen mirroring. The iPhone 14 Pro → Xiaomi TV Box 4K acceptance test is the mandatory final gate for video/audio/synchronization and connection verification.


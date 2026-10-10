# Development environment

31 August 2026 review. [Machine audit](../artifacts/environment-report.json) and official release verification are different kinds of evidence; an installed folder is not counted as proof of a working toolchain.

| Component | Research baseline | Source / status |
|---|---|---|
| Android SDK | min 26 / compile 37 / target 37 | API 37.0 revision 2 and android.jar found |
| AGP | 9.3.2 | [Official notes](https://developer.android.com/build/releases/agp-9-3-0-release-notes); includes the JDK 17 lint fix |
| Gradle | 9.5.0 | Same AGP compatibility table; there is no wrapper in the project yet |
| JDK | 17 | Microsoft JDK 17.0.20.1 was run |
| Kotlin | 2.4.10 | [Kotlin releases](https://kotlinlang.org/docs/releases.html) |
| Compose for TV | tv-material 1.1.0 | [Stable release table](https://developer.android.com/jetpack/androidx/releases/tv) |
| Media3 | 1.11.0 | [Official notes](https://developer.android.com/jetpack/androidx/releases/media3); ExoPlayer will not be used in place of the raw mirroring path |
| NDK | r29 / 29.0.14206865 | [Stable release](https://developer.android.com/ndk/downloads); installation with SDK Manager completed and a real build was performed for two ARM ABIs |
| CMake / Ninja | 3.22.1 / bundled with the SDK | Run on the original `toolchain-probe` C target for two ARM ABIs; the receiver core was not built |
| Build Tools | 37.0.0 | Folder found inside the SDK; AAB/APK verification was not performed |
| Android Studio | Quail 3 / 2026.1.3 Patch 1 | [Official release](https://developer.android.com/studio/releases); the IDE installation was not verified in this audit |
| Python | 3.12.13 | For this stage's research/QA tools with the standard library only |

[libs.versions.toml](../libs.versions.toml) is a fixed research baseline, not the effective dependency graph. No library was added to the application without a license review. The compatibility of the selected Kotlin/Compose plugin with AGP's built-in Kotlin support will be verified in Stage 1 through official guidance and a real build.

On the Android 17 target, network communication will not start without the [ACCESS_LOCAL_NETWORK](https://developer.android.com/privacy-and-security/local-network-permission) permission being granted; the API check and permission revocation will be tested separately. [16 KB page compliance](https://developer.android.com/guide/practices/page-sizes) is not confirmed automatically by the NDK version: ELF/ZIP alignment and a real 16 KB run test are required.

## Re-audit

```powershell
python scripts/environment_doctor.py --sdk "$env:LOCALAPPDATA\Android\Sdk"
```

On this machine `python` resolves to the Windows Store alias. The working local interpreter:

```powershell
& 'C:\Users\ASUS\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' scripts/environment_doctor.py
```

The tool does not install anything, does not accept the SDK license, does not connect to devices and does not install APKs. It does not write the identifiers from the ADB list into the report. The absence of a device connected to ADB does not mean there is no device at home; physical access has not yet been verified.

The NDK r29 installation is complete. Before Stage 1, the Gradle wrapper will be pinned with the official distribution hash, the version catalog will be bound to real dependencies, and dependency locking/verification metadata will be produced. These have not been reported as already done.

## Native toolchain trial

```powershell
python scripts/probe_toolchain.py --sdk "$env:LOCALAPPDATA\Android\Sdk"
```

[Real command results and hashes](../artifacts/toolchain-probe.json): the original small C library was compiled with CMake/Ninja + NDK r29 for `armeabi-v7a` and `arm64-v8a`. PT_LOAD alignment is 16384 in both ELFs; the `.so` data start offsets inside the ZIP are 16384 and 32768. The result was also cross-checked with the NDK `llvm-readelf` and the SDK `zipalign -c -P 16`.

The trial output is under `.research/toolchain-probe/` and is kept outside Git. `layout-16kb.zip` is not an installable APK; it contains no manifest and no application. `verify_native.py` checks the ELF/ZIP format; it does not provide evidence of APK signature, manifest, completeness of dynamic dependencies, decoder function, or running on a real 16 KB device. Native runtime acceptance and the audit of AirRelay release binaries remain open.

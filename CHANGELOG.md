# Work log

## 6 October 2026 — R39 pre-release review candidate

- End-user branding defaults, Compose samples and downloaded file names were made consistent with AirRelay; persistent package/JNI/preference identifiers were preserved.
- The microphone hardware requirement was removed; resource ownership and permission checking on recording audio startup failure were fixed.
- Pre-API 30 NetworkRequest compatibility was added for Android 7–10 network monitoring; PiP and recording remux API limits were stated explicitly.
- Android 13 permission is respected in notification updates; the media session exception is preserved. Foreground service startup behavior is not changed.
- Existing Media3 unstable API usages were marked with explicit opt-in; the playback core was not changed.
- Recording dates follow Compose's selected locale configuration. AAB language splitting was disabled so that all 16 languages remain in the offline selection.
- This candidate is test-signed; the real iPhone/TV final acceptance and the production-signed store package are separate steps.

## 1 September 2026 — Migration from the Windows prototype to the Android TV receiver

- The hybrid playback model verified in the iPhone 14 Pro session was ported to Android: MediaCodec video frames are released to the surface immediately by default, while audio stays on its own NTP timeline.
- Waiting for 256 packets on a missing sequence number in AAC-ELD's triple/redundant RTP stream was removed; in this stream the missing sequence is skipped, while ALAC and AAC-LC continue to use retransmission protection.
- RAOP `audio_flush` is no longer an empty callback. It discards old PCM, re-anchors the audio time, refills the jitter buffer and safely reopens the active Oboe output.
- Oboe low-latency mode was made the default for Android TV on new installations. The user setting can be changed from developer options.
- The dynamic UxPlay media ports that caused a control session to be established on the emulator without video appearing were pinned; the Android core and the Windows bridge now use the same contract over TCP `7000/7100` and UDP `7011/6001/6000`.
- The Android TV screen was renewed around the live mirroring focus. The music player, timeline, seeking and track controls were removed; ready/connecting/live states and iPhone guidance were added.
- The PIN window can no longer be closed before pairing is complete; the initial TV focus was moved to the `Overview` tab so that it does not accidentally select `Stop`.
- The x86_64 debug APK was successfully produced in clean and incremental builds, installed in place only on `emulator-5584`, and 44 regression tests passed.

## 31 August 2026

- The main specification was read; the user's scope of only an Android TV receiver and the iPhone system AirPlay sender was processed.
- The first target was set as the Stage 0 research/audit starter package.
- Three protocol candidates were examined with pinned commits and source file hashes; the core selection was left open.
- The Android TV architecture, toolchain, physical spike and performance measurement method were documented.
- 33 top-level P0 + 69 performance/network acceptance records and 58 test definitions were created. No acceptance result was set to PASS.
- Environment/provenance/record consistency tools and 24 regression tests were added.
- The preparation record check passed; the release check was rejected as expected due to missing P0 and Stage 0 gates.
- This work was not presented as a working screen mirroring product or a completed Stage 0.

## 31 August 2026 — Continuing toward the final objective

- The active objective was preserved as the final application and verified delivery package; the preparation objective does not replace it.
- The user's iPhone 14 Pro and Xiaomi TV Box 4K device pair was recorded; model/OS details were not assumed.
- 185 files from seven pinned upstream repositories were inventoried with hashes and license signals. The FairPlay legal uncertainty declaration for the UxPlay/RPiPlay sources was verified.
- NDK r29 was installed. The original C tool was compiled with CMake/Ninja for two ARM ABIs; the 16 KB ELF/ZIP format was verified with a Python check, llvm-readelf and zipalign. No receiver or physical device test was considered performed.
- Native inventory and ELF/ZIP inspection tools were added; the preparation regression tests rose to 44 and passed.
- Core provenance, physical connection and real application build/acceptance gates remained open. No P0 product record was set to PASS.
- The primary documents of two additional open repositories and two commercial SDKs were examined; the core was not selected due to proprietary modules and undocumented usage rights. It was stated that MFi audio coverage does not prove screen mirroring SDK access.
- A tool that reads limited technical information from a single explicitly selected ADB device and a TV connection guide were added. The tool rejects the emulator; it does not produce automatic connection, application installation or an acceptance result.
- The user's preference not to use a paid SDK was recorded. The open source/GPL route continues to be examined; no APK, code or asset will be taken from commercial end-user applications.
- An Android TV application skeleton was added: a pinned Gradle wrapper, Leanback launcher, D-pad initial focus, foreground ReceiverService and a local status screen. The debug APK was installed/launched on the emulator; the foreground service was verified with D-pad OK. No AirPlay core or iPhone stream was added.
- A four-digit PIN was added to the receiver session with `SecureRandom`. The PIN is shown on the TV screen only in the running session; it is cleared from memory when the receiver stops. The start/display/stop flow was verified on the emulator; the PIN was not written to the evidence file.
- A MediaCodecList-based H.264/AVC capability query was added. The application shows the hardware and low-latency features of the suitable decoder only from the device's real codec report; no video package or software fallback was added.

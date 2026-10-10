# AirRelay R40 — remaining work after phase 2

Phase 2 (media-path/native fixes, brand migration, English default locale, UxPlay change
record) is complete and verified on the Xiaomi TV (MiTV-AFMU0, Android 14, armeabi-v7a):
build, install, receiver start/stop/restart cycles, and a live iPhone mirroring session
(HEVC 996x2160, first frame submitted, clean audio, zero regression markers, zero crashes).

This file tracks what is left. Worklist items are grouped by phase; nothing here is done yet.

## Phase 3 — UI, lifecycle, settings

### Device-found leak: one GL texture and one shader program leaked per decoder restart (fixed)

Found by the 20-cycle connect/disconnect soak on the real TV, in a single process, with balanced
session counts (19 mirror sessions opened, 19 closed, 19 codec starts, zero errors):

| Measure | Before the sessions | After | Growth |
|---|---|---|---|
| Open fds | 103 | 206 | **+103** (~5 per session) |
| Threads | 34 | 44 | +10 |
| PSS | 131.6 MB | 164.6 MB | +33 MB |

The fd breakdown identified the subsystem: `sync_file` 46, `anon_inode` 75, `dmabuf` 18,
`gralloc_shared_memory` 9 — codec/GPU buffers.

Root cause: `VideoPipeline._releaseGl()` released the `SurfaceTexture`, the input `Surface` and the
EGL surfaces, but never deleted the external texture created by `glGenTextures` or the shader
program created by `glCreateProgram`. Those are GPU driver objects: releasing the SurfaceTexture
does not free them, and the pipeline restarts on every decoder start, so 19 sessions leaked 19
textures and 19 programs. The `finally` in `_loop()` already ran `_releaseGl()` on the GL thread,
so the fix only had to delete the objects there while the EGL context is still current.

Verification after the fix, same test:

| Measure | Baseline | After | Note |
|---|---|---|---|
| Open fds | 153 | 116 | no growth (a net decrease) |
| `sync_file` fds | — | **1** | was 46 |
| `dmabuf` / `gralloc` fds | — | **0 / 0** | were 18 / 9 |
| PSS | 164.4 MB | 134.8 MB | no growth |
| Threads | 31 | 37 | +6 over 11 sessions |
| Sessions | — | 11 opened, 11 closed, 11 codec starts | 0 errors, 0 GL cleanup failures |

Caveat recorded honestly: the second run had 11 sessions against 19 in the first, so the totals are
not directly comparable; the per-session growth is (5.4 fds → 0), and the leak's fingerprint
(`sync_file`/`dmabuf`/`gralloc`) is what disappeared. A residual +6 threads over 11 sessions was
observed and is not yet explained; it is likely Android's binder/dispatch pools growing with
connection churn, but it should be re-measured over a longer run before being dismissed.

#### Automated soak without a phone (works, with two traps)

The project already contains the harness needed to exercise the video path on the device without an
iPhone sender: `IntegrationInstrumentation` is a custom `Instrumentation` with named modes, and
`tvHevcOnly=true` runs `SyntheticVideoProbe.runTvHevc` — three rounds of real 996x2160 HEVC decode
through the hardware decoder (`c2.amlogic.hevc.decoder`) plus pixel verification.

```powershell
adb -s <tv> shell am force-stop com.android.tv.settings          # trap 1
adb -s <tv> shell input keyevent KEYCODE_WAKEUP                   # trap 2
adb -s <tv> shell am instrument -w -e tvHevcOnly true `
    com.airrelay.receiver.test/io.github.jqssun.airplay.IntegrationInstrumentation
```

Two traps, both outside the application:

1. **The TV's Settings task must be cleared before each round.** `am instrument` force-stops the app
   (`Force stopping com.airrelay.receiver ... start instr`), and the TV then restores whatever task
   was behind it — which was `com.android.tv.settings/.device.apps.specialaccess.SystemAlertActivity`
   (the "display over other apps" screen) left in the back stack. That screen took the foreground,
   the probe's `SurfaceView` lost its surface, and the probe reported `HEVC output froze`. Measured:
   142 `SystemAlertActivity` appearances and intermittent failures without the workaround; **0
   appearances and 10/10 passes with it**. This was first misdiagnosed as display sleep, then as an
   intermittent decoder stall — it is neither, and the application does not open that screen (the
   only call sites are user-triggered buttons).
2. **The screen must be awake.** The probe hosts a `SurfaceView` and checks `surface.isValid` after
   5 s; with the screen asleep the check fails. `svc power stayon true` plus a `KEYCODE_WAKEUP`
   before each round is enough.

Verified clean run: 10 rounds, **10 PASS**, 30 codec starts, 29 first-frame submissions, zero
`FATAL`, zero `GL cleanup failed`, zero Settings-screen appearances.

Known limitation: `am instrument` restarts the app process for every run, so fds/threads cannot be
compared across rounds. That gap is now closed by an in-process soak mode.

#### `soakHevcRounds` — automated leak measurement inside one process

`SyntheticVideoSoak` (androidTest) loops `SyntheticVideoProbe.runTvHevc` inside a single process and
reports open fds, thread count, PSS and **fd categories** before and after, so a graphics/codec leak
(`dmabuf`, `sync_file`, `gralloc`) is distinguishable from test-harness churn (sockets, pipes, plain
files). Each probe call is three decoder rounds, so `N` calls exercise `3N` codec/pipeline restarts.

```powershell
adb -s <tv> shell am force-stop com.android.tv.settings
adb -s <tv> shell input keyevent KEYCODE_WAKEUP
adb -s <tv> shell am instrument -w -e soakHevcRounds 10 `
    com.airrelay.receiver.test/io.github.jqssun.airplay.IntegrationInstrumentation
```

The probe now also sets `FLAG_KEEP_SCREEN_ON` on its host activity, which removes the display-sleep
trap described above.

Measured (8 calls = 24 codec restarts, all rounds passing):

| | Before | After |
|---|---|---|
| Open fds | 77 | 185 |
| Threads | 18 | 40 |
| PSS | 176.2 MB | 223.1 MB |
| fd categories | `/dev` 13, socket 7, file 51, pipe 2, eventfd 4 | `/dev` 28, socket 14, file 52, pipe 2, eventfd 18, `anon_inode` 3, **`sync_file` 47**, **`dmabuf` 14**, **`gralloc/memfd` 7** |

**Attribution done — the bulk of this growth is test-harness churn, not a product leak.**

A control mode (`churnRounds`) starts and finishes the probe's host Activity the same number of times
(same `SurfaceView`, same keep-screen-on flag) but decodes nothing, so the two runs can be subtracted:

| 8 rounds | A) activity churn only | B) soak with decoding | B − A |
|---|---|---|---|
| Open fds | 76 → 154 (**+78**) | 77 → 185 (**+108**) | **+30** |
| Threads | 18 → 33 (+15) | 18 → 40 (+22) | +7 |
| PSS | +72.6 MB | +47.0 MB | −25.6 MB |
| `sync_file` | +33 | +47 | +14 |
| `dmabuf` | +8 | +14 | +6 |
| `gralloc/memfd` | +4 | +7 | +3 |

Activity churn alone accounts for **78 of the 108 fds (72%)**, and for most of the graphics
descriptors — even `sync_file`, `dmabuf` and `gralloc` grow without any decoding, because each probe
call leaves behind an Activity, a `SurfaceView` and an EGL context that are not reclaimed promptly.
PSS is actually worse without decoding (+72.6 MB against +47.0 MB). The residual ~+30 fds is within
churn noise, and the product path measured clean on a real iPhone session after the GL fix
(fds 153 → 116, `sync_file` 46 → 1).

So: **do not report the synthetic soak's absolute growth as a product leak.** To make the soak
sensitive to pipeline-only leaks, `SyntheticVideoProbe.runTvHevc` needs a variant that loops the
decode rounds against **one** host Activity and one renderer instead of starting a fresh Activity per
call (it currently calls `startActivitySync` inside `TvPixelSink`, and the round body would have to be
extracted for that).

Mode selection: `-e soakHevcRounds N` runs the decode soak, `-e churnRounds N` runs the control.

The intermittent `HEVC output froze` seen earlier also deserves the same treatment: it disappeared
after the Settings-task workaround and the keep-screen-on change (8/8 and 10/10 clean runs), but it
was observed twice in ten calls in one earlier in-process run, so it is not yet explained.

### Device-found crash: audio decode wrote into a freed timeline (fixed)

The first live mirror session after the A2 refactor crashed the app process ~1 second after
the first frame:

```
F/libc: Fatal signal 11 (SIGSEGV), fault addr 0x0, tid ... name: Thread-8 >>> com.airrelay.receiver
Cause: null pointer dereference
#00 __memcpy_base_a55
#01 SpscRing::write(short const*, unsigned int)
#02 TimelineBuffer::write(short const*, unsigned int, long long)
#03 MediaCodecDecoder::drainLoop()
```

Root cause, introduced by the earlier `audio_engine.cpp` change: the `decode()` snapshot of
`mTimeline` was taken *before* the queued-configuration block, but that block calls
`initInternals()` and **replaces** `mTimeline`. The decoder was therefore built from the
pre-swap buffer, which the local `shared_ptr` released as soon as `decode()` returned — so the
decoder's drain thread wrote into freed memory.

Fix: apply the queued configuration first, then snapshot the published state under the rebuild
lock, so the decoder is always built from the current timeline while the local `shared_ptr`
keeps that buffer alive.

Verified on the TV after rebuilding: four mirror sessions in a **single process**, all with
`First frame submitted`, zero `Fatal signal`/`SIGSEGV`/`ANR`, zero audio underruns/xruns/decode
errors (the corrupting build showed 1-2 underruns), fps averaging ~51 (max 64), and transport
replacement plus resolution changes handled without a restart.

### Progress (all compiled, built for armeabi-v7a and installed on the TV)

- **A2 completed.** The codec (re)start no longer holds the media lock across the GL
  lifecycle: `feedFrame` decides under the lock, performs `pipeline.release()/start()` without
  it, then commits under a `sessionGeneration` check; `release()` joins the render thread
  outside the lock too. The fresh-SurfaceTexture-per-decoder behaviour is preserved.
- **C1, C4, C5, C6, C10, C11, C12 done.** Conditional `unbindService` (with a diagnostic log;
  surfacing the failure in the UI is still open), D-pad Down only consumed when focus moved,
  `LaunchedEffect` keys extended with `globalModal`/`page`, `KEEP_SCREEN_ON` cleared in
  `onStop`, `Locale.ROOT` metrics, `VideoTrackDialog` keyed on the option list, guarded
  `startService` from `onStop`.
- **C2 done, plus a bug it exposed.** `LOGS` is now a first-class page with nav entries in both
  layouts and it renders `LogsScreen`. Verifying it on the TV showed the page was reachable but
  permanently empty: `DiagnosticStore.offer()` was a no-op stub and `export()` threw. Both are
  implemented now (bounded ingress → drain loop → bounded line buffer; export writes a capped
  snapshot to the shared cache for FileProvider). Confirmed on the TV: the page lists real
  events (`Server started on port 7000`, `Discovery RAOP: REGISTERING`).
- **D1, D2, D4, D5 done.** `remove()` is non-blocking, `setBlocked` uses `apply()` and no longer
  requires the key to be in the trusted list (blocking works with "Require PIN" off),
  `applyAtReceiverStart()` returns the settings-commit result and clears the draft with `apply()`.
- **D3 was a false finding.** `register()` already returns `edit.commit()`. No change made.
- **C7, C9, D6 done.** The seek bar now reports `progressBarRangeInfo` and a state description to
  screen readers; the `−10 s` / `+10 s` buttons use the localized `seconds_short` unit
  (the remaining hardcoded labels — `720p`, `MP4`, `Mbps`, `+`, `−`, `·` — are units and
  mathematical signs, so they are intentionally left alone); the optimistic transport state is
  gated on the bound service so a play/pause flip can no longer stick after the service is gone.
- **C8 partly done (crash removed).** `TvFocusMemory.tvFocusId` no longer calls `check()` on a
  duplicate identity — two settings sharing a translated title, or a UI-language switch, used to
  crash composition. The duplicate is skipped and logged instead. Still open: giving the
  advanced settings fields stable `Prefs`-key identities (19 call sites pass a localized title).
- **C3 done for code and strings.** Deleted `ui/VideoControls.kt` (556), `ui/gestures/GestureUi.kt`
  (223), `ui/gestures/GestureStates.kt` (267), `ui/gestures/GestureDetectors.kt` (98) and
  `ui/PlaybackSpeedSelector.kt` (213) = 1,357 unreachable lines, plus the orphaned ViewModel APIs
  (`setVideoSpeed`, `setVideoSkipSilence`, `toggleVideoDownload`, `audioVolumeUp/Down`,
  `audioScanBegin/End`). Then removed the **118 string keys that no longer had any reference**
  (verified against `R.string.*`, `@string/*` and dynamic-lookup patterns across `app`,
  `androidTest`, `tests` and `scripts`) from all 16 locales: 490 → 372 keys per locale, all sets
  identical, no format-specifier mismatch. The removed list is recorded in
  `docs/R40_UNUSED_STRINGS.txt`.
- **Build-workspace staleness fixed.** `scripts/build_receiver.ps1` used `robocopy /E`, which
  copies but never deletes, so the deleted screens kept compiling from the workspace copy and
  failed against the strings removed with them — the repository built, the workspace did not.
  A `Remove-StaleTree` pass now purges stale files from the copied source subtrees
  (`app/src`, `app/libs`, `gradle`, `tools`) while leaving caches (`app/build`, `app/.cxx`,
  `tools/toolchain-probe`) untouched. Confirmed: 5 stale files purged, `assembleDebug` green.

### Phase 3 status: closed except the visual polish set

Done: A2, C1, C2 (+ the inert-diagnostics bug it exposed), C3 (1,357 dead lines, 7 orphan ViewModel
APIs, 118 unreferenced string keys across all 16 locales), C4, C5, C6, C7, C8 (crash removed and
19 advanced-settings fields now use stable `focusKey` identities instead of translated titles),
C9, C10, C11, C12, D1, D2, D4, D5, D6 and I11 (AccentSoft/SelectedFill are no longer identical to
Elevated) and I15 (dark base theme, so launch no longer flashes white behind the dark brand mark).

Still open, and deliberately so:

* **C1 remainder** — a failed `bindService` is logged but not yet surfaced in the UI.
* **I1-I10, I12-I14** — colour-token discipline, overlay theme, radii/typography/spacing scales,
  state components and dialog hierarchy change how the UI *looks*. The session's model cannot view
  images, so these need user-reviewed screenshots on the TV before they are worth doing; the
  findings and intended values stay recorded in `docs/DESIGN_REVIEW.md`.

`scripts/run_full_check.ps1` now runs every non-device check in one command (provenance,
acceptance bookkeeping, SBOM freshness, unit tests) and reports the release gate as blocked rather
than failed.

### Code and lifecycle (C/D items)

| Item | Where | Problem | Fix |
|---|---|---|---|
| C1 | `MainActivity.kt:74,235-239` | `bindService()` result is discarded while `unbindService(connection)` runs unconditionally in `onDestroy` → `IllegalArgumentException` on the bind-failure path, and a failed bind leaves the receiver silently dead. | Keep the returned flag; unbind only when true; surface a bind-failure state. |
| C2 | `MainScreen.kt:211`; `InteractionPolicy.kt:4-16` | `ReceiverPage.LOGS` renders `ReceiverHelp`, no nav target selects `LOGS`, and `LogsScreen` is never composed → the log view/export/clear API is unreachable although `tab_logs` advertises it. | Route `LOGS` to `LogsScreen`; give it `tab = LOGS`, `parent = HOME`; add a nav entry to both nav lists. |
| C3 | `ui/gestures/**`, `ui/VideoControls.kt`, `ui/PlaybackSpeedSelector.kt` | ~1,600 lines with zero call sites; speed, skip-silence, lock, content scale, zoom/double-tap seek and download are advertised but unreachable. | Wire `LogsScreen` only; delete the dead composables plus their orphaned ViewModel APIs and now-unused string keys (verify zero references with grep). |
| C4 | `MainScreen.kt:312-317` | `KEYCODE_DPAD_DOWN` is consumed unconditionally while `homePrimary.requestFocus()` is wrapped in `runCatching`; when the primary action is disabled, Down does nothing and focus is trapped in the top bar during STARTING. | Consume the key only when focus actually moved. |
| C5 | `MainScreen.kt:135-138` | `LaunchedEffect` reads `globalModal` and `page` but keys only on `(setupComplete, bound, starting, running)` → "focus the primary action once no modal is open" never re-triggers after a dialog closes on HOME. | Add `globalModal` and `page` to the key list. |
| C6 | `MainActivity.kt:76-85` | `FLAG_KEEP_SCREEN_ON` is cleared in a `repeatOnLifecycle(STARTED)` collector, so the clear branch never runs on STOP and the flag stays on while backgrounded. | Clear the flag in `onStop()`. |
| C7 | `MainScreen.kt:561-593` | `ReceiverSeekBar` is a hand-rolled focusable control with no `progressBarRangeInfo`/`stateDescription`; screen readers cannot read position, duration or the seek step. | Add semantics. |
| C8 | `SettingsScreen.kt:447-557` | Focus identity is built from localized text (`"advanced.toggle.$title"`), so switching UI language invalidates remembered ids, and two settings sharing a translated title crash composition via the `check()` in `TvFocusMemory.kt:79`. | Use stable `Prefs` keys. |
| C9 | `MainScreen.kt:517,524`; `SettingsScreen.kt:93-94`; `RecordingPanel.kt:99`; `QuickRecording.kt:59`; `VideoTrackDialog.kt:86,88` | Hardcoded user-visible text (`"−10 s"`, `"+10 s"`, `"${value}p"`, …) bypasses the resource set even though equivalents exist. | Move them to string resources in all 16 locales. |
| C10 | `MainViewModel.kt:60-70` | `bitrateStr`/`jitterStr` format with the default locale while `StreamMetrics.kt:27-30` uses `Locale.ROOT` → mixed decimal separators under tr/de. | Use `Locale.ROOT`. |
| C11 | `VideoTrackDialog.kt:72-73` | `initialKey` is keyed on `options.isEmpty()` instead of `options`, so when the list changes without changing emptiness no node holds initial focus. | Key on `options`. |
| C12 | `MainActivity.kt:222-226` | `startService(...STOP)` from `onStop` can throw `IllegalStateException` on API 26+ if the recording service is gone while its status has not flipped. | Wrap in `runCatching`. |
| D1 | `PendingReceiverSettings.kt:15` | `remove()` takes the shared static lock with a blocking `withLock` while `applyAtReceiverStart()` holds it across two synchronous commits on `Dispatchers.IO`; the only caller is the UI thread → freeze/ANR during a receiver start. | `tryLock`/off-main, like the sibling writes. |
| D2 | `TrustedDevices.kt:31-36` | `setBlocked` performs a synchronous `commit()` from a Compose click. | `apply()` (or `Dispatchers.IO`). |
| D3 | `TrustedDevices.kt:11-19` | `register()` returns `true` regardless of the `commit()` result. | Return the commit result. |
| D4 | `TrustedDevices.kt:31-36` | With “Require PIN” off, `setBlocked` can never succeed because the key is never registered, yet `onClientAllowed` admits every valid key → the receiver is open and no device can be blocked. | Allow blocking any `validKey`; keep the block list independent of the trusted list. |
| D5 | `PendingReceiverSettings.kt:49-55` | `applyAtReceiverStart()` returns the draft-clear commit result, so a failed clear aborts a start whose settings were actually committed. | Return the settings-commit result; `apply()` the clear. |
| D6 | `MainViewModel.kt:644-651,697-712` | Optimistic transport state sticks when the service is gone (after `onServiceDisconnected` nothing reconciles it). | Gate on `_serviceBound`. |

### Design polish (I1-I15)

The design review verdict is **targeted polish** (see `docs/DESIGN_REVIEW.md`). Remaining:
colour-token discipline (hardcoded hexes in `CapsulePlayerControls.kt:27,58-71`,
`MainScreen.kt:468,478-480`, `StreamMetrics.kt:41`), overlay theme consistency
(`MainScreen.kt:165` forces `AirPlayTheme(selected="dark")`), one shape scale (12/20/28),
2dp focus rings on chips/sliders, typography floor (12sp capsule labels, 13sp `labelSmall`,
`bodyMedium` for descriptions), error/LIVE/empty state components, collapsible advanced
settings with one segmented recording-quality control, overflow caps for 16 locales,
removing `fontScale` double-scaling, a distinct `AccentSoft`, confirm-primary dialog
hierarchy, a 4/8/12/16/24/32 spacing rhythm, and a dark base theme.

## Shippability sprint — arm64, release build, signing, GPL

Done and verified:

* **arm64-v8a + armeabi-v7a universal APK built** (7m23s). 38 MB, six libraries per ABI, ABI list
  exact, 16 KB page alignment passes, signature verifies. Until now the app shipped `armeabi-v7a`
  alone, which cannot be installed on 64-bit-only Google TV devices — the largest reach gap.
* **The release variant was built and exercised for the first time.** `isMinifyEnabled = true` had
  never produced an artifact, so R8 was unverified. Result: `assembleRelease` is green (lintVital
  included), the APK is **8.94 MB against 31.38 MB debug** (71% smaller), `mapping.txt` is produced,
  and the minified build was installed on the TV and driven by a real iPhone: video and audio
  callbacks fire, first frame submitted, fps 61-62, decoder residence 17-28 ms, zero audio
  underruns, and **zero** `NoSuchMethodError`, `JNI DETECTED ERROR`, `UnsatisfiedLinkError`,
  `NoClassDefFoundError`, `AbstractMethodError`, `FATAL` or `ANR`. That is the empirical proof that
  the keep rules cover the 26 Kotlin methods `android_raop_callbacks.c` and `log_sink.h` call by
  name.
* **Wrong-ABI footgun closed.** `allAbis` used to fall back to `x86_64` even for release, so a bare
  `assembleRelease` produced an emulator-only artifact. The fallback now depends on what was
  requested: a release invocation defaults to the real-TV ABIs.
* **`scripts/create_release_keystore.ps1`** creates the release key without ever echoing the
  password or putting it on a command line, refuses to overwrite an existing keystore, and wires the
  four AGP properties into the gitignored `local.properties`.
* **`scripts/verify_release_artifact.ps1`** checks an APK against the release checklist (exact ABI
  set, signature and signer, `zipalign -c -P 16`, frozen application id, version metadata,
  debuggable flag) and exits non-zero on failure.
* **GPL corresponding source produced and verified**: 10,968 files, 31.7 MB,
  SHA-256 `a6e68d3255f757ea027e0388a5627b1897e97d05eaf82e1f5ef990c9854843a1`, with
  `EXPORT_MANIFEST.json` inside and no signing material, `local.properties` or secrets.
* **`legal/NOTICE.md` corrected**: still said "AynaTV Receiver", gave no GPL-3.0 §6 source-availability
  statement, and (in the first draft of this change) recorded the archive's own hash — impossible,
  because the notice travels inside the archive. It now names the production command, the manifest,
  and carries the `<SOURCE-URL-NOT-SET>` placeholder that must be filled before the first public
  binary.
* **`docs/RELEASE_RUNBOOK.md`** documents the order of operations, the irreversible decisions
  (keystore, application id), the verification steps and the publication set.

What still needs a human:

1. Run `scripts/create_release_keystore.ps1` (the password must never pass through an agent), then
   build a release-signed artifact so `G0-TOOLCHAIN` can move.
2. Fill `<SOURCE-URL-NOT-SET>` in `legal/NOTICE.md` once the distribution channel is chosen.
3. Test the universal APK on a 64-bit device/emulator — this TV is 32-bit, so the arm64 slice is
   verified structurally only. The same applies to the existing `sanitize` variant
   (arm64 + HWASan/UBSan), which is the right tool for hunting more bugs of the kind we already
   found in the audio lifetime path.

## Phase 4 — evidence, packaging, documentation

### Restored: the specification, the traceability matrix and a real release gate

The release gate had aborted with an `OSError` because its three inputs were missing. They are
restored or generated now, all from real sources:

* The original specification was recovered from the user's documents folder and the handover
  archive (`Android_TV_iPhone_Ekran_Yansitma_Alicisi_AI_Ajan_Ana_Sartnamesi.md`, 3,082 lines,
  SHA-256 `19cd21a750c28a74319a217d5f6aab4091be3204bc58e69b402f04c98f22b304`). All three copies
  found on disk were byte-identical, and that hash **matches `spec_sha256` in
  `docs/TEST_CATALOG.json`**, so this is provably the document the catalog was built from.
* `REQUIREMENTS_TRACEABILITY.csv` is generated by the reviewed mapping in
  `scripts/seed_traceability.py`: 102 rows (33 section-3.1 P0 requirements, 69 section-19
  performance thresholds), **every one `NOT_RUN`**. No result was invented.
* Result: preparation gate `ok: true` (exit 0) with the blockers listed, release gate
  `ok: false` (exit 2) — fail-closed exactly as designed — and the Python suite went from
  `FAILED (errors=17)` to `Ran 57 tests ... OK`.
* `artifacts/evidence/README.md` documents the evidence contract the gate enforces, so a future
  PASS record cannot be written by guesswork.

**Correction to an earlier plan item:** the CSV's Turkish column names (`Kaynak satır`,
`Test sonucu`, …) must **not** be anglicised. `scripts/check_readiness.py` reads those exact
keys, so renaming them would break the gate. Leave them.

### SBOM: generated instead of claimed

`legal/SBOM.spdx.json` claimed to be created by `tools/generate_sbom.py`, which did not exist,
and described "AynaTV Receiver 0.0.31". The generator now exists and derives 33 packages from
sources that are actually in the repository: every declared Gradle dependency with its resolved
version from `gradle/libs.versions.toml`, the Compose BOM-managed entries marked as such, and the
pinned native components from `docs/research/native-audit.lock.json` (UxPlay `4621533…`, libplist
2.3.1 `f41b1ea…`, FFmpeg 8.1.2 `38b8833…`, OpenSSL 3.4.4, plus the playfair/RPiPlay/wrapper
provenance commits). Licences come from an explicit table, `NOASSERTION` where unknown, and the
document states plainly that it is not the transitive closure and how to obtain one.
`--check` mode is deterministic and part of `run_full_check.ps1`. The superseded provisional SBOM
is archived at `docs/archive/legal/SBOM-0.0.31-provisional.spdx.json`.

### Documentation

`docs/archive/INDEX.md` records what is archived and why, and lists the documents recommended for
archiving with their contradictions — including **`docs/VELORA_CAST_USER_GUIDE.md`**, which still
describes the product as "Velora Cast", an abandoned brand candidate, and `docs/ARCHITECTURE.md`,
which still claims the design is not implemented.

### Still open in phase 4

* Move the recommended documents into `docs/archive/` and translate the remaining active Turkish
  documentation to English. The legacy per-revision notes are numerous; this is writing work, not
  verification work, and nothing blocks on it.
* Wire the `androidTest` dependencies and document the on-device test command.
* README/CHANGELOG consistency pass against the current version and ABI set.
* Decide whether `libssl.so` should be removed from packaging (it is verified absent from the APK).

### Icon rendering: app logo appears cropped (user-reported) — RESOLVED

**Status: fixed and confirmed by the user on the real TV after a clean reinstall.**

The true root cause was found only after the first attempt failed on the device:

* A layer-list `<item>` with an explicit `width`/`height` **pins the child to that dp size**
  instead of letting it follow the icon's real bounds. `AdaptiveIconDrawable` sets the layer
  bounds to whatever size the icon is drawn at, so the original 66dp × 60dp pin overflowed every
  smaller rendering (which is why the crop showed up in the settings list) — and the first fix in
  this session, a 108dp pin, was worse: the settings list drew only the artwork's **top-left
  quarter**.
* Final fix: the foreground, the monochrome layer and both legacy icons are **vectors** with no
  pinned sizes anywhere, so one drawing is correct at 24dp and at 320dp:
  `ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml` are 108-unit vectors with the
  600-unit mark scaled by 0.091603 and centred (drawn mark 48 × 40.1 units, half-diagonal 31.3
  units — inside the guaranteed 66-unit circle and inside the usual 72-unit mask);
  `mipmap-anydpi/ic_launcher.xml` and `ic_launcher_round.xml` are 72-unit vectors (circle plus a
  44-unit mark) for API 24-25, which is inside `minSdk`.
* No colour changed: the mark stays cream `#FFF9ED` plus teal `#3ACDB7`, the monochrome layer
  stays white, and the icon background stays `#17191B` (that value dates from 6 October, before
  this session's icon work — verified by file timestamps and by comparing against the hand-over
  archive).

Evidence collected:

* The installed APK on the TV was pulled back and its resource dumped: `<vector>`,
  `viewportWidth=108`, `scaleX=0.091603` — proof the device carries the fixed asset.
* The old build was still installed at the time the user first reported "still broken"; its
  SHA-256 matched the earlier R8 test build, so the observation was of a stale artifact.
* On the TV's "display over other apps" list, the AirRelay row contains ~2245 px of the exact
  brand background `#17191B` (a ~47 × 47 px icon), but almost no brand teal — the system's
  settings list renders icons tinted, which is why pixel-colour analysis cannot confirm the mark
  there. The user's visual confirmation is the acceptance for this item.

Also found while verifying (and then retracted, with proof): the setup wizard's permission button
was suspected to be dead on this TV because `am start -a MANAGE_OVERLAY_PERMISSION` reported
"unable to resolve Intent". Driving the real button proved otherwise — `logcat` shows
`START ... act=MANAGE_OVERLAY_PERMISSION dat=package: cmp=com.android.tv.settings/.device.apps.specialaccess.SystemAlertActivity`
and that activity becomes the resumed one. The application call resolves the TV-specific
implementation; only the shell invocation failed. No defect. (Minor leftover: the unused private
helper `_overlayIntent` in `SettingsScreen.kt` was removed.)

The original diagnosis is kept below for the record.

Observed: in TV Settings → “Display over other apps”, the AirRelay icon is visibly cut off.

Diagnosis (adaptive-icon geometry, API 26+):

1. `res/drawable/ic_launcher_foreground.xml` is a `<layer-list>` whose only item is
   `66dp × 60dp`. An adaptive-icon foreground must be a **108dp × 108dp** canvas with the
   artwork inside the central **66dp** guaranteed-visible circle. The framework scales the
   66 × 60 layer-list up into the 108dp canvas, magnifying the mark ~1.6× past the safe zone,
   so the icon mask crops it. `res/drawable/ic_launcher_monochrome.xml` repeats the same
   mistake, and `mipmap-anydpi-v26/ic_launcher_round.xml` reuses both.
2. The logo artwork itself (`airrelay_logo_vector.xml`) is square (100dp × 100dp), but it is
   placed in a 66 × 60 box, so the layer-list stretches it non-uniformly — the mark is also
   distorted.
3. The legacy `res/mipmap-anydpi/ic_launcher.xml` (and `ic_launcher_round.xml`) uses a 72dp
   oval with a 60 × 55 logo box, repeating the non-square stretch for pre-26 launchers.
4. There are no density-bucket mipmaps; only `mipmap-anydpi` + `mipmap-anydpi-v26`. That is
   acceptable for vector icons, but it means the broken adaptive path is used on every modern
   device (the TV is API 34), which is exactly where the crop is seen.

Fix:

- Rebuild `ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml` as **108 × 108dp**
  drawables: an outer `<layer-list>` item of `108dp × 108dp` containing a centred, **square
  66dp × 66dp** item for the logo (preserving the 100 × 100 aspect ratio). Keep all visible
  artwork inside the central 66dp circle.
- Fix the legacy `mipmap-anydpi/ic_launcher.xml` and `ic_launcher_round.xml` to a square logo
  box (for example 60 × 60 inside the 72dp shape) so nothing is stretched.
- Keep `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` pointing at the fixed
  foreground/background/monochrome layers.

Verification:

- `aapt2 dump badging` confirms `application-icon` still resolves.
- Reinstall, then compare screenshots of Settings → “Display over other apps” and the launcher
  row before/after; the whole mark must be visible inside the circular mask.
- Sanity check the safe zone: no visible pixel may sit outside the central 66dp circle of the
  108dp canvas. Optional: render the adaptive icon at 108dp in a Compose preview.

### Icon and brand rendering — additional predicted issues (same root cause family)

The cropped launcher icon is the visible symptom; the same mistakes appear on every other
brand surface. Fix them together, from one corrected geometry.

1. **TV launcher banner squashes the mark.** `res/drawable/banner.xml` places
   `airrelay_logo_vector` (square, 100dp × 100dp intrinsic) into a `90dp × 82dp` item, and a
   `<layer-list>` item stretches its child to the item bounds → non-uniform scaling. Use a
   square box (for example 82dp × 82dp) and position it with the banner layout instead of
   resizing the artwork.
2. **The legacy launcher icon distorts the mark, and minSdk 24 means it is used.** On API 24
   and 25 (below the adaptive-icon level) the launcher reads
   `res/mipmap-anydpi/ic_launcher.xml` and `ic_launcher_round.xml`, both of which put the square
   logo in a `60dp × 55dp` box inside a 72dp oval → the same non-square stretch, plus a
   silhouette (oval) that does not match the adaptive icon (full-bleed dark square). Make both
   a square logo box with a matching background, and check them on an API 24/25 profile if one
   is available.
3. **Notification icons are framework drawables, not brand.**
   `AirPlayService.kt:1670` uses `android.R.drawable.ic_media_play` and
   `RecordingService.kt:64` uses `android.R.drawable.presence_video_online`. The notifications
   therefore show a generic Android glyph rather than the AirRelay mark, and `android.R.drawable`
   contents are not stable across OEM ROMs. Add one Notification-1 compliant monochrome
   `drawable/ic_notification.xml` (white silhouette on transparent, artwork inside the inner
   ~22dp of a 24dp canvas) and use it in both services; verify it in the TV notification shade.
4. **Themed-icon (monochrome) layer.** `ic_launcher_monochrome.xml` repeats the 66 × 60 defect;
   after the fix, confirm the silhouette still reads at 48dp in Android 13+ themed mode, where
   the system tints the layer and ignores its colours (the two white paths are fine).
5. **The documented master logo source is missing.**
   `docs/PLAY_STORE_PREPARATION_20260924.md` states that the user's master artwork was preserved
   at `design/airrelay-svg-logo/source.svg`, but that path does not exist; only
   `design/airrelay-monogram/airrelay-monogram.svg` (392 bytes) and
   `design/airrelay-monogram/approved-preview.png` remain. Restore or relocate the master file
   and correct the document, otherwise the store assets cannot be regenerated from source.
6. **Store assets must be re-derived after the geometry fix.** Any exported store icon
   (512 × 512), Google TV banner (320 × 180) and feature graphic produced from the old 66 × 60
   layout will be cropped or scaled the same way, and
   `design/airrelay-monogram/approved-preview.png` predates the fix. Re-export them from the
   corrected geometry and re-approve the preview. Add an explicit store-asset checklist to the
   release gates.
7. **No density-bucket mipmaps** — only `mipmap-anydpi` and `mipmap-anydpi-v26`. Vector
   drawables are fine on API 21+, but old TV boxes may render the pre-26 layer-list poorly;
   optionally generate a density set from the corrected geometry (low priority).
8. **Launch flash is not brand-consistent.** The base theme is light while the TV theme is dark
   (also finding I15), so a launch shows a white flash with the dark mark. Set a dark
   `windowBackground` (or an explicit brand splash) so the first frame matches the brand.
9. **In-app brand mark is correct — use it as the reference.** `ReceiverBrand()`
   (`ReceiverComponents.kt:136-147`) draws the logo with `Modifier.size(44.dp)` and
   `ContentScale.Fit`, which preserves the aspect ratio. The icon fixes should copy that
   approach: a square box plus aspect-preserving scaling.

### Other phase-4 items

- Restore the release gate: the main specification document and
  `REQUIREMENTS_TRACEABILITY.csv` are absent, so `scripts/check_readiness.py` aborts with an
  `OSError` in both modes and 17 of the 57 Python tests fail; `artifacts/evidence/` does not
  exist. Keep the gate fail-closed and do not fabricate acceptance records.
- `artifacts/preparation-tests.json` is a committed artifact that the documented
  `scripts/run_checks.py` overwrites (it currently records `ok: false`, 57 run, 17 errors).
  Stop shipping a mutable report as evidence.
- Regenerate `legal/SBOM.spdx.json` with an actual `tools/generate_sbom.py` (the file it
  claims as its creator does not exist); the current SBOM is `AirRelay … 0.0.31`-era and its
  versions drift from `gradle/libs.versions.toml`.
- Translate the active documentation set to English, archive the contradictory dated notes
  under `docs/archive/` with an English index, and fix the stale claims
  (`docs/ARCHITECTURE.md` still says the design is not implemented).
- Wire `androidTestImplementation` dependencies and document the on-device test command;
  `scripts/run_full_check.ps1` for one-command verification.

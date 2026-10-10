# UxPlay patch series — how the vendored tree is produced

`app/src/main/cpp/third_party/UxPlay` is not pristine upstream. It is produced in
three recorded steps.

## 1. Pinned upstream

UxPlay at commit `462153392f2e30937424922039ff9f0cda5e7b1a` (the pin also appears in
`legal/SBOM.spdx.json` and `docs/research/native-audit.lock.json`).

## 2. `0001`–`0006`: upstream → 2026-09-07 delivery baseline

These are the reviewed fixes recorded for the earlier delivery:

| Patch | Scope |
|---|---|
| `0001-fix-hls-playlist-crash.patch` | HLS playlist bounds/pointer handling |
| `0002-fix-uaf-on-playlist-refresh.patch` | playlist refresh lifetime |
| `0003-refresh-live-media-playlists-on-demand.patch` | live playlist refresh |
| `0004-video-sender-compat.patch` | sender plist/DMAP compatibility |
| `0005-signal-video-reset-on-abandoned-play-connection.patch` | dangling `video_play_conn` |
| `0006-deliver-volume-while-audio-stream-paused.patch` | volume while the RAOP stream is paused |

## 3. `0007-r40-video-compat-vs-2026-09-07-baseline.patch`: baseline → R40

This patch closes a gap: the shipped tree contained local UxPlay changes that were
**never recorded** — `current_media_request` handling, the `playlist &&` guard, scrub/rate
validation and live HLS refresh state were absent from `0001`–`0006`. `0007` is generated
from the real bytes, so the tree is now reproducible from the chain.

It contains 14 modified files and 2 added files (`lib/airplay_rate.h`,
`lib/fcup_response.h`), including the R40 fix that removes a residual use-after-free:
`get_media_playlist()` now copies the remote URI into a caller-owned buffer while the
`media_data_store` lock is still held, instead of returning a pointer that
`create_media_data_store()` may free before the caller uses it.

## Reproduction

```bash
# from a checkout whose vendored UxPlay is the pinned upstream commit
git apply patches/UxPlay/0001-fix-hls-playlist-crash.patch
git apply patches/UxPlay/0002-fix-uaf-on-playlist-refresh.patch
git apply patches/UxPlay/0003-refresh-live-media-playlists-on-demand.patch
git apply patches/UxPlay/0004-video-sender-compat.patch
git apply patches/UxPlay/0005-signal-video-reset-on-abandoned-play-connection.patch
git apply patches/UxPlay/0006-deliver-volume-while-audio-stream-paused.patch
git apply patches/UxPlay/0007-r40-video-compat-vs-2026-09-07-baseline.patch
```

`0007` must be applied last. It was verified mechanically: applying it to the baseline
archive `AynaTV-Kaynak-Kod-ve-Devir-2026-09-07.zip` (extracted under
`app/src/main/cpp/third_party/UxPlay/`) reproduces the current tree byte-for-byte
(LF-normalised; 0 content differences, 0 missing, 0 extra files).

Because the build compiles the vendored tree directly and never applies these patches,
they are a change record and a reproduction path, not a build step.

## Provenance status

`0001`–`0006` map upstream to the 2026-09-07 baseline. The step from the pinned upstream
commit to that baseline is asserted by the earlier delivery's hashes
(`docs/refinement2/PREVIOUS_DELIVERY_PROTECTED_SHA256.json`); no pristine upstream copy is
present in this checkout to re-derive it offline. `0007` is fully verified against the
baseline archive.

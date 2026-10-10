# Windows direct receiver reproduction

The iPhone test reproduced a remote-media fetch failure outside Android: GStreamer
reported `souphttpsrc0 Forbidden`, then internal stream/typefind errors. The sender
identified its media process as WebKit. The reason the origin rejected the request
has not been established; no codec change or access-control bypass is justified.

When mirroring subsequently resumed (498x1080), the receiver aborted at
`video_renderer_choose_codec: assertion failed: (!hls_video)`. This is a separate
receiver crash, not proof of a phone disconnect or emulator problem.

Windows-only renderer changes:
- Route HLS pipeline errors through the existing EOS handling path.
- Reject a premature mirror codec through the existing per-stream reset path,
  instead of terminating the entire process. This fallback does not guarantee
  seamless mirroring recovery; it requires an iPhone retest.

Validation: existing Windows CMake/Ninja build completed and uxplay.exe restarted.
Remote video playback, seamless return, and the 403 cause remain unverified.
Raw reproduction evidence is retained locally in
`artifacts/protocol-analysis/windows-hls-crash-20260910.log`; it may contain signed
media URLs or pairing data and must not be shared unredacted. Build output is in
`artifacts/protocol-analysis/windows-hls-recovery-build.log`.

Android APK and emulator were not changed for this Windows diagnostic correction.

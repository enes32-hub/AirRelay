# R34 — End current AirPlay connection

Build: 0.1.0-rc34-test, versionCode 69, arm64-v8a + armeabi-v7a debug.
APK: artifacts/releases/AirRelay-R34-session-end.apk
SHA-256: 7512947B03C8B56B0A901CD1CC9F1777C38C04B5332D2665F73CCF83372807DF

## Fix
Previously endCurrentStream only stopped local renderers. It did not close native sender sockets. Now it requests connection destruction on the HTTP server owner thread through a mutex-protected flag. Existing client connections and their RTP workers are destroyed; listening sockets, discovery and pairing storage remain intact. Late media/mirror callbacks are suppressed until a new connection starts after the old connections close. Request processing checks the disconnect flag after select to avoid dispatching stale requests.

Also: UP above the highest control hides the overlay (seek bar remains reachable); removed upper-left recording label. Recording finalization behavior is unchanged.

## Verification
- PASS: ARM debug build and instrumentation APK build (artifacts/r34-build.log).
- PASS: real TCP transport close and reconnect, three cycles, same native receiver handle/listener; synthetic late mirror/prepare callbacks cannot revive presentation (artifacts/r34-transport-end.txt).
- PASS: mirror control panel hide/reveal and initial focus (artifacts/r34-control-panel.txt).
- PASS: direct-mode panel hide/reveal and centered controls (artifacts/r34-direct-controls.txt).
- PASS: installed in-place on 192.168.31.131:5555, versionCode 69 confirmed. No app data cleared; recordings not copied or deleted.
- NOT RUN: real iPhone streaming disconnect indicator, audio/video teardown with an actual sender, subsequent real iPhone reconnect. User acceptance pending.

TCP probes and synthetic UI callbacks do not constitute a full AirPlay/iPhone acceptance test.

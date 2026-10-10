# Logo and subtitle revision — 2026-09-22

- Original supplied RGBA logo used without raster modification in branding, launcher and TV banner. Theme palettes unchanged.
- Subtitle bottom padding raised from 12% to 18%; default, negative numbered and lower fractional cues aligned at 82% with END anchor. Upper explicit positions preserved. Burned-in captions cannot be moved.
- PASS: x86_64 debug and instrumentation build; ARM debug build.
- PASS: subtitle-position instrumentation regression (default, negative numbered, lower fractional, upper preserved, window fill cleared).
- PASS: emulator-5584 update preserving data and visual inspection of main-screen logo; screenshot artifacts/logo-preview.png.
- NOT RUN: real iPhone subtitle visual acceptance.
- TV NOT UPDATED: 192.168.31.131:5555 ADB connection timed out (10060).
- Separate APKs: artifacts/releases/AirRelay-R35-logo-subtitle-emulator.apk and AirRelay-R35-logo-subtitle-TV-ARM.apk. Prior TV artifact preserved.

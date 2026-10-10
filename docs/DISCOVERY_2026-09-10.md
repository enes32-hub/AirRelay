# Emulator discovery and test capture

Observed: Android NSD still had active AirPlay/RAOP registrations, while its bounded diagnostic history no longer contained the original TXT registration lines. The Windows bridge withdrew the receiver and could not restore it from that dump. Wi-Fi IP and interface remained 192.168.31.32 / 7.

Fix: retain previously verified TXT only while the full active registration identity, including client ID and startTime, remains unchanged. Stop, replacement or missing registrations invalidate that cache. History alone cannot advertise an inactive receiver. This fixes history rollover during a running bridge; a cold bridge start after history has already rolled over still requires refreshing receiver registration.

Validation: all eight lifecycle tests pass, including history rollover, registration replacement and stop/cache invalidation. Actual emulator-5554 stop/start withdrew Windows services and then registered them again. Current bridge PID: 15388. No APK change required.

Test capture PID: 2936. tools/emulator-airplay-bridge/watch_test.py collects selected logcat diagnostics with credential-related lines omitted and URLs/long identifiers redacted. Rotates at 8 MiB with four backups. It records neither screen images nor audio. Capture reconnects when ADB disconnects. Logs: artifacts/discovery-20260910/receiver.log and bridge.log. Windows reboot terminates these helper processes. iPhone visibility and end-to-end playback await the user's test.

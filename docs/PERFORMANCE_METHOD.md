# Real device measurement method

This document is a test plan; it is not a measurement result. The effective chain is iPhone → Android TV only.

## Experiment record

On every run the receiver build/core commit and APK SHA-256, iPhone model/iOS build, Android TV model/OS build/ABI, decoder name, audio format, network type and test duration are recorded. Real IP, MAC, PIN, private key, SSID or personal content do not go into the report. Unprotected test content on the device is used instead of the user's personal screen; a screen/audio recording feature is not added to the receiver.

Reference network: the same subnet, TV on Ethernet or strong 5/6 GHz Wi-Fi, iPhone on strong 5/6 GHz, RSSI of about -60 dBm or better, at least 100 Mbps local throughput, loss <0.5%, jitter p95 <10 ms and AP isolation off. The local network must keep working even if the internet is cut. A user-owned controlled laboratory is used for network measurement; no speed test is sent to an external service.

## Discovery and startup

For discovery and connection distributions, at least 30 independent connection attempts are planned for every device/OS/network/profile combination. First-time setup and reconnections of a trusted device are not mixed together. Failed attempts are kept in the report; only the successful ones are not selected out.

Monotonic event times: application start, advertisement ready, visible in the iPhone list, user selection, PIN screen, PIN acceptance, session setup, first video and first audio. The start of the first frame/audio durations is PIN acceptance; PIN waits with no timeout are reported separately. This startup definition is a measurement decision and does not change the thresholds. Specification 19.2: advertisement ≤5 s p95, appearing in the list ≤8 s p95, PIN screen ≤3 s p95, session after PIN ≤4 s p95, first frame p50 ≤2 s / p95 ≤4 s, first audio p95 ≤4 s.

## Latency, frame and audio

1. The iPhone and the TV appear in the same frame of the same high-speed camera; the end-to-end difference is measured with a time code/clap pattern. The camera frame duration, the uncertainty and the number of samples are reported. The difference between the phone/TV wall clocks is not used as the measurement.
2. In sequential samples the nearest-rank `ceil(0.95*n)` is used for p95; p50 is the median. Profiles and devices are evaluated separately. Low latency p50 ≤150 ms, p95 ≤250 ms; automatic p95 ≤350 ms; stable p95 ≤500 ms.
3. 30 minutes in the 1080p60 test: average ≥59 fps, drop <0.5%; for 1080p30 ≥29.5 fps and drop <0.25%. Numerator/denominator (expected/received/decoded/displayed/dropped frames) is stated separately; the decoder callback count alone is not the number of frames shown on screen.
4. >500 ms freeze 0, 250–500 ms freeze at most 1 per hour, corrupted frame and wrong rate 0. The hourly freeze criterion requires at least 60 minutes of separate observation; a 30-minute recording does not by itself count as one hour.
5. The absolute value of the A/V difference p95 ≤50 ms, no sample >100 ms; drift ≤50 ms at the end of 2 hours. Do not reduce negative and positive offsets to an average that cancels them out. In 30 minutes the underrun target is 0, at most 1 short event; corrupted PCM 0.
6. From orientation change to the correct frame p95 ≤1.5 s. A 500 ms interruption, decoder stall and moving picture after Surface ≤2 s; roaming ≤8 s, controlled service restart ≤5 s.

## Robustness and resources

First 20 two-minute connect/disconnect cycles; then separate 2-hour and 8-hour runs at Tier A/B. At a stable start and after the same recovery interval, memory, fd, thread, codec, AudioTrack and lock counts are taken. At the end of 2 hours memory growth <10%, crash/ANR/hang/leak 0. In 8 hours continuous memory growth and corruption that prevents reconnection 0. The numerical memory measurement is interpreted with the effects of GC and the allocator explained; a single fluctuation is not counted as a trend.

After 30 minutes of 1080p60 the thermal state is recorded; a continuous severe level is not accepted. The network degradation matrix is applied in separate controlled runs according to main specification section 32. Results that do not meet the thresholds are kept as FAIL.

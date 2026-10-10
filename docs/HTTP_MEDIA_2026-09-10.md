# Remote video access failures

Observed at host time 13:32:15 and 13:32:32: AirPlay TCP ports 7000/7100 accepted sender connections. WebKit-origin remote play was handed to Media3; playlist loading failed with HTTP 403, surfaced as generic error 2004. This is not evidence of a codec failure. The source URL and access requirements are not yet supplied for reproduction.

Changes: report actual HTTP status to the existing error/fallback path; explain access denial in Turkish/English; do not retry an identical 401/403 load; retain existing retry handling for other failures. Add redacted per-load diagnostics (manifest/segment/DRM, elapsed time, byte count, status), without content URLs or request credentials.

The existing service releases remote playback and resumes mirror audio on failure. This change does not synthesize credentials, guarantee sender resumption, or claim that the original 403 source is fixed. Reproducing the original site remains required to determine whether the sender omitted required request context, the link expired, or the source restricts receiver access.

Android regression entry point: IntegrationInstrumentation with httpOnly=true. It serves a controlled 403 manifest, checks a single request and exact status propagation, then prepares a local video with the same AirPlayVideoPlayer instance.

Result: PASS HTTP_403: single request; exact error; next video ready. Both application and test APK built successfully and were installed on emulator-5554 with data-preserving updates. App reopened and native receiver registered after testing; bridge/capture remain running. This validates error handling and subsequent preparation, not the original external video or iPhone playback.

Follow-up at 13:43:34: original remote source still returned 403. At 13:43:43 control connections closed and screen audio was configured, but the captured interval contains no renewed video-size/decoder-start event after the failure. UI inspection confirms the access-denied overlay remained. Sender mirror resumption is not proven.

Return changes: re-arm first-frame observation without restarting the existing decoder; accept only output recorded after the return request, rejecting cached/queued earlier frames. A valid current mirror presentation clears a stale remote error. Error controls stay visible, and Back goes directly to the receiver home instead of only hiding controls. Host regressions: 18 frame-provenance checks and 18 screen-session checks PASS. Original site access failure and real iPhone resumption still require testing; missing sound deferred at user's request.

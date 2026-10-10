# AirRelay Receiver — legal documents

Public legal pages for **AirRelay Receiver** (`com.airrelay.receiver`), an AirPlay receiver for
Android TV developed by **Mahmut Enes Bozdoğan**.

| Page | URL |
|---|---|
| Privacy policy (English) | https://enes32-hub.github.io/AirRelay/legal/privacy.html |
| Gizlilik politikası (Türkçe) | https://enes32-hub.github.io/AirRelay/legal/privacy-tr.html |
| Landing page | https://enes32-hub.github.io/AirRelay/ |

The privacy-policy URL to enter in the Google Play Console store listing is the **English** page:
`https://enes32-hub.github.io/AirRelay/legal/privacy.html`

## Sources

| File | Purpose |
|---|---|
| `PRIVACY.md` | English source (single source of truth) |
| `PRIVACY.tr.md` | Turkish translation |
| `build_site.py` | Renders the markdown above into `privacy.html`, `privacy-tr.html` |

Pages are plain HTML with inline CSS — no external stylesheet, no CDN, no JavaScript — because the
Play Console requires the policy to be on an active, non-editable URL and rejects PDFs.

Rebuild after editing the markdown:

```bash
python build_site.py
```

## Content basis

Every statement about the application was verified against the 1.0.0 source tree, in particular:

- no analytics, advertising or crash-reporting SDK is present,
- the app contains no hard-coded internet address and contacts no developer or third-party server,
- `RECORD_AUDIO` is required only as the prerequisite of Android's audio-playback-capture API, and
  capture is restricted to the application's own audio output (its own UID); the microphone is never
  read,
- recordings, saved videos, settings and support logs are stored on the device, with
  `android:allowBackup="false"`.

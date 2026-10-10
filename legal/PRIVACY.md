# AirRelay Receiver — Privacy Policy

**App:** AirRelay Receiver (`com.airrelay.receiver`) for Android TV
**Developer:** Mahmut Enes Bozdoğan
**Contact:** alquariz@yandex.com
**Effective date:** 11 October 2026
**Document version:** 1.0 (applies to app version 1.0.0 and later)

---

## 1. Summary

- AirRelay Receiver **does not collect, transmit or sell your personal data.** There is no account, no sign-in and no developer server.
- The app works entirely on **your own local network**, between your Apple device (iPhone, iPad or Mac) and your Android TV.
- **No advertising, no analytics, no tracking, no crash-reporting SDKs** are included.
- Anything the app stores — screen recordings, saved videos, technical logs — is stored **on your device** and stays there unless **you** choose to share it.
- The app requests the microphone-adjacent permission `RECORD_AUDIO`, but it **never reads your microphone**. That permission is a technical prerequisite of the Android API used to capture the app's *own* audio while you record. See section 5.

---

## 2. What the app does

AirRelay Receiver turns an Android TV device into an AirPlay receiver. When you start screen mirroring from an Apple device on the same local network, the TV shows that screen and plays its audio. On request, the app can also:

- record the incoming picture and sound to a video file on the TV,
- save a video that is being streamed to the TV,
- keep technical logs to help diagnose problems.

All of this happens on the TV. Nothing is uploaded anywhere.

---

## 3. Data we collect

**None.** The developer of this app does not receive, store or process any of your data.

Specifically:

| Question | Answer |
|---|---|
| Is an account or sign-in required? | No. |
| Do you collect names, e-mail addresses or device identifiers? | No. |
| Is usage data, telemetry or analytics sent anywhere? | No. |
| Are advertising identifiers used? | No. |
| Is any data sent to the developer or third parties? | No, none. |
| Does the app work without internet access? | Yes — it needs only a local network. |

Because no data reaches the developer, there is no developer-side data to access, correct, export or delete.

---

## 4. What is stored on your device (and how to delete it)

| Stored item | Where it lives | Why | How to delete |
|---|---|---|---|
| Screen recordings you start | App storage on the TV (private app directory; can be exported to the Movies library from inside the app) | The recording feature you asked for | Delete inside the app, or remove the files from the TV |
| Videos you choose to save to the TV | TV media library | The "save video" feature you asked for | Delete from the TV's media library |
| Technical / support logs | Private app directory on the TV | Troubleshooting and support requests | Use "clear logs" in the app, or clear the app's storage / uninstall the app |
| App settings (device name, PIN preference, display preferences) | Private app directory on the TV | Remembering your preferences | Clear the app's storage or uninstall the app |

Cloud backup is disabled (`android:allowBackup="false"`), so this data is **not** copied to Google's cloud backup service.

Logs stay on the device unless **you** explicitly export or send them. If you send a log to the developer for support, it is used only to answer your request and is deleted afterwards.

---

## 5. Permissions and why they are needed

| Permission | Why the app asks for it | What it does **not** do |
|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Receive the AirPlay stream and discover receivers on the local network | Does not contact developer or third-party servers |
| `CHANGE_WIFI_MULTICAST_STATE` | Multicast/mDNS discovery so your Apple device can find the TV | — |
| `RECORD_AUDIO` | **Technical prerequisite** of Android's audio-playback-capture API, used only while you record. Capture is restricted to **this app's own audio output** (matching its own UID) | **Never reads the microphone.** No microphone audio is recorded, stored or transmitted |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Keep receiving reliably while the app is in the background, as required by Android | — |
| `POST_NOTIFICATIONS` | Show the ongoing "receiver active / recording" notification required for background operation | — |
| `WAKE_LOCK` | Prevent the TV from sleeping in the middle of a cast | — |
| `RECEIVE_BOOT_COMPLETED` | Optional: start the receiver automatically after the TV boots, **only if you enable this setting** (it is off by default) | Does not start anything you did not enable |
| `SYSTEM_ALERT_WINDOW` (display over other apps) | Optional: bring the receiver to the foreground when a cast begins | Not used to show advertising or to monitor other apps |

Screen recording additionally requires Android's own MediaProjection consent dialog, which you must approve each time.

---

## 6. Network activity

- The app communicates **only within your local network** (LAN): it discovers AirPlay senders via mDNS and receives the stream on the AirPlay/RTSP ports (7000 and 7100 by default) from the sending device.
- The app contains **no hard-coded internet addresses** and makes no connections to the developer or to third-party services.
- AirPlay/RTSP traffic between your Apple device and the TV is **not encrypted** — this is how the AirPlay mirroring protocol works. Anyone who can observe your local network could in principle see that traffic. Use a trusted, password-protected Wi-Fi network, and prefer a separate network for guests.
- Because Android's cleartext-traffic flag is enabled for protocol compatibility, the app is able to receive such unencrypted local traffic. It does not use it to contact the internet.

---

## 7. Recordings and copyright

Recordings and saved videos are created **at your request** and are stored on your device. You are responsible for having the right to record or save the content that appears on your screen (for example protected video from a streaming service may not be recordable, and recording other people's material may require their permission). The app does not remove or bypass content protection.

---

## 8. Third-party software and open-source components

The app includes open-source components, among them:

- **UxPlay** (GPL-3.0) and **android-airplay-server** (GPL-3.0) — AirPlay receiver implementation,
- **FFmpeg** (LGPL) — audio decoding,
- **libplist**, **OpenSSL** (Apache-2.0),
- **AndroidX, Jetpack Compose, Media3 (ExoPlayer), Oboe, Kotlin/kotlinx.coroutines** (Apache-2.0).

The complete licence texts and copyright notices are available **inside the app**, under *Settings → About → Component licences*. No advertising, analytics, attribution or tracking SDK from any third party is included.

---

## 9. Children's privacy

The app is not directed at children and collects no personal data from anyone, including children.

---

## 10. Your rights

Privacy laws such as the GDPR (EU) and the CCPA/CPRA (California) give you rights over personal data held about you. The developer holds **no personal data about you**, so there is nothing to disclose, correct or erase on our side. Data created by the app is under your control on your own device, and section 4 explains how to delete it. If you have questions, contact us at the address below.

---

## 11. Changes to this policy

If this policy changes, the updated version is published at this address with a new effective date and document version. Material changes will also be noted in the app's release notes.

---

## 12. Contact

**Mahmut Enes Bozdoğan**
E-mail: **alquariz@yandex.com**

Apple, AirPlay, iPhone, iPad and Mac are trademarks of Apple Inc. This application is not affiliated with, endorsed by or sponsored by Apple Inc.

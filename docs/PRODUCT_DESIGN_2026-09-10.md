# Velora Cast: product design review

Review date: 2026-09-10. This document records design work, not confirmation that real-device playback or store readiness has passed.

## Brand research and decision

Selected provisional public name: **Velora Cast**. The installed package remains `com.aynatv.receiver`; updates, application data and pairing keys do not depend on a package rename.

Public pages actually inspected in the browser:

- [AirScreen on Google Play](https://play.google.com/store/apps/details?id=com.ionitech.airscreen&hl=en): Ionitech, receiver positioning with AirPlay, Cast, Miracast and DLNA. We do not copy its name, icons or claim its unsupported protocols.
- [AirServer Universal](https://www.airserver.com/PC): receiver positioning, clear sender instructions and a separate trademark notice. Its multi-protocol support does not imply ours.
- [Exact Velora Cast search](https://www.google.com/search?q=%22Velora+Cast%22+app): visible results included unrelated decorative objects and social/media uses; no screen-mirroring app was found in the inspected results. This is limited evidence, not global availability.
- [Exact Rillcast search](https://www.google.com/search?q=%22Rillcast%22): existing podcast/studio use, rejected.
- [Exact Veylcast search](https://www.google.com/search?q=%22Veylcast%22): an existing secure-video platform at veylcast.com, rejected.

**Trademark, company-name, domain and app-store clearance is not certified.** An indexed web search cannot prove exclusivity. The compound Velora Cast is a provisional product identity; commercial clearance remains a release prerequisite. No account, domain or mark was registered.

## Palette and icon

Fixed charcoal/stone palette: background `#141413`, panel `#20201E`, elevated `#30302C`, ivory text `#F4F1E9`, secondary text `#BBB8AF`, warm amber accent `#E1BB7E`, soft accent `#3B3327`. No cyan/purple gradients or wallpaper-generated colours. A strong light outline indicates remote focus independently of colour; reduced motion remains supported.

Calculated WCAG contrast ratios:

| Pair | Ratio |
| --- | ---: |
| Main text / panel | 14.46:1 |
| Secondary text / panel | 8.23:1 |
| Amber / panel | 9.02:1 |
| Button text / amber | 8.85:1 |
| Focus outline / elevated surface | 11.74:1 |

These are colour calculations, not measurements of a physical TV. TV overscan, launcher masking, font scale and interaction require screenshot/device review.

The original vector emblem uses a circular amber disc and a monitor/V motif. Google TV can mask the adaptive icon itself; both adaptive and round references are provided. API 24/25 fallback mipmap vectors were missing and are now provided. Android TV still gets a 320×180 banner with the complete name outlined as vector glyphs. `tools/generate_product_brand.ps1` reproduces these resources without copying competitor assets or embedding an external image.

[Official Android TV icon guidance](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines) was also inspected: both `android:icon` and a 16:9 `android:banner` are required, adaptive icons are recommended, and the launcher chooses the shape. TV does not use themed monochrome icons. The existing `roundIcon` compatibility reference is retained but is not what guarantees a round adaptive mask; current guidance deprecates that attribute for adaptive icons. The circular artwork itself remains round inside the safe area.

## End-user changes

- Brand comes from the app-name resource instead of a hardcoded AynaTV label.
- Short product copy replaces developer narration such as “not implemented”, “emulator ceiling”, “defaults preserved” and MediaFormat constant names. Support diagnostics remain available under Help.
- Clear picture preference labels with selected-profile explanations keep the distinction between screen resolution and internet-video bitrate honest.
- Manual preferred bitrate appears only in advanced settings and explicitly does not control the mirroring sender’s encoder. It depends on the source variants and is not advertised as a hard network-bandwidth cap.
- Startup preferences include background operation and incoming-stream display. Permission is requested only through an explicit button. The UI acknowledges TV operating-system restrictions.
- Quit is a named navigation action with a confirmation that receiver/background activity will stop.
- A port-conflict message identifies the occupied port without stopping an unrelated app.
- About contains version information, Apple trademark clarification and bundled open-source licences. No legal attribution is removed for cosmetic reasons.
- Error 5001 explains interrupted picture and how new sender content resumes it. The waiting spinner, PIN precedence, current-session first-frame checks and real video seek controls remain intact.
- Text-field focus alone still does not open the keyboard.

## Verification state

**Passed:** every resource XML parses; Turkish default and explicit English resources each contain 367 named strings at the first check, with no duplicate or missing counterpart. Further profile/recovery strings were added symmetrically. Contrast calculations above passed the normal-text 4.5:1 threshold.

**Pending at initial review:** Android compile, actual TV launcher icon/banner, remote focus, startup/quit/permissions, large text and screen playback. A source-only check is not designer approval of the rendered product. Final screenshot observations are added after the integrated APK is available.

The 197 KB notices asset is displayed with a bounded lazy reader and D-pad scrolling. It is read off the main thread and is not measured as a single enormous text layout. This avoids introducing a long-frame problem in the legal-information screen.

## First real emulator review

Reviewed `artifacts/product-polish-20260910/home.png` and `settings.png` (1920×1080). The new palette, circular emblem, wordmark and light focus ring were legible. The settings screen showed no text overlap; its long receiver name fitted without loss. The home screen **failed** the first review: a three-line receiver name combined with the large two-line hero pushed the primary actions partly out of view. This was fixed by removing the redundant eyebrow and changing the hero to a compact, single-line heading while retaining full name wrapping and scrolling. The original failing screenshot remains as evidence; a new integrated capture is required before signing off that correction.

`startup.png` confirmed the startup toggles and descriptions fit, with TV startup and background disabled by default. It also exposed a residual purple tone in the default Material3 off-switch track. All surface-container and tertiary palette tokens were then explicitly set to the neutral theme; this correction awaits the final integrated screenshot. The underlying warm palette, emblem and typography are approved; this approval does not substitute for launcher/large-text/runtime verification.

## Integrated home approval

Reviewed the real `home-final.png` at 1920×1080. **PASS:** the compact heading, complete three-line receiver name, connection status, both primary actions, help action and footer are all visible without overlap or clipping. The initial clipped-controls finding is resolved in this capture. The charcoal/ivory/amber palette, circular emblem, typography and selected-versus-focused treatment are **designer-approved for this release candidate**.

This visual approval covers the observed home/settings/startup screens at the captured scale. It is not a claim of trademark clearance, physical Google TV launcher compatibility, all font scales, or successful iPhone playback. Those acceptance results must remain separate in the release report.

The integrated `startup-final.png` was then inspected. **PASS:** disabled switch tracks are now neutral charcoal/stone, the earlier purple tint is absent, active switches use the intended amber, and all four startup controls and descriptions are readable. The neutral palette correction is visually confirmed. Final designer verdict: **approved for the observed release-candidate UI**, with trademark clearance and actual Google TV launcher-mask testing still outstanding.

## Final FPS-control honesty correction

The independent inspector found that the advanced editor still offered 120 FPS and accepted 1–240, although the receiver clamps custom output to 60 on a physical TV and 30 on the development emulator. The UI now uses `StreamProfile.CUSTOM.frameLimit` and the same emulator hardware check as `AirPlayService.effectiveMirrorFps`: presets stop at the effective device ceiling, custom entry is validated against 1–ceiling, and existing out-of-range saved values display their effective clamped value without rewriting preferences. Both locales state the numeric ceiling and that the setting applies with the **Custom** picture preference. No 60 FPS performance guarantee is made. This is an editor-only correction; decoder and transport settings are unchanged.

## Large-font final approval

The additional `home-large-font.png` check at system font scale **1.3** exposed partial cropping of the bottom primary-button backgrounds while their text remained readable. The existing scroll view could reveal them, but the initial layout did not fully fit. `ReceiverHome` was adjusted only for `fontScale >= 1.2`: outer vertical spacing is 12 dp instead of 16 dp and the receiver-name card uses 16 dp vertical padding instead of 20 dp. Horizontal padding, font sizes, full name and the normal 1.0 layout are unchanged.

Reviewed the rebuilt, real `home-large-font-fixed.png` at 1920×1080 and font scale 1.3 (APK SHA-256 `6CC39C2F7CC5981471789A74AC0117EBC685F73FBE0EFA4C6A93D8AAF1590DE9`). **PASS:** the complete three-line name, status, instructions, both primary-button shapes and labels, guide and footer are fully visible. No clipping or overlap is present in this capture. The additional large-font correction is designer-approved. Root restored the system font scale to 1.0 after capture.

Final visual approval covers the actually reviewed 1.0 and 1.3 home layouts and the settings/startup captures described above. It does not certify every font scale, physical-TV overscan, Google TV launcher masking, trademark clearance or iPhone playback acceptance.

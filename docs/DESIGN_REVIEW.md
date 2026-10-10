# AirRelay TV UI — design review and polish plan

Reviewer: frontend/TV design specialist (delegated review, read-only).
Scope: the full Compose UI layer, both theme XML files and the base string set.
Status: **accepted — verdict (a) targeted polish, not a redesign.**

## 1. Verdict

**Targeted polish of the existing screens.**

The fundamentals are already strong: a semantic token system with three user palettes
(`ui/theme/Theme.kt:12-32`, `103-112`), a genuine TV focus system with persistent focus IDs and
safe dialog defaults (`ui/ReceiverComponents.kt:82`, `ui/ReceiverDialogs.kt:32,151`,
`ui/ReceiverSettings.kt:109-124`), rich idle/state modelling with live-region semantics
(`ui/ReceiverComponents.kt:167-182`, `ui/ReceiverHome.kt:96-108`), and responsive behaviour for
font scale, reduced motion and accessibility timeouts (`ui/ReceiverHome.kt:35-55`,
`ui/MainScreen.kt:341-343,406-413`). The defects listed below are systematic inconsistencies —
duplicated colours, ten competing corner radii, two recording-quality controls, one orphaned
screen — that a from-scratch redesign would re-implement rather than fix. The overlay focus
choreography (`ui/MainScreen.kt:389-451`) is intricate and works; discarding it is high risk and
low reward.

## 2. Strengths to keep

- Semantic colour roles with three user-selectable palettes and centralised theming.
- Persistent focus IDs, focus restore after pickers, safe dialog focus defaults, and the
  wake/reveal overlay choreography.
- Colour-coded status dot with `stateDescription`/`liveRegion` semantics plus a phase-specific note.
- `fontScale`-aware layouts, stacked guide steps, reduced-motion gating, accessibility-aware
  overlay timeout.
- PIN dialog: secure-flag policy, 86dp digit boxes, 48sp monospace digits
  (`ui/ReceiverDialogs.kt:33-50`).
- Onboarding wizard with progress bar and permission gating (`ui/ReceiverDialogs.kt:63-144`).

## 3. Findings

| # | Sev | Location | Problem | Fix |
|---|---|---|---|---|
| 1 | High | `ui/CapsulePlayerControls.kt:27,58-71`; `ui/MainScreen.kt:468,478-480`; `ui/StreamMetrics.kt:41` | Hardcoded hexes duplicate the playback palette (`0xFFE5F4E9` duplicates `Theme.kt:36`; `0xFFE5B7A8` duplicates `Theme.kt:37`); the overlay is hardcoded a third time. The overlay drifts from any palette edit. | Reference the palette only; delete the duplicates. |
| 2 | High | `ui/MainScreen.kt:165` | The player is wrapped in `AirPlayTheme(selected="dark")`, so a light-theme user gets a dark teal overlay. The playback palette (`Theme.kt:34-40`) is a fourth accent family unrelated to the classic lavender palette. | Derive overlay colours from the active palette, or scope and document the exception. |
| 3 | High | `ui/ReceiverDialogs.kt:36,46,72,152`; `ui/ReceiverComponents.kt:65,207`; `ui/ReceiverHome.kt:87,151`; `ui/ReceiverSettings.kt:51`; `ui/ReceiverPermissions.kt:44`; `ui/MainScreen.kt:479,617`; `ui/StreamMetrics.kt:41`; `ui/LogsScreen.kt:92` | Ten radii in use: 2, 6, 10, 12, 14, 16, 18, 22, 26, 28, 52, plus `CircleShape` and M3 chip defaults. | Three-tier scale: **12** controls/chips, **20** cards/panels, **28** dialog shells; `CircleShape` for circles only. |
| 4 | Medium | `ui/SettingsScreen.kt:357-375,522-529`; `Theme.kt:110` | `TvAction`/`TvPreference` use a 2dp focus border, but filter chips and sliders rely on the default focus indicator while ripples are globally disabled — focus is nearly invisible at couch distance. | 2dp focus border/ring on chips and sliders. |
| 5 | Medium | `ui/CapsulePlayerControls.kt:73-74`; `Theme.kt:98`; `ui/ReceiverHome.kt:108,173`; `ui/ReceiverComponents.kt:120,173` | Typography floor is too small for 10 feet: 10sp capsule labels, 12sp `labelSmall`, 14sp `bodySmall` for most descriptions. Semantic contrast is AA-compliant (classic Muted ≈7:1); the failure is size-driven. | 12sp capsule labels, 13sp `labelSmall`, `bodyMedium` for descriptions. |
| 6 | Medium | `ui/MainScreen.kt:456-463,473-475,454` | Error, LIVE and waiting states are text-only: a bare centred `Text` on black, a small text row for LIVE (although `live_badge` exists in the string set), and a 44dp spinner. | Width-capped error card (icon + body + return action), LIVE badge chip, larger spinner. |
| 7 | Medium | `ui/RecordingPanel.kt:116`; `ui/ReceiverSettings.kt:371`; `ui/LogsScreen.kt:87` | Empty states are bare muted text. | One shared `EmptyState` composable (icon + headline + hint) reusing panel styling. |
| 8 | Medium | `ui/SettingsScreen.kt:69-307,322`; `ui/QuickRecording.kt:56-61`; `ui/RecordingPanel.kt:98-101` | Advanced settings is a flat wall (4 sections, 20+ rows); `SectionHeader` uses `colorScheme.primary` while the rest of the app uses muted labels; recording quality is duplicated with inconsistent controls. | Collapsible section cards, one segmented quality control, uniform label treatment. |
| 9 | Medium | `ui/MainScreen.kt:211` | `ReceiverPage.LOGS` renders `ReceiverHelp`; `LogsScreen` is unreachable although `tab_logs` implies a real destination. | Route `LOGS` to `LogsScreen`. |
| 10 | Medium | `ui/ReceiverComponents.kt:117-120`; `ui/ReceiverSettings.kt:373`; `ui/MainScreen.kt:456-463` | Overflow risk across 16 locales: preference title/description have no line cap, device rows concatenate name+ID into the title, error text is unbounded full-width. | 2-line cap with ellipsis, device ID moved to the description, constrained error card. |
| 11 | Low | `ui/CapsulePlayerControls.kt:48`; `ui/MainScreen.kt:494-541` | `fontScale` is applied twice: capsule widths are multiplied by it while `sp` text already scales, so at 1.3–1.5x the overlay rows can exceed the 1100dp container. | Drop the manual width maths; let `FlowRow` flow. |
| 12 | Low | `Theme.kt:20-22` | `AccentSoft` equals `Elevated` in the classic palette, so "soft accent" containers read as plain elevated panels. | Distinct tinted token. |
| 13 | Low | `ui/ReceiverDialogs.kt:155-156,135` | Cancel is styled primary in `ConfirmReceiverAction` while confirm is primary elsewhere. | Confirm primary, cancel neutral — except for destructive confirms. |
| 14 | Low | `ui/MainScreen.kt:223,299-300`; `ui/ReceiverHome.kt:82,89`; `ui/ReceiverDialogs.kt:75-81` | No spacing scale; ad-hoc 4–56dp values. | 4/8/12/16/24/32 rhythm. |
| 15 | Low | `res/values/themes.xml:3`; `res/values-television/themes.xml:3-5` | The base theme is light while the TV theme is dark with a black window background — a flash/coherence mismatch on the non-TV fallback. | Dark base theme, or a matching window background. |

## 4. Per-screen polish order

1. **Global tokens** — one shape scale (12/20/28), one spacing rhythm, no hardcoded hexes, a distinct
   `AccentSoft`, dark base theme (findings 1, 3, 12, 14, 15).
2. **Player overlay** — 12sp labels, error card with an action, LIVE badge, palette derived from the
   active theme, no manual `fontScale` width maths (2, 5, 6, 11).
3. **Home** — keep the layout; descriptions up to `bodyMedium`; pending-panel accent; keep the
   status-dot language (5, 12).
4. **Dialogs** — keep the PIN digits; add pairing iconography; confirm primary except destructive;
   28dp shell with 16dp inner radii (3, 13).
5. **Settings** — collapsible advanced groups, chip focus borders, unified recording quality,
   preference overflow caps, LOGS route fix, device-row restructure (4, 8, 9, 10).
6. **Recordings / Logs / Help** — shared `EmptyState`; keep the accordion help (7).

## 5. Quick wins (lowest risk, highest visual return)

1. Replace the hardcoded capsule/overlay/metrics hexes with palette references
   (`ui/CapsulePlayerControls.kt:27,58-71`, `ui/MainScreen.kt:468,478-480`, `ui/StreamMetrics.kt:41`).
2. Capsule labels 10sp → 12sp (`ui/CapsulePlayerControls.kt:73-74`).
3. 2dp focus border on filter chips (`ui/SettingsScreen.kt:357-375,522-529`).
4. Cap `TvPreference` titles at two lines with ellipsis (`ui/ReceiverComponents.kt:117`).
5. Route `LOGS` to `LogsScreen` (`ui/MainScreen.kt:211`).
6. Error overlay: width cap, icon, home action (`ui/MainScreen.kt:456-463`).
7. Normalise card radii to the 12/20/28 scale (`ui/ReceiverComponents.kt:207`,
   `ui/StreamMetrics.kt:41`, `ui/ReceiverPermissions.kt:44`, `ui/ReceiverDialogs.kt:36,152`).
8. Restyle the recording-quality setting as a labelled segmented control
   (`ui/QuickRecording.kt:56-61`).

## 6. Constraints

No new dependencies or frameworks. Compose Material3 as already used. The existing vector brand
assets (AirRelay logo and monogram) remain the brand surface. Behaviour is unchanged: all mode and
state decisions stay in `receiver/InteractionPolicy.kt` and the ViewModel; this review changes
presentation only.

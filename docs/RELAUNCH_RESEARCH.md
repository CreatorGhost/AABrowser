# AA Browser — Relaunch Research & Roadmap (2026-06-13)

> Source: 6-agent web research (car-native apps incl. CarTube, competitor browsers, platform
> capabilities, stability practices, differentiators/naming) + product synthesis. This is the
> blueprint for the rename + relaunch. Honest about Android Auto platform limits.

## Positioning
A **car-native media-hub browser** for Android Auto / Automotive: a polished, stable, AMOLED
launcher of the media services people actually want in the car (YouTube, Netflix, Twitch, web
video, news) with big glanceable tiles, voice-driven controls, and a real tabbed browser
underneath. Its defining promise is **consent-gated passenger video continuity**: video playback
requires an explicit passenger/non-driver confirmation, and once confirmed the app should not
voluntarily close the active video surface during vehicle-motion lifecycle changes. For people who
today sideload CarStream/Fermata or admire iOS CarTube but want one stable, no-root, open-source
Android app.

## ⏰ Timing (why now)
Google announced **official browser + video app categories for Android Auto, rolling out with
Android 16** (Vivaldi + Chrome as launch partners). Once a browser is one tap away from Play, "a
browser exists for AA" is no longer the pitch. We must win on **Read-Aloud, Media Hub, voice,
AMOLED polish, SponsorBlock/ad-block, and stability**, and time the relaunch to that window.

## 🚩 The 3 flagship features to lead with
1. **Passenger-confirmed video continuity** — before real video starts or resumes, the app requires a
   clear confirmation that playback is for a passenger/non-driver. After consent, the app preserves
   the active media WebView/fullscreen surface across motion-related lifecycle changes instead of
   force-closing it.
2. **Media Hub start page** — curated big-tile launcher (YouTube/Netflix/Twitch/web/news + a
   "Continue" row). The core "this is OURS" identity; evolves the existing 6-card start page. Apply
   the neon **Aurora** styling here as the hero surface.
3. **Voice-first navigation** — surface the already-built `SpeechRecognitionBridge` as a large
   primary mic button ("open YouTube", "search …", "go to …") so users dodge the laggy head-unit
   keyboard.

## Feature roadmap (feasibility · consent posture)
**Flagship**
- Passenger-confirmed video continuity — moderate · consent-gated video
- Read-Aloud TTS — moderate · audio/voice mode
- Media Hub start page — easy · large-target media launcher
- Voice-first nav — easy · large-target media launcher

**Core**
- MediaBrowserService + `onPlayFromSearch` (browsable Saved/Bookmarks/History in the car media UI) — moderate · audio/voice mode
- Reader Mode (Readability.js; also the clean text source for Read-Aloud — build together) — moderate
- Quick-resume "Continue watching/listening" row (resume last page/tab/video + TTS position) — easy · consent-aware

**Nice-to-have**
- On-device translation (ML Kit, offline, privacy-safe) — moderate
- Find-in-page (`findAllAsync`) — easy
- Reading List → "read these to me" TTS queue (local only) — moderate · audio/voice mode
- One-tap fullscreen + oversized gesture controls + consent-aware video handoff — easy
- Per-site settings (desktop-UA/zoom/dark by host) + download manager — moderate
- Optional cosmetic "Pro" pack (extra TTS voices/themes; never gates safety features; no ads) — easy
- (Defer) Tab groups + Credential Manager passkeys — hard · low car value

## ✅ Pre-relaunch STABILITY checklist (owner's #1 ask: "everything stable")
- **`onRenderProcessGone()` in ConfiguredWebView — MISSING, #1 fix.** On renderer crash/OOM: remove dead WebView, destroy, recreate fresh + reload last URL, return true so the app process survives. No tight reload loop.
- `setRendererPriorityPolicy(RENDERER_PRIORITY_BOUND, waivedWhenNotVisible=true)` so hidden tabs are reclaimed first.
- `WebView.saveState`/`restoreState` per tab + `onSaveInstanceState` — persist heavy state to disk; keep the Bundle tiny (avoid TransactionTooLargeException). *(roadmap F2)*
- Lazy tab materialization + LRU eviction; destroy background renderers; target <250 MB steady. *(F3)*
- Audit MainActivity tab lifecycle for destroy()/leak discipline (LeakCanary + Profiler).
- **Passenger video consent gate — IMPLEMENTED.** Before real video playback starts or resumes, Driftway requires confirmation that playback is for a passenger/non-driver. Muted previews are ignored; audio/voice controls remain available without video consent.
- StrictMode (debug) to catch main-thread I/O; move prefs/icon cache off-thread; DataStore for hot prefs. *(F4/F5)*
- Keep `startForeground(...MEDIA_PLAYBACK)` synchronous on every start path (already done — preserve in refactors).
- **Opt-in** crash + ANR reporting (self-hosted ACRA/Sentry) behind the existing analytics toggle (default OFF); strip PII/URLs.
- Stand up **CI** (`.github/workflows` is empty): build signed release + lint + tests → publish to GitHub Releases (Obtainium). Real semantic version for relaunch.
- Tests beyond stubs: AAOS-emulator Espresso (tab manager no-dead-ends), a motion-lifecycle test (active video is not force-closed after passenger consent), AMOLED golden tests at 800x480; DHU manual checklist.
- Validate car-quality numbers: launch ≤10s, content ≤10s, button ≤2s; touch ≥64dp, ≥24dp apart, fonts ≥24sp; window insets; no heads-up notifications while driving.
- Pinned deps; CHANGELOG per tag; staged rollout (beta tag → latest); **do NOT rotate the release keystore** (Obtainium pins the cert).
- QA matrix: Android 13/14/16 + recent AA updates; multi-tab + desktop-UA video + Read-Aloud surviving motion lifecycle changes end-to-end.

## Name ideas (verify trademark/USPTO classes 9/42 + Play + domain before committing)
- **Aurova** — ties to the neon "Expressive Aurora" identity; short, brandable, no Tube/Car/Google echo.
- **Cabana** — in-cabin "your media cabin"; warm, memorable.
- **Nocturne** — true-black AMOLED / night-drive aesthetic; premium.
- **Driftway** — road + flow; reads as a real product, easy to say aloud (matters for voice).
- **Lumicar** — light/luminance + car; descriptive yet ownable.
- **Halo Drive** — cockpit "halo" glow + driving.
- **Idlecast** — media-casting feel; self-explanatory.
- **Wayfare** — journey + "fare" (content); trademark-light fallback.

## ⚠️ Honest constraints (don't market against these)
- **Video playback is consent-gated.** The app must not present video as driver-facing. It should require passenger/non-driver confirmation before user-started, audible/unmuted, or fullscreen video starts or resumes, and after consent it should not voluntarily close the active video surface during motion-related lifecycle changes.
- **WebView DRM = Widevine L3 (SD only)** — Netflix ~480p, Crunchyroll ~720p. No HD/4K. Document it.
- **Not Play-Store distributable** ("video, games and browsers are not allowed" for AA). Runs only because the manifest declares NAVIGATION + ACCESS_SURFACE + MAP_TEMPLATES and draws the WebView on the nav Surface — a sideload-only workaround. Distribution stays sideload (APK / Obtainium / GitHub Releases) + AAAD whitelist.
- **Distribution is fragile**: AAAD free tier throttles to 1 app / 30 days; Google's 2025-2026 unverified-developer sideload block + stricter AA verification can break install/launch anytime. Ship our own clear sideload+whitelist guide + a "why can't AA see me?" self-diagnostic.
- **Platform behavior varies by host/OEM.** The app can avoid force-closing its own WebView/fullscreen surface, but the external Android Auto/AAOS host may still enforce behavior outside the app process.
- **Car App Library templates are category-gated + Play-reviewed.** MediaSession and WebView remain the current implementation path. PiP / passenger displays are OEM/AAOS-controlled, not addressable by a sideload.
- **Cross-browser/device login import impossible** (sandbox + Keystore). Offer "log in once, stay logged in" + quick site switching; don't market profiles/sync.
- **YouTube video ads not durably blockable** (first-party SSAI). Don't promise YouTube ad-free.

## Suggested relaunch sequence
1. **Stability hardening first** (onRenderProcessGone, saveState, lazy tabs, CarUxRestrictions, crash reporting, CI) — the "everything stable" foundation.
2. **Flagship build**: Read-Aloud + Reader Mode (together) → Media Hub start page (with neon Aurora) → Voice-first mic.
3. **Identity**: pick name → icon/splash/theme → MediaBrowserService browsable tree.
4. **Relaunch** with this doc + `RELAUNCH_FEATURES.md` as the landing/changelog copy, timed near the Android-16 AA-browser-category window.

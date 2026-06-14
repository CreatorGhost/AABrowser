# AA Browser → Relaunch Feature Map (base v2.0 → current)

> Purpose: the marketing + product feature table for relaunching this as a renamed, "truly ours"
> automotive browser. Columns: what the **old** app did, what the **new** build does, and the
> **one-line marketing hook**. Verdict at the bottom: relaunch-worthy? Yes.

## Headline features (the relaunch story)

| # | Capability | Old AA Browser (v2.0) | New build (v2.1-beta1) | Marketing hook |
|---|-----------|------------------------|-------------------------|----------------|
| 1 | **Consent-gated video continuity** | ❌ Media/video can stop when the car host sends background lifecycle events | ✅ Active media WebView is preserved across lifecycle changes, with an explicit passenger/non-driver consent gate before real video playback | **"Passenger-confirmed video that does not get force-closed by the app."** |
| 2 | **Ad / tracker / pop-up blocking** | ❌ None at all ("No Ad Blocking — contributions welcome") | ✅ Network blocking (EasyList/AdGuard/StevenBlack data) + cosmetic ad-slot hiding + Facebook in-feed hider + **pop-up/pop-under blocking** + opt-in SponsorBlock | **"Built-in ad, tracker & pop-up blocking — no setup."** |
| 3 | **Premium look** | Generic stock Material 3; Material-You wallpaper **overrode** the brand colors; Holo-era icons | ✅ Curated brand + true-black AMOLED theme ships by default; system-bar contrast fixed. **(Next: full neon "Expressive Aurora" redesign.)** | **"A premium cockpit UI, not a stretched phone app."** |
| 4 | **Streaming quality** | Mobile players, capped/restricted on Netflix/YT/Crunchyroll | ✅ Desktop players default on car/large displays → full-size player + better quality controls | **"Full-size video players, the way the site intended."** |
| 5 | **Stay signed in** | Cookies dropped on process kill; "Sign in with Google/Apple" pop-ups silently failed | ✅ Cookies flushed on pause; OAuth pop-ups open as in-app tabs | **"Sign in once — stay signed in."** |
| 6 | **Smoothness** | Main-thread jank (sync prefs, per-pixel QR, list rebuilds); phone-sized touch targets | ✅ Off-thread work, bulk QR, active-tab GPU tuning, head-unit touch sizing (w600dp), R8-minified release (46M→6.4M) | **"Built for the big screen — smooth and responsive."** |
| 7 | **Privacy** | Always-on telemetry with a persistent ID, no opt-out | ✅ Telemetry **opt-in (off by default)**; SponsorBlock opt-in with disclosure | **"Private by default."** |

## Quality & engineering (trust signals for the relaunch)

| Area | Improvement |
|------|-------------|
| Code review | Every feature passed a multi-agent adversarial review + CodeRabbit; all findings fixed |
| Reproducible builds | Pinned floating dependency; signed release pipeline |
| Fullscreen video | Hardened (transient bars, opaque backing, survives nav interruptions) |
| Honesty | Sets correct expectations: YouTube video ads & HD-DRM (Widevine L1) are platform limits, not promised |

## Relaunch verdict

**Relaunch-worthy — yes.** This is not a patch; it's three new capability pillars
(consent-gated video continuity, ad/pop-up blocking, premium identity) plus fixes to the old app's
worst flaws. Feature #1 alone gives the browser a clear media promise: the app should not
force-close passenger-confirmed video playback.

**Recommended sequence:** ship the **Expressive Aurora** redesign (new neon visual identity) BEFORE
the rename — so the new name arrives with a new *look*, not just new internals. Then rename +
relaunch with this table as the changelog/landing-page copy.

## Candidate features for the next (relaunch) version
- **Expressive Aurora** visual identity (neon accent on AMOLED) — the rename's visual anchor *(planned, Sprint A)*
- ViewModel + `WebView.saveState` (scroll/history/forms survive restarts) — *Sprint 0 foundation*
- Lazy tab loading / LRU (memory headroom on head units) — *Sprint 0*
- Credential Manager + "Sign in with Google" app identity (passkeys; recover Google Password Manager logins) — *Sprint 3*
- Per-site ad-block exceptions UI; "Manage exceptions" list
- Picture-in-Picture + in-player large-target controls
- New app name, icon, splash, start-page identity
- Passenger/non-driver consent gate before real video playback starts or resumes; muted previews stay non-blocking

## How to test the current build
See the chat / `docs/OPTIMIZATION_ROADMAP.md`. Short version: install the signed
`AABrowser-2.1-beta1.apk` from GitHub Releases (v2.1-beta1) — you do NOT need to wait for the PRs
to merge; the release already contains all of PR #1 + PR #2.

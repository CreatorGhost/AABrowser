# V3 Redesign — "unrecognizable" revamp plan

> Goal: revamp so thoroughly that the original creator wouldn't recognize it — a premium,
> neon, car-native media browser. Direction = **Expressive Aurora** (chosen) with a **neon**
> accent on true-black AMOLED. This table is the proposal to review BEFORE building.

## Name (recommendation)
Lead: **Aurova** — ties directly to the neon "Aurora" identity, short, brandable, no Tube/Car/
Google echo. Alternates if you want a different feel:
- **Nocturne** — cinematic true-black / night-drive vibe.
- **Driftway** — road + flow; warmer, less techy.
- **Idlecast** — playful wink at "parked + casting media".

*(Pick one; I'll verify trademark/Play/domain before locking. Everything below is name-agnostic.)*

## LOCKED DECISIONS (2026-06-14)
- **Name: Driftway** (verify trademark/domain before public relaunch).
- **Repo: rename existing** `CreatorGhost/AABrowser` → `Driftway` (GitHub redirects old links). Do the actual repo rename + applicationId/package/label/icon change as the rebrand step so current beta links aren't disrupted mid-flight. Keep GPLv3 + original attribution.
- **Control bar: hidden + swipe-up** — bar hidden by default; a FIXED always-visible bottom-edge handle + swipe-up (or tap handle) reveals Back·Home·Tabs·Menu. The trigger is always visible (no hunt); the bar itself stays out of the way.
- **Accent: electric blue** (vivid/saturated) on true-black AMOLED.
- Build via PRs; **every PR waits for CodeRabbit before merge**.

## Redesign table (Today → New)

| # | Area | Today (the old app) | New (V3) | Why — in-car rationale | Effort |
|---|------|---------------------|----------|------------------------|--------|
| 1 | **Identity** | "AA Browser", generic blue Material, wallpaper-tinted | New name + neon-Aurora brand; one bold accent on AMOLED black | A distinct, ownable identity; not "a creator's side project" | S |
| 2 | **App icon / splash** | Stock globe+car icon, plain launch box | New icon + a smooth neon splash (logo + subtle motion), not a bare box | First impression = premium, not hobbyist | S |
| 3 | **Home page** | "Quick Links" **box** with 6 plain cards | **Media Hub**: big neon tiles (YouTube/Netflix/Crunchyroll/Animetsu/Twitch) + a "Continue watching/listening" row + bold display headline over a contained mesh-gradient hero | The home screen IS the product identity; glanceable big tiles fit a car, not a form | L |
| 4 | **Primary controls** | Annoying three-dots FAB (auto-hides, 2-taps-deep, duplicated, relocatable) | **Docked bottom bar**: Back · Home · Tabs(live count) · Menu — fixed, ~76dp, always visible, one tap | Few/large/always-visible/unambiguous = the car-HMI rule; kills the "hunt for the button" problem | M |
| 5 | **Theme / color** | Blue (#1B6EF3) M3 + purple; Material-You overrides it | Neutral AMOLED surface ladder + ONE neon accent (active/focus/primary only); curated, never wallpaper-driven | On-brand every launch; true-black for night driving; sunlight contrast | M |
| 6 | **Typography** | Default Roboto, 12–15sp | Display face for headlines + humanist UI face; emphasized bold scale, ≥24sp on head units | Bold legible type = glanceable + "designed" | M |
| 7 | **Shape language** | Hardcoded 16/24/28dp corners | One squircle shape scale via theme overlays; signature morph on active tab/tile | Distinctive silhouette; stops looking "default Material" | M |
| 8 | **Motion** | None (instant cuts) | Spring/physics press feedback + fade/shared-axis panel transitions (short, calm) | "Smooth/premium" feel; calm = safe for a car | M |
| 9 | **Icons** | Mixed; Holo `ic_menu_*` (fixed) | One coherent Material Symbols family, accent-tinted | Visual consistency | S |
| 10 | **Tabs / bookmarks** | Plain rows, raw URLs (fixed→host) | Premium cards, logo-forward, tab-count badge | Glanceable, branded | M |
| 11 | **Settings** | 12+ identical dense bordered cards, BodySmall | Grouped, larger rows/switches, "About" collapses the long tail | Usable while parked; less scrolling | M |
| 12 | **Menu** | Everything 2 taps deep in one sheet | Trimmed to genuine overflow (Bookmarks/Share/Settings/New Tab/QR); primary nav now on the bar | Common actions one tap; menu = long tail only | M |
| 13 | **Flagship feature** | — | **Read-Aloud / Listen-to-Articles** (on-device TTS via the existing MediaSession) | The one premium feature usable WHILE DRIVING (audio); no rival has it | M |
| 14 | **Voice-first** | Speech bridge exists but buried | Large mic on the home/bar: "open YouTube", "search…", "go to…" | Avoids the laggy head-unit keyboard | S |
| 15 | **Persistence defaults** | FAB + URL bar hidden by default; 3s auto-hide | Controls + URL/security bar visible by default; auto-hide only in fullscreen video | Screen space isn't scarce parked; persistence beats hunting | S |

## Build order (each step build-verified + emulator-checked; PRs wait for CodeRabbit)
1. **Foundation theme**: neon AMOLED color identity + shape scale + type scale + fonts + icon family.
2. **Docked control bar** (replaces the FAB) + persistence defaults.
3. **Media-Hub home page** (premium tiles + Continue row + hero + voice mic) — replaces the box.
4. **Motion** layer (transitions + press springs).
5. **Read-Aloud TTS** flagship.
6. New **name/icon/splash** swap-in.
7. Settings polish.

## Constraints kept honest (from prior research)
- Driver video-in-motion stays blocked (platform); audio-while-driving is the durable win.
- Widevine SD cap; YouTube ads not durably blockable. Sideload-only distribution.

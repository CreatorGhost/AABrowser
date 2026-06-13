# Pop-up / annoyance / ad hardening plan (general sites — NOT YouTube)

> Decision: do NOT chase YouTube ads (engine-locked, SSAI-doomed). Instead make general-site
> ad/pop-up blocking genuinely strong — sketchy sites with pop-up/redirect storms are dangerous
> to deal with in a car. Everything below is in-app, View-based, no engine swap.

## What exists today
- `onCreateWindow` blocks programmatic (non-gesture) pop-ups; gesture pop-ups open as a tab (OAuth).
- Network ad/tracker blocking on subresources (StevenBlack/EasyList host data) + cosmetic CSS + Facebook hider + opt-in SponsorBlock.
- **Gaps:** no JS-dialog spam control, no `beforeunload` trap suppression, no auto-redirect/popunder guard, no annoyance filter lists, no "blocked" feedback, no settings toggle.

## Plan (Today → New)

| # | Layer | Today | New | Effort |
|---|-------|-------|-----|--------|
| 1 | **Programmatic pop-ups** | gesture-gated (good) | + cap rapid multi-pop-ups (popunder storm): allow 1 per user gesture, block bursts | S |
| 2 | **`window.open` storm guard (JS)** | none | document-start script: neutralize `window.open` from timers/no-gesture, block popunder `onclick`+`blur` tab-under tricks | M |
| 3 | **Auto-redirect / tab-under** | not blocked | in `shouldOverrideUrlLoading`, block cross-domain main-frame navigations with **no user gesture** (`request.hasGesture()`) that hit the ad/redirect block list; legit gesture redirects pass | M |
| 4 | **JS dialog spam** (`alert`/`confirm`/`prompt` loops) | shown unlimited | rate-limit per page; after N, offer "Block further dialogs from this site" (auto-dismiss the rest) | M |
| 5 | **`beforeunload` "Leave site?" trap** | shown | auto-allow leaving (suppress the hostage prompt) | S |
| 6 | **Annoyance/pop-up filter lists** | hosts + EasyList only | add AdGuard Annoyances + EasyList pop-up rules to the network block set | S |
| 7 | **Feedback** | silent | subtle "Blocked N pop-ups" chip/toast so the user knows + can allow this site | S |
| 8 | **Settings + per-site allow** | global ad-block toggle only | "Block pop-ups & redirects" toggle (default ON) + reuse per-host ad-block allowlist as the escape hatch | S |
| 9 | **Honest scope** | — | strong for general/fishy sites; explicitly NOT a YouTube ad blocker | — |

## Build order
1. JS dialog spam control + `beforeunload` suppression (WebChromeClient overrides) — biggest safety win, low risk.
2. `window.open` storm guard (document-start JS) + pop-up burst cap.
3. Auto-redirect / tab-under guard in `shouldOverrideUrlLoading` (gesture + block-list gated, conservative).
4. Annoyance filter lists + "blocked" feedback chip.
5. Settings toggle + per-site allow.
6. Build, emulator smoke-test, PR → **wait for CodeRabbit** → fix → merge → release new APK.

## Safety rules (don't break legit sites)
- Only block redirects/pop-ups with NO user gesture, or that match the ad/redirect list. A real tap-through must always work.
- Per-site allowlist is the escape hatch; global toggle off disables all of it.
- Never block the main-frame page the user explicitly navigated to.

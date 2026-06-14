# Passenger Video Playback Plan

## Status

Implemented on branch `feature/passenger-video-consent`.

Resolved decisions:
- Consent is per app session.
- `Continue as passenger` is available immediately.
- Muted autoplay previews do not trigger consent.
- User-started, audible/unmuted, or fullscreen video playback triggers consent.

## Web Research Confirmation

The approach is correct for the behavior Driftway can control inside its own process:

- Android's official car-app quality guidance treats video visibility while driving as a driver-distraction concern. It says video app UI must not be visible while driving for Play/Android Auto quality compliance, with behavior ultimately enforced by Android Auto / AAOS hosts and OEM policy.
- Android's official `WebView` docs say `onPause()` pauses extra WebView processing and `onResume()` resumes it. They also document `pauseTimers()` / `resumeTimers()` as global JavaScript/layout timer controls. Therefore, avoiding app-side `onPause()`/fullscreen teardown for consented video is the right app-level preservation point.
- Chrome's autoplay policy says muted autoplay is allowed, while autoplay with sound depends on user interaction/engagement. MDN similarly distinguishes audible media from inaudible media and recommends handling `play()` promise failures. Therefore, gating user-started, audible/unmuted, or fullscreen video while ignoring muted previews is the right consent boundary.
- This does not guarantee video if the Android Auto / AAOS host externally hides or blocks the surface. The implementation goal is only that Driftway itself does not voluntarily pause, close, or tear down passenger-confirmed video playback.

Sources reviewed:
- `https://developer.android.com/docs/quality-guidelines/car-app-quality`
- `https://developer.android.com/reference/android/webkit/WebView`
- `https://developer.chrome.com/blog/autoplay`
- `https://developer.mozilla.org/en-US/docs/Web/Media/Guides/Autoplay`

## Goal

Enable video playback to continue instead of being force-closed by Driftway when the car host sends motion-related lifecycle changes. Real playback must be consent-gated: before user-started, audible/unmuted, or fullscreen video starts or resumes, the app shows a clear confirmation that video is for a passenger/non-driver and must not be watched by the driver.

## Product Rule

- Video playback is allowed only after explicit passenger/non-driver consent.
- The consent prompt is mandatory before user-started, audible/unmuted, or fullscreen video starts or resumes.
- Audio and voice controls can continue without video consent.
- After consent, app lifecycle code must not voluntarily close fullscreen or pause the active media WebView just because the host backgrounds/stops the Activity.
- The app must not claim it can override Android Auto, AAOS, OEM, or regional restrictions outside the app process.

## Consent UX

- Trigger the consent prompt when a page attempts to play an HTML5 `video` element.
- Dialog copy should be direct: video is not for the driver; continue only if a passenger/non-driver is watching.
- Primary action: `I am a passenger / non-driver`.
- Secondary action: `Cancel video`.
- Consent should be session-scoped at first. Do not add a persistent "never ask again" option until the behavior is proven on a real head unit.
- If the user cancels, keep the video paused and allow audio/voice-only features to remain available.

## Implementation Plan

1. Add a video-consent bridge path in `MediaPlaybackBridge`. Done.

2. Extend the injected JS so `HTMLMediaElement.prototype.play` detects `VIDEO` elements before playback begins. Done.

3. If video consent has not been granted, prevent or immediately pause that video, post a native consent request, and remember the pending video element. Done.

4. Add native state in `MainActivity` for `isVideoPlaybackConsentGranted` and `pendingVideoConsentTabId`. Done.

5. Show a Material dialog from `MainActivity` when the JS bridge requests consent. Done.

6. On confirm, mark consent granted for the session and evaluate JS on the requesting tab to resume the pending video. Done.

7. On cancel, evaluate JS on the requesting tab to keep the pending video paused and clear the pending element. Done.

8. Add lifecycle helpers in `MainActivity`, for example `shouldPreserveActiveVideoPlayback()` and `activeMediaWebView()`. Done as `shouldPreserveConsentedVideoPlayback()` and `resumeConsentedVideoWebView()`.

9. Update `onPause()` so it does not call `exitFullscreen()` or `webView?.onPause()` when consented video playback is active or starting. Done.

10. Update `onStop()` so it does not unconditionally call `exitFullscreen()` when consented video playback is active. Done.

11. Keep the media tab resumed with `onResume()` and `resumeTimers()` while consented playback is active. Done.

12. Ensure explicit user pause, tab close, renderer crash, Activity destroy, and notification Stop still tear down playback normally. Done in code; still needs manual device verification.

13. Add string resources for the consent dialog title, message, confirm action, and cancel action. Done.

14. Update docs/comments after implementation to remove "pending" wording. Done.

## Verification

- Build: `./gradlew testDebugUnitTest assembleDebug`.
- Manual emulator check: open a test page with an HTML5 video, press play, verify the consent dialog appears before real playback.
- Manual cancel check: cancel the prompt, verify the video stays paused.
- Manual confirm check: confirm as passenger/non-driver, verify the video starts or resumes.
- Manual lifecycle check: while video is playing fullscreen, trigger `onPause()`/`onStop()` style interruptions and verify Driftway does not exit fullscreen or pause the active media WebView by itself.
- Manual regression check: user pause stays paused; closing the media tab stops the session; notification Stop still stops playback.
- Head-unit/DHU check: repeat the fullscreen playback flow during vehicle-motion lifecycle changes.

## Resolved Questions

- Consent resets per app session.
- Consent is required for user-started, audible/unmuted, or fullscreen video playback.
- Consent is requested when a page attempts real video playback, not when merely opening a known video site.

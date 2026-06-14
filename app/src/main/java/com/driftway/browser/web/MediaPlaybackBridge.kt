package com.driftway.browser.web

import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * Bridges HTML5 media playback in a WebView to the native side so the app can keep media
 * playing across host lifecycle changes and expose transport controls to the head unit / lock
 * screen.
 *
 * The injected JS ([INJECTION_JS]) watches `<video>`/`<audio>` play/pause/ended events and
 * `navigator.mediaSession.metadata`, and posts state to [onState]. Native transport actions are
 * routed back into the page by evaluating `window.__aaMediaControl.*` (see helpers below).
 *
 * [onState] is invoked on the WebView's JS/binder thread — callers must marshal to the UI thread.
 */
class MediaPlaybackBridge(
    private val onState: (MediaState) -> Unit,
    private val onVideoConsentRequested: (VideoConsentRequest) -> Unit = {}
) {
    data class MediaState(
        val state: String,        // "playing" | "paused" | "stopped"
        val title: String,
        val artist: String,
        val positionMs: Long,
        val durationMs: Long,
        val mediaType: String
    ) {
        val isVideo: Boolean get() = mediaType.equals("video", ignoreCase = true)
    }

    data class VideoConsentRequest(
        val reason: String,
        val title: String,
        val host: String
    )

    @JavascriptInterface
    fun onState(json: String?) {
        json ?: return
        // NOTE: title/artist are page-controlled strings used only as notification text. We do
        // NOT plumb the page's artwork URL: fetching a page-supplied URL natively would be an
        // SSRF sink (e.g. http://192.168.1.1/admin) on a head unit. Add artwork later only with
        // an https-only/same-origin allowlist + off-thread decode.
        val parsed = runCatching {
            val o = JSONObject(json)
            MediaState(
                state = o.optString("state", "stopped"),
                title = o.optString("title", ""),
                artist = o.optString("artist", ""),
                positionMs = o.optLong("position", 0L).coerceAtLeast(0L),
                durationMs = o.optLong("duration", 0L).coerceAtLeast(0L),
                mediaType = o.optString("mediaType", "")
            )
        }.getOrNull() ?: return
        onState(parsed)
    }

    @JavascriptInterface
    fun requestVideoConsent(json: String?) {
        val parsed = runCatching {
            val o = if (json.isNullOrBlank()) JSONObject() else JSONObject(json)
            VideoConsentRequest(
                reason = o.optString("reason", "play"),
                title = o.optString("title", ""),
                host = o.optString("host", "")
            )
        }.getOrElse {
            VideoConsentRequest(reason = "play", title = "", host = "")
        }
        onVideoConsentRequested(parsed)
    }

    companion object {
        const val INTERFACE_NAME = "AABrowserMedia"

        fun playJs(): String = "window.__aaMediaControl && window.__aaMediaControl.play();"
        fun pauseJs(): String = "window.__aaMediaControl && window.__aaMediaControl.pause();"
        fun stopJs(): String = "window.__aaMediaControl && window.__aaMediaControl.stop();"
        fun seekJs(positionMs: Long): String =
            "window.__aaMediaControl && window.__aaMediaControl.seek($positionMs);"
        fun grantVideoConsentJs(): String =
            "window.__aaVideoConsent && window.__aaVideoConsent.grant();"
        fun markVideoConsentGrantedJs(): String =
            "window.__aaVideoConsent && window.__aaVideoConsent.markGranted();"
        fun cancelVideoConsentJs(): String =
            "window.__aaVideoConsent && window.__aaVideoConsent.cancel();"

        // Injected on every page load (see ConfiguredWebView.onPageFinished). Guarded so it only
        // installs once per document. Uses capture-phase listeners because media events do not bubble.
        val INJECTION_JS = """
            (function() {
              if (window.__aaMediaInit) return;
              window.__aaMediaInit = true;
              var active = null;
              var videoConsentGranted = false;
              var pendingVideo = null;
              var pendingPlayResolve = null;
              var pendingPlayReject = null;
              var lastUserGestureAt = 0;
              var mediaProto = (typeof HTMLMediaElement !== 'undefined') ? HTMLMediaElement.prototype : null;
              var nativePlay = mediaProto && mediaProto.play;
              function now() { return Date.now ? Date.now() : new Date().getTime(); }
              function markUserGesture(e) { if (!e || e.isTrusted !== false) lastUserGestureAt = now(); }
              ['pointerdown','touchstart','click','keydown'].forEach(function(name) {
                document.addEventListener(name, markUserGesture, true);
              });
              function isRecentUserGesture() { return now() - lastUserGestureAt < 1500; }
              function isVideo(el) { return el && el.tagName === 'VIDEO'; }
              function isAudible(el) { return !!(el && !el.muted && el.volume > 0); }
              function isFullscreen(el) {
                try {
                  return document.fullscreenElement === el ||
                    document.webkitFullscreenElement === el ||
                    !!el.webkitDisplayingFullscreen;
                } catch (e) { return false; }
              }
              function shouldGateVideo(el) {
                if (!isVideo(el) || videoConsentGranted) return false;
                return isRecentUserGesture() || isAudible(el) || isFullscreen(el);
              }
              function shouldTrackMedia(el) {
                if (!isVideo(el)) return true;
                if (el === active) return true;
                return isAudible(el) || isFullscreen(el) || isRecentUserGesture();
              }
              function rejectPending() {
                var reject = pendingPlayReject;
                pendingPlayResolve = null; pendingPlayReject = null;
                if (reject) {
                  try { reject(new DOMException('Video playback requires passenger consent', 'NotAllowedError')); }
                  catch (e) { reject(new Error('Video playback requires passenger consent')); }
                }
              }
              function resolvePending(value) {
                var resolve = pendingPlayResolve;
                pendingPlayResolve = null; pendingPlayReject = null;
                if (resolve) { try { resolve(value); } catch (e) {} }
              }
              function requestConsent(el, reason) {
                pendingVideo = el;
                try { el.pause(); } catch (e) {}
                try {
                  AABrowserMedia.requestVideoConsent(JSON.stringify({
                    reason: reason || 'play',
                    title: document.title || '',
                    host: location.hostname || ''
                  }));
                } catch (e) {}
                return new Promise(function(resolve, reject) {
                  pendingPlayResolve = resolve;
                  pendingPlayReject = reject;
                });
              }
              if (nativePlay && mediaProto && !mediaProto.__aaVideoConsentWrapped) {
                mediaProto.__aaVideoConsentWrapped = true;
                mediaProto.play = function() {
                  if (shouldGateVideo(this)) return requestConsent(this, 'play');
                  return nativePlay.apply(this, arguments);
                };
              }
              function pick() {
                var meds = document.querySelectorAll('video, audio');
                for (var i = 0; i < meds.length; i++) {
                  if (!meds[i].paused && !meds[i].ended && meds[i].currentTime > 0 && shouldTrackMedia(meds[i])) return meds[i];
                }
                if (active && shouldTrackMedia(active)) return active;
                return meds.length ? meds[0] : null;
              }
              function meta() {
                try {
                  var m = navigator.mediaSession && navigator.mediaSession.metadata;
                  if (m && m.title) {
                    return { title: m.title, artist: m.artist || '' };
                  }
                } catch (e) {}
                return { title: document.title || '', artist: location.hostname || '' };
              }
              function report(state) {
                var el = active;
                var dur = (el && isFinite(el.duration)) ? Math.round(el.duration * 1000) : 0;
                var pos = (el && isFinite(el.currentTime)) ? Math.round(el.currentTime * 1000) : 0;
                var md = meta();
                try {
                  AABrowserMedia.onState(JSON.stringify({
                    state: state, title: md.title, artist: md.artist,
                    position: pos, duration: dur,
                    mediaType: el && el.tagName ? el.tagName.toLowerCase() : ''
                  }));
                } catch (e) {}
              }
              // Debounce timers so transient buffering/seek/segment/ad-boundary events on custom
              // players (Netflix/Twitch/Crunchyroll/YouTube ads) don't flip our state spuriously.
              var pauseTimer = null, endedTimer = null;
              function clearTimers() {
                if (pauseTimer) { clearTimeout(pauseTimer); pauseTimer = null; }
                if (endedTimer) { clearTimeout(endedTimer); endedTimer = null; }
              }
              function anyPlaying() {
                var meds = document.querySelectorAll('video, audio');
                for (var i = 0; i < meds.length; i++) {
                  if (!meds[i].paused && !meds[i].ended && shouldTrackMedia(meds[i])) return meds[i];
                }
                return null;
              }
              document.addEventListener('play', function(e) {
                var t = e.target;
                if (t && (t.tagName === 'VIDEO' || t.tagName === 'AUDIO')) {
                  if (shouldGateVideo(t)) { requestConsent(t, 'play-event'); return; }
                  if (!shouldTrackMedia(t)) return;
                  clearTimers(); active = t; report('playing');
                }
              }, true);
              document.addEventListener('playing', function(e) {
                var t = e.target;
                if (t && (t.tagName === 'VIDEO' || t.tagName === 'AUDIO')) {
                  if (shouldGateVideo(t)) { requestConsent(t, 'playing-event'); return; }
                  if (!shouldTrackMedia(t)) return;
                  clearTimers(); active = t; report('playing');
                }
              }, true);
              document.addEventListener('volumechange', function(e) {
                var t = e.target;
                if (t && shouldGateVideo(t) && !t.paused && !t.ended) requestConsent(t, 'unmuted');
              }, true);
              document.addEventListener('fullscreenchange', function() {
                var el = document.fullscreenElement || document.webkitFullscreenElement;
                if (shouldGateVideo(el) && !el.paused && !el.ended) requestConsent(el, 'fullscreen');
              }, true);
              document.addEventListener('webkitfullscreenchange', function() {
                var el = document.webkitFullscreenElement || document.fullscreenElement;
                if (shouldGateVideo(el) && !el.paused && !el.ended) requestConsent(el, 'fullscreen');
              }, true);
              // A 'pause' is only real if playback doesn't resume shortly (rebuffering, ABR/quality
              // switches, seeking and DRM key rotation all fire pause->play pairs).
              document.addEventListener('pause', function(e) {
                if (e.target !== active) return;
                if (pauseTimer) clearTimeout(pauseTimer);
                pauseTimer = setTimeout(function() {
                  pauseTimer = null;
                  if (active && active.paused && !active.ended) report('paused');
                }, 900);
              }, true);
              // 'ended' is a soft end-of-item: another element (next video, next episode, post-ad
              // content) may start. Only a real stop if nothing is playing after a short grace.
              document.addEventListener('ended', function(e) {
                if (e.target !== active) return;
                if (endedTimer) clearTimeout(endedTimer);
                endedTimer = setTimeout(function() {
                  endedTimer = null;
                  var next = anyPlaying();
                  if (next) { clearTimers(); active = next; report('playing'); }
                  else report('stopped');
                }, 1500);
              }, true);
              // Heartbeat: re-point 'active' (ad->content swap, SPA video changes) and keep state live.
              setInterval(function() {
                if (active && !active.paused && !active.ended && shouldTrackMedia(active)) { report('playing'); return; }
                var p = anyPlaying();
                if (p) { clearTimers(); active = p; report('playing'); }
              }, 3000);
              window.__aaMediaControl = {
                play: function() { clearTimers(); var el = pick(); if (el) { active = el; el.play(); } },
                pause: function() { clearTimers(); var el = active || pick(); if (el) el.pause(); report('paused'); },
                // Stop must drive a full teardown: pause, then report 'stopped' so the native
                // side abandons audio focus (a plain pause would only report 'paused').
                stop: function() { clearTimers(); var el = active || pick(); if (el) el.pause(); report('stopped'); active = null; },
                seek: function(ms) { if (active && isFinite(active.duration)) active.currentTime = ms / 1000; }
              };
              window.__aaVideoConsent = {
                grant: function() {
                  videoConsentGranted = true;
                  var el = pendingVideo;
                  pendingVideo = null;
                  if (!el) { resolvePending(); return; }
                  active = el;
                  try {
                    var p = nativePlay ? nativePlay.call(el) : el.play();
                    if (p && p.then) {
                      p.then(function(value) { resolvePending(value); }).catch(function() { rejectPending(); });
                    } else {
                      resolvePending();
                    }
                  } catch (e) { rejectPending(); }
                },
                markGranted: function() { videoConsentGranted = true; },
                cancel: function() {
                  var el = pendingVideo;
                  pendingVideo = null;
                  if (el) { try { el.pause(); } catch (e) {} }
                  rejectPending();
                },
                isGranted: function() { return videoConsentGranted; }
              };
            })();
        """.trimIndent()

        // Spoof the Page Visibility API. Host lifecycle changes can set document.hidden=true, and
        // YouTube/Netflix/Twitch players listen for that and auto-pause. Keeping the page reported
        // as visible lets consent-confirmed media continue. Injected early (onPageStarted) so it
        // runs before the site's player reads visibility.
        val VISIBILITY_SPOOF_JS = """
            (function(){
              if (window.__aaVisInit) return; window.__aaVisInit = true;
              function def(o,p,v){ try { Object.defineProperty(o,p,{configurable:true,get:function(){return v;}}); } catch(e){} }
              def(document,'hidden',false); def(document,'webkitHidden',false);
              def(document,'visibilityState','visible'); def(document,'webkitVisibilityState','visible');
              function swallow(e){ e.stopImmediatePropagation(); }
              document.addEventListener('visibilitychange', swallow, true);
              document.addEventListener('webkitvisibilitychange', swallow, true);
            })();
        """.trimIndent()
    }
}

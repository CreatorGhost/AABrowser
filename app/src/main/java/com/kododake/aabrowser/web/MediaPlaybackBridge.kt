package com.kododake.aabrowser.web

import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * Bridges HTML5 media playback in a WebView to the native side so the app can keep audio
 * playing in the background (e.g. when driving starts) and expose transport controls to the
 * head unit / lock screen.
 *
 * The injected JS ([INJECTION_JS]) watches `<video>`/`<audio>` play/pause/ended events and
 * `navigator.mediaSession.metadata`, and posts state to [onState]. Native transport actions are
 * routed back into the page by evaluating `window.__aaMediaControl.*` (see helpers below).
 *
 * [onState] is invoked on the WebView's JS/binder thread — callers must marshal to the UI thread.
 */
class MediaPlaybackBridge(
    private val onState: (MediaState) -> Unit
) {
    data class MediaState(
        val state: String,        // "playing" | "paused" | "stopped"
        val title: String,
        val artist: String,
        val positionMs: Long,
        val durationMs: Long
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
                durationMs = o.optLong("duration", 0L).coerceAtLeast(0L)
            )
        }.getOrNull() ?: return
        onState(parsed)
    }

    companion object {
        const val INTERFACE_NAME = "AABrowserMedia"

        fun playJs(): String = "window.__aaMediaControl && window.__aaMediaControl.play();"
        fun pauseJs(): String = "window.__aaMediaControl && window.__aaMediaControl.pause();"
        fun stopJs(): String = "window.__aaMediaControl && window.__aaMediaControl.stop();"
        fun seekJs(positionMs: Long): String =
            "window.__aaMediaControl && window.__aaMediaControl.seek($positionMs);"

        // Injected on every page load (see ConfiguredWebView.onPageFinished). Guarded so it only
        // installs once per document. Uses capture-phase listeners because media events do not bubble.
        val INJECTION_JS = """
            (function() {
              if (window.__aaMediaInit) return;
              window.__aaMediaInit = true;
              var active = null;
              function pick() {
                var meds = document.querySelectorAll('video, audio');
                for (var i = 0; i < meds.length; i++) {
                  if (!meds[i].paused && !meds[i].ended && meds[i].currentTime > 0) return meds[i];
                }
                return active || (meds.length ? meds[0] : null);
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
                    position: pos, duration: dur
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
                  if (!meds[i].paused && !meds[i].ended) return meds[i];
                }
                return null;
              }
              document.addEventListener('play', function(e) {
                var t = e.target;
                if (t && (t.tagName === 'VIDEO' || t.tagName === 'AUDIO')) {
                  clearTimers(); active = t; report('playing');
                }
              }, true);
              document.addEventListener('playing', function(e) {
                var t = e.target;
                if (t && (t.tagName === 'VIDEO' || t.tagName === 'AUDIO')) {
                  clearTimers(); active = t; report('playing');
                }
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
                if (active && !active.paused && !active.ended) { report('playing'); return; }
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
            })();
        """.trimIndent()
    }
}

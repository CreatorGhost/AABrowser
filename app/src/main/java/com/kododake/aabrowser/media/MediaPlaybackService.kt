package com.kododake.aabrowser.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import com.kododake.aabrowser.MainActivity
import com.kododake.aabrowser.R

/**
 * Foreground service that keeps the process alive (with a MediaStyle notification) while HTML5
 * media plays in the WebView, so audio continues when the app is backgrounded / the car host
 * hides the UI during driving. It owns no playback itself — it mirrors the [MediaSessionCompat]
 * created by [MediaSessionController] and routes notification button taps back through the
 * session's transport controls (which the controller forwards into the page via JS).
 */
class MediaPlaybackService : Service() {

    private var controller: MediaControllerCompat? = null
    private var controllerCallback: MediaControllerCompat.Callback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Bind the controller FIRST (so the notification can reflect real state)...
        val token = intent?.let {
            IntentCompat.getParcelableExtra(it, EXTRA_TOKEN, MediaSessionCompat.Token::class.java)
        }
        if (token != null && controller == null) {
            bindController(token)
        }

        // ...then call startForeground UNCONDITIONALLY and before any early return, so a
        // startForegroundService() can never miss the ~5s startForeground deadline (which would
        // throw ForegroundServiceDidNotStartInTimeException). buildNotification() tolerates a
        // null controller. The typed overload satisfies the API 34+ foreground-service-type check.
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )

        when (intent?.action) {
            ACTION_PLAY -> controller?.transportControls?.play()
            ACTION_PAUSE -> controller?.transportControls?.pause()
            ACTION_STOP -> {
                controller?.transportControls?.stop()
                stopPlaybackService()
                return START_NOT_STICKY
            }
        }

        if (controller == null) {
            stopPlaybackService()
            return START_NOT_STICKY
        }

        val state = controller?.playbackState?.state
        if (state == PlaybackStateCompat.STATE_STOPPED || state == PlaybackStateCompat.STATE_NONE) {
            stopPlaybackService()
        }
        return START_NOT_STICKY
    }

    private fun bindController(token: MediaSessionCompat.Token) {
        val c = runCatching { MediaControllerCompat(this, token) }.getOrNull() ?: return
        val cb = object : MediaControllerCompat.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                val s = state?.state
                if (s == PlaybackStateCompat.STATE_STOPPED || s == PlaybackStateCompat.STATE_NONE) {
                    stopPlaybackService()
                } else {
                    notificationManager().notify(NOTIFICATION_ID, buildNotification())
                }
            }

            override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
                notificationManager().notify(NOTIFICATION_ID, buildNotification())
            }

            override fun onSessionDestroyed() {
                stopPlaybackService()
            }
        }
        c.registerCallback(cb)
        controller = c
        controllerCallback = cb
    }

    private fun buildNotification(): Notification {
        val c = controller
        val playbackState = c?.playbackState
        val metadata = c?.metadata
        val isPlaying = playbackState?.state == PlaybackStateCompat.STATE_PLAYING

        val title = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() } ?: getString(R.string.app_name)
        val artist = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotBlank() }
        val artwork = metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                R.drawable.media_pause,
                getString(R.string.media_pause),
                servicePendingIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                R.drawable.media_play,
                getString(R.string.media_play),
                servicePendingIntent(ACTION_PLAY)
            )
        }
        val stopAction = NotificationCompat.Action(
            R.drawable.media_stop,
            getString(R.string.media_stop),
            servicePendingIntent(ACTION_STOP)
        )

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(c?.sessionToken)
            .setShowActionsInCompactView(0, 1)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_media_notification)
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(artwork)
            .setContentIntent(contentIntent)
            .setStyle(mediaStyle)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .addAction(playPauseAction)
            .addAction(stopAction)
            .build()
    }

    private fun servicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MediaPlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun stopPlaybackService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.media_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            description = getString(R.string.media_notification_channel_description)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun onDestroy() {
        controllerCallback?.let { controller?.unregisterCallback(it) }
        controller = null
        controllerCallback = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "media_playback"
        private const val NOTIFICATION_ID = 2025
        private const val EXTRA_TOKEN = "media_session_token"
        private const val ACTION_PLAY = "com.kododake.aabrowser.media.PLAY"
        private const val ACTION_PAUSE = "com.kododake.aabrowser.media.PAUSE"
        private const val ACTION_STOP = "com.kododake.aabrowser.media.STOP"

        fun start(context: Context, token: MediaSessionCompat.Token) {
            val intent = Intent(context, MediaPlaybackService::class.java)
                .putExtra(EXTRA_TOKEN, token)
            // A background start can be refused (ForegroundServiceStartNotAllowedException, a
            // subclass of IllegalStateException) — e.g. autoplay advances a playlist while the
            // app is backgrounded. Swallow it: the MediaSession stays active and the service is
            // re-started on the next foreground entry. Better a missing notification than a crash.
            runCatching { context.startForegroundService(intent) }
        }

        // No explicit stop(): the controller sets the session to STOPPED (or releases it), and
        // the service's MediaControllerCompat.Callback (onPlaybackStateChanged / onSessionDestroyed)
        // tears the foreground service down. This avoids a restricted background startService call.
    }
}

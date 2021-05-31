package snnafi.podplay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import snnafi.podplay.R
import snnafi.podplay.ui.PodcastActivity
import java.net.URL

class PodplayMediaService : MediaBrowserServiceCompat(), PodplayMediaCallback.PodplayMediaListener {

    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        createMediaSession()
    }


    /**
     * Called to get the root information for browsing by a particular client.
     *
     *
     * The implementation should verify that the client package has permission
     * to access browse media information before returning the root id; it
     * should return null if the client is not allowed to access this
     * information.
     *
     *
     * @param clientPackageName The package name of the application which is
     * requesting access to browse media.
     * @param clientUid The uid of the application which is requesting access to
     * browse media.
     * @param rootHints An optional bundle of service-specific arguments to send
     * to the media browse service when connecting and retrieving the
     * root id for browsing, or null if none. The contents of this
     * bundle may affect the information returned when browsing.
     * @return The [BrowserRoot] for accessing this app's content or null.
     * @see BrowserRoot.EXTRA_RECENT
     *
     * @see BrowserRoot.EXTRA_OFFLINE
     *
     * @see BrowserRoot.EXTRA_SUGGESTED
     */
    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        return MediaBrowserServiceCompat.BrowserRoot(PODPLAY_EMPTY_ROOT_MEDIA_ID, null)
    }

    /**
     * Called to get information about the children of a media item.
     *
     *
     * Implementations must call [result.sendResult][Result.sendResult]
     * with the list of children. If loading the children will be an expensive
     * operation that should be performed on another thread,
     * [result.detach][Result.detach] may be called before returning from
     * this function, and then [result.sendResult][Result.sendResult]
     * called when the loading is complete.
     *
     *
     * In case the media item does not have any children, call [Result.sendResult]
     * with an empty list. When the given `parentId` is invalid, implementations must
     * call [result.sendResult][Result.sendResult] with `null`, which will invoke
     * [MediaBrowserCompat.SubscriptionCallback.onError].
     *
     *
     * @param parentId The id of the parent media item whose children are to be
     * queried.
     * @param result The Result to send the list of children to.
     */
    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        if (parentId.equals(PODPLAY_EMPTY_ROOT_MEDIA_ID)) {
            result.sendResult(null)
        }
    }

    fun createMediaSession() {

        mediaSession = MediaSessionCompat(this, "PodplayMediaService")
        sessionToken = mediaSession.sessionToken
        val callBack = PodplayMediaCallback(this, mediaSession)
        mediaSession.setCallback(callBack)
        callBack.listener = this@PodplayMediaService
    }

    companion object {
        private const val PODPLAY_EMPTY_ROOT_MEDIA_ID =
            "podplay_empty_root_media_id"
        private const val PLAYER_CHANNEL_ID = "podplay_player_channel"
        private const val NOTIFICATION_ID = 1
    }

    private fun getPausePlayActions():
            Pair<NotificationCompat.Action, NotificationCompat.Action> {
        val pauseAction = NotificationCompat.Action(
            R.drawable.ic_pause_white, getString(R.string.pause),
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                this,
                PlaybackStateCompat.ACTION_PAUSE
            )
        )
        val playAction = NotificationCompat.Action(
            R.drawable.ic_play_arrow_white, getString(R.string.play),
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                this,
                PlaybackStateCompat.ACTION_PLAY
            )
        )
        return Pair(pauseAction, playAction)
    }

    private fun isPlaying(): Boolean {
        if (mediaSession.controller.playbackState != null) {
            return mediaSession.controller.playbackState.state ==
                    PlaybackStateCompat.STATE_PLAYING
        } else {
            return false
        }
    }

    private fun getNotificationIntent(): PendingIntent {
        val openActivityIntent = Intent(
            this,
            PodcastActivity::class.java
        )
        openActivityIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        return PendingIntent.getActivity(
            this@PodplayMediaService, 0, openActivityIntent,
            PendingIntent.FLAG_CANCEL_CURRENT
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
        if (notificationManager.getNotificationChannel
                (PLAYER_CHANNEL_ID) == null
        ) {
            val channel =
                NotificationChannel(PLAYER_CHANNEL_ID, "Player", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }

    // 1
    private fun createNotification(
        mediaDescription:
        MediaDescriptionCompat,
// 2
        bitmap: Bitmap?
    ): Notification {
        val notificationIntent = getNotificationIntent()
// 3
        val (pauseAction, playAction) = getPausePlayActions()
// 4
        val notification = NotificationCompat.Builder(
            this@PodplayMediaService, PLAYER_CHANNEL_ID
        )
// 5
        notification
            .setContentTitle(mediaDescription.title)
            .setContentText(mediaDescription.subtitle)
            .setLargeIcon(bitmap)
            .setContentIntent(notificationIntent)
            .setDeleteIntent(
                MediaButtonReceiver.buildMediaButtonPendingIntent
                    (this, PlaybackStateCompat.ACTION_STOP)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSmallIcon(R.drawable.ic_episode_icon)
            .addAction(if (isPlaying()) pauseAction else playAction)
            .setStyle(
// 6
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                            this, PlaybackStateCompat.ACTION_STOP
                        )
                    )
            )
        return notification.build()
    }

    private fun displayNotification() {
        // 1
        if (mediaSession.controller.metadata == null) {
            return
        }
// 2
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
// 3
        val mediaDescription =
            mediaSession.controller.metadata.description
// 4
        GlobalScope.launch {
// 5
            val iconUrl = URL(mediaDescription.iconUri.toString())
            // 6
            val bitmap =
                BitmapFactory.decodeStream(iconUrl.openStream())
// 7
            val notification = createNotification(
                mediaDescription,
                bitmap
            )
// 8
            ContextCompat.startForegroundService(
                this@PodplayMediaService,
                Intent(
                    this@PodplayMediaService,
// 9
                    PodplayMediaService::class.java
                )
            )
            startForeground(
                PodplayMediaService.NOTIFICATION_ID,
                notification
            )
        }
    }

    override fun onStateChanged() {
        displayNotification()
    }

    override fun onStopPlaying() {
        stopSelf()
        stopForeground(true)
    }

    override fun onPausePlaying() {
        stopForeground(false)
    }


    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        mediaSession.controller.transportControls.stop()
    }
}
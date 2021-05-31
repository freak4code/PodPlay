package snnafi.podplay.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import snnafi.podplay.R
import snnafi.podplay.db.PodPlayDatabase
import snnafi.podplay.repository.PodcastRepo
import snnafi.podplay.service.RssFeedService
import snnafi.podplay.ui.PodcastActivity

class EpisodeUpdateWorker(
    context: Context, params:
    WorkerParameters
) : CoroutineWorker(context, params) {
    /**
     * A suspending method to do your work.  This function runs on the coroutine context specified
     * by [coroutineContext].
     * <p>
     * A CoroutineWorker is given a maximum of ten minutes to finish its execution and return a
     * [ListenableWorker.Result].  After this time has expired, the worker will be signalled to
     * stop.
     *
     * @return The [ListenableWorker.Result] of the result of the background work; note that
     * dependent work will not execute if you return [ListenableWorker.Result.failure]
     */
    override suspend fun doWork(): Result {
        coroutineScope {
            val job = async {
                val db = PodPlayDatabase.getInstance(applicationContext)
                val repo = PodcastRepo(RssFeedService.instance as RssFeedService, db.podcastDao())
                repo.updatePodcastEpisodes {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        createNotificationChannel()
                    }
                    for (podcastUpdate in it) {
                        displayNotification(podcastUpdate)
                    }
                }

            }
            job.await()

        }
        return Result.success()

    }


    companion object {
        const val EPISODE_CHANNEL_ID = "podplay_episodes_channel"
        const val EXTRA_FEED_URL = "PodcastFeedUrl"
    }

    // 1
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
// 2
        val notificationManager =
            applicationContext.getSystemService(NOTIFICATION_SERVICE) as
                    NotificationManager
// 3
        if (notificationManager.getNotificationChannel(EPISODE_CHANNEL_ID) == null) {// 4
            val channel = NotificationChannel(
                EPISODE_CHANNEL_ID,
                "Episodes", NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun displayNotification(
        podcastInfo:
        PodcastRepo.PodcastUpdateInfo
    ) {
// 1
        val contentIntent = Intent(
            applicationContext,
            PodcastActivity::class.java
        )
        contentIntent.putExtra(EXTRA_FEED_URL, podcastInfo.feedUrl)
        val pendingContentIntent =
            PendingIntent.getActivity(
                applicationContext, 0,
                contentIntent, PendingIntent.FLAG_UPDATE_CURRENT
            )
// 2
        val notification =
            NotificationCompat.Builder(
                applicationContext,
                EPISODE_CHANNEL_ID
            )
                .setSmallIcon(R.drawable.ic_episode_icon)
                .setContentTitle(
                    applicationContext.getString(
                        R.string.episode_notification_title
                    )
                )
                .setContentText(
                    applicationContext.getString(
                        R.string.episode_notification_text,
                        podcastInfo.newCount, podcastInfo.name
                    )
                )
                .setNumber(podcastInfo.newCount)
                .setAutoCancel(true)
                .setContentIntent(pendingContentIntent)
                .build()
// 4
        val notificationManager =
            applicationContext.getSystemService(NOTIFICATION_SERVICE)
                    as NotificationManager
        // 5
        notificationManager.notify(podcastInfo.name, 0, notification)
    }
}

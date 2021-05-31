package snnafi.podplay.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import snnafi.podplay.model.Episode
import snnafi.podplay.model.Podcast
import snnafi.podplay.repository.PodcastRepo
import snnafi.podplay.util.DateUtils
import snnafi.podplay.viewmodel.SearchViewModel.PodcastSummaryViewData
import java.util.*

class PodcastViewModel(application: Application) : AndroidViewModel(application) {

    var podcastRepo: PodcastRepo? = null
    var activePodcastViewData: PodcastViewData? = null
    var activePodcast: Podcast? = null
    var livePodcastData: LiveData<List<PodcastSummaryViewData>>? = null
    var activeEpisodeViewData: EpisodeViewData? = null

    data class PodcastViewData(
        var subscribed: Boolean = false,
        var feedTitle: String? = "",
        var feedUrl: String? = "",
        var feedDesc: String? = "",
        var imageUrl: String? = "",
        var episodes: List<EpisodeViewData>
    )

    data class EpisodeViewData(
        var guid: String? = "",
        var title: String? = "",
        var description: String? = "",
        var mediaUrl: String? = "",
        var releaseDate: Date? = null,
        var duration: String? = "",
        var isVideo: Boolean = false
    )

    private fun episodesToEpisodesView(episodes: List<Episode>):
            List<EpisodeViewData> {
        return episodes.map {
            val isVideo = it.mimeType.startsWith("video")
            EpisodeViewData(
                it.guid, it.title, it.description,
                it.mediaUrl, it.releaseDate, it.duration, isVideo
            )
        }
    }

    private fun podcastToPodcastView(podcast: Podcast):
            PodcastViewData {
        return PodcastViewData(
            podcast.id != null,
            podcast.feedTitle,
            podcast.feedUrl,
            podcast.feedDesc,
            podcast.imageUrl,
            episodesToEpisodesView(podcast.episodes)
        )
    }

    // 1
    fun getPodcast(
        podcastSummaryViewData: PodcastSummaryViewData,
        callback: (PodcastViewData?) -> Unit
    ) {
// 2
        val repo = podcastRepo ?: return
        val feedUrl = podcastSummaryViewData.feedUrl ?: return
        // 3
        repo.getPodcast(feedUrl) {
            it?.let {
// 5
                it.feedTitle = podcastSummaryViewData.name ?: ""
                // 6
                it.imageUrl = podcastSummaryViewData.imageUrl ?: ""
                // 7
                activePodcastViewData = podcastToPodcastView(it)
// 8
                activePodcast = it
                callback(activePodcastViewData)
            }
        }
    }

    fun saveActivePodcast() {
        val repo = podcastRepo ?: return
        activePodcast?.let {
            //   it.episodes = it.episodes.drop(1) tesing notification
            repo.save(it)
        }
    }

    private fun podcastToSummaryView(podcast: Podcast):
            PodcastSummaryViewData {
        return PodcastSummaryViewData(
            podcast.feedTitle,
            DateUtils.dateToShortDate(podcast.lastUpdated),
            podcast.imageUrl,
            podcast.feedUrl
        )
    }

    fun getPodcast(): LiveData<List<PodcastSummaryViewData>>? {
        val repo = podcastRepo ?: return null
        if (livePodcastData == null) {
            val liveData = repo.getAll()
            livePodcastData = Transformations.map(liveData) {
                it.map {
                    podcastToSummaryView(it)
                }
            }

        }

        return livePodcastData
    }

    fun deleteActivePodcast() {
        val repo = podcastRepo ?: return
        activePodcast?.let {
            repo.deletePodcast(it)
        }

    }

    fun setActivePodcast(
        feedUrl: String, callback:
            (PodcastSummaryViewData?) -> Unit
    ) {
        val repo = podcastRepo ?: return
        repo.getPodcast(feedUrl) {
            if (it == null) {
                callback(null)
            } else {
                activePodcastViewData = podcastToPodcastView(it)
                activePodcast = it
                callback(podcastToSummaryView(it))
            }
        }
    }
}

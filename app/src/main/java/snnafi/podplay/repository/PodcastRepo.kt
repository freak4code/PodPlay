package snnafi.podplay.repository

import android.util.Log
import androidx.lifecycle.LiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import snnafi.podplay.db.PodcastDao
import snnafi.podplay.model.Episode
import snnafi.podplay.model.Podcast
import snnafi.podplay.service.RssFeedResponse
import snnafi.podplay.service.RssFeedService
import snnafi.podplay.util.DateUtils

class PodcastRepo(
    private var rssFeedService: RssFeedService,
    private var podcastDao: PodcastDao
) {


    fun getPodcast(feedUrl: String, callBack: (Podcast?) -> Unit) {
        GlobalScope.launch {
            val podcast = podcastDao.loadPodcast(feedUrl)

            if (podcast != null) {
                podcast.id?.let {

                    podcast.episodes = podcastDao.loadEpisodes(it)


                    GlobalScope.launch(Dispatchers.Main) {
                        callBack(podcast)
                    }
                }

            } else {

                rssFeedService.getFeed(feedUrl) {
                    var podcast: Podcast? = null
                    if (it != null) {
                        podcast = rssResponseToPodcast(feedUrl, "", it)
                    }
                    GlobalScope.launch(Dispatchers.Main) {
                        callBack(podcast)
                    }

                }
            }
        }

    }

    private fun rssItemsToEpisodes(
        episodeResponses:
        List<RssFeedResponse.EpisodeResponse>
    ): List<Episode> {
        return episodeResponses.map {
            Episode(
                it.guid ?: "",
                null,
                it.title ?: "",
                it.description ?: "",
                it.url ?: "",
                it.type ?: "",
                DateUtils.xmlDateToDate(it.pubDate),
                it.duration ?: ""
            )
        }
    }

    private fun rssResponseToPodcast(
        feedUrl: String, imageUrl:
        String, rssResponse: RssFeedResponse
    ): Podcast? {
// 1
        val items = rssResponse.episodes ?: return null
        // 2
        val description = if (rssResponse.description == "")
            rssResponse.summary else rssResponse.description
        // 3
        return Podcast(
            null,
            feedUrl, rssResponse.title, description,
            imageUrl,
            rssResponse.lastUpdated, episodes =
            rssItemsToEpisodes(items)
        )
    }

    fun save(podcast: Podcast) {
        GlobalScope.launch {
            val podcastId = podcastDao.insertPodcast(podcast)
            for (episod in podcast.episodes) {
                episod.podcastId = podcastId
                podcastDao.insertEpisod(episod)
            }
        }
        Log.i("98765","SAVE p")
    }

    fun getAll(): LiveData<List<Podcast>> {
        return podcastDao.loadPodcasts()
    }

    fun deletePodcast(podcast: Podcast) {
        GlobalScope.launch {
            podcastDao.deletePodcast(podcast)
        }

    }


    private fun getNewEpisodes(
        localPodcast: Podcast, callBack:
            (List<Episode>) -> Unit
    ) {
        rssFeedService.getFeed(localPodcast.feedUrl) { response ->
            if (response != null) {
                val remotePodcast =
                    rssResponseToPodcast(
                        localPodcast.feedUrl,
                        localPodcast.imageUrl, response
                    )
// 3
                remotePodcast?.let {
                    val localEpisodes =
                        podcastDao.loadEpisodes(localPodcast.id!!)
                    val newEpisodes = remotePodcast.episodes.filter { episode ->
                        localEpisodes.find {
                            episode.guid == it.guid
                        } == null

                    }

                    callBack(newEpisodes)
                }
            } else {
                callBack(listOf())
            }
        }
    }

    private fun saveNewEpisodes(
        podcastId: Long, episodes:
        List<Episode>
    ) {
        GlobalScope.launch {
            for (episode in episodes) {
                episode.podcastId = podcastId
                podcastDao.insertEpisod(episode)
            }
        }
    }


    fun updatePodcastEpisodes(
        callback: (List<PodcastUpdateInfo>) ->
        Unit
    ) {
// 1
        val updatedPodcasts: MutableList<PodcastUpdateInfo> =
            mutableListOf()
// 2
        val podcasts = podcastDao.loadPodcastsStatic()
// 3
        var processCount = podcasts.count()
// 4
        for (podcast in podcasts) {
// 5
// 6
            getNewEpisodes(podcast) { newEpisodes ->
                if (newEpisodes.count() > 0) {
                    saveNewEpisodes(podcast.id!!, newEpisodes)
                    updatedPodcasts.add(
                        PodcastUpdateInfo(
                            podcast.feedUrl,
                            podcast.feedTitle, newEpisodes.count()
                        )
                    )
                }
                processCount--
                if (processCount == 0) {
                    callback(updatedPodcasts)

                }

            }

        }
    }

    class PodcastUpdateInfo(
        val feedUrl: String, val name: String,
        val newCount: Int
    )

}
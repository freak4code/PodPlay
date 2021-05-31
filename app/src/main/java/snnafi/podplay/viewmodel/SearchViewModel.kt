package snnafi.podplay.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import snnafi.podplay.repository.ItunesRepo
import snnafi.podplay.service.PodcastResponse
import snnafi.podplay.util.DateUtils

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    var itunesRepo: ItunesRepo? = null

    data class PodcastSummaryViewData(
        var name: String? = "",
        var lastUpdated: String? = "",
        var imageUrl: String? = "",
        var feedUrl: String? = ""
    )

    private fun itunesPodcastToPodcastSummaryView(
        itunesPodcast: PodcastResponse.ItunesPodcast
    ):
            PodcastSummaryViewData {
        return PodcastSummaryViewData(
            itunesPodcast.collectionCensoredName,
            DateUtils.jsonDateToShortDate(itunesPodcast.releaseDate),
            itunesPodcast.artworkUrl100,
            itunesPodcast.feedUrl
        )
    }

    fun searchPodcasts(term: String, callBack: (List<PodcastSummaryViewData>) -> Unit) {
        itunesRepo?.searchByTerm(term) {
            if (it == null){
                callBack(emptyList())
            } else {
                val searchViews = it.map {
                    itunesPodcastToPodcastSummaryView(it)
                }
                callBack(searchViews)
            }
        }
    }

}
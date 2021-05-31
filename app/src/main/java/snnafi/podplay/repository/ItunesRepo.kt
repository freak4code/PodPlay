package snnafi.podplay.repository

import android.util.Log
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import snnafi.podplay.service.ItunesService
import snnafi.podplay.service.PodcastResponse


class ItunesRepo(private val itunesService: ItunesService) {
    private  val TAG = "ItunesRepo"

    fun searchByTerm(term: String, callBack: (List<PodcastResponse.ItunesPodcast>?) -> Unit) {

        val podcastCall = itunesService.searchPodcastByTerm(term)
        podcastCall.enqueue(object: Callback<PodcastResponse> {
            /**
             * Invoked for a received HTTP response.
             *
             *
             * Note: An HTTP response may still indicate an application-level failure such as a 404 or 500.
             * Call [Response.isSuccessful] to determine if the response indicates success.
             */
            override fun onResponse(call: Call<PodcastResponse>, response: Response<PodcastResponse>) {
                val body = response.body()
                callBack(body?.results)

            }

            /**
             * Invoked when a network exception occurred talking to the server or when an unexpected exception
             * occurred creating the request or processing the response.
             */
            override fun onFailure(call: Call<PodcastResponse>, t: Throwable) {
               callBack(null)

            }

        })
    }
}
package snnafi.podplay.ui

import android.content.Context
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.*
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import snnafi.podplay.R
import snnafi.podplay.adapter.EpisodeListAdapter
import snnafi.podplay.viewmodel.PodcastViewModel

class PodcastDetailsFragment : Fragment(), EpisodeListAdapter.EpisodeListAdapterListener {

    val podcastViewModel by activityViewModels<PodcastViewModel>()
    private lateinit var episodeListAdapter: EpisodeListAdapter
    private var listener: OnPodcastDetailsListener? = null
    private var menuItem: MenuItem? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnPodcastDetailsListener) {
            listener = context
        } else {
            throw RuntimeException("${context.toString()} must implement OnPodcastDetailsListener")
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_podcast_details, container, false)
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setupControls()
        updateControls()
    }

    override fun onStart() {
        super.onStart()

    }

    override fun onStop() {
        super.onStop()
        val fragmentActivity = activity as FragmentActivity


    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_details, menu)
        menuItem = menu.findItem(R.id.menu_feed_action)
        updateMenuItem()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_feed_action -> {
                Log.i("98765", "SAVE p")

                podcastViewModel.activePodcastViewData?.feedUrl?.let {
                    if (podcastViewModel.activePodcastViewData?.subscribed == true) {
                        listener?.onUnsubscribe()
                        Log.i("98765", "<<unSAVE p")
                    } else {
                        listener?.onSubscribe()
                        Log.i("98765", "<<SAVE p")
                    }

                }


                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }

    }

    private fun updateControls() {
        val viewData = podcastViewModel.activePodcastViewData ?: return

        view?.let {
            it.findViewById<TextView>(R.id.feedTitleTextView).text = viewData.feedTitle
            it.findViewById<TextView>(R.id.feedDescTextView).text = viewData.feedTitle
            activity?.let {
                Glide.with(it)
                    .load(viewData.imageUrl)
                    .into(it.findViewById(R.id.feedImageView))
            }
        }
    }

    private fun setupControls() {
        // 1

        view?.let {
            val episodeRecyclerView = it.findViewById<RecyclerView>(R.id.episodeRecyclerView)
            it.findViewById<TextView>(R.id.feedDescTextView).movementMethod =
                ScrollingMovementMethod()
// 2
            episodeRecyclerView.setHasFixedSize(true)
            val layoutManager = LinearLayoutManager(activity)
            episodeRecyclerView.layoutManager = layoutManager
            val dividerItemDecoration = DividerItemDecoration(
                episodeRecyclerView.context, layoutManager.orientation
            )
            episodeRecyclerView.addItemDecoration(dividerItemDecoration)
// 3
            episodeListAdapter = EpisodeListAdapter(
                podcastViewModel.activePodcastViewData?.episodes, this
            )
            episodeRecyclerView.adapter = episodeListAdapter
        }

    }

    companion object {
        fun newInstance() = PodcastDetailsFragment()
    }

    interface OnPodcastDetailsListener {
        fun onSubscribe()
        fun onUnsubscribe()
        fun onShowEpisodePlayer(episodeViewData: PodcastViewModel.EpisodeViewData)
    }

    fun updateMenuItem() {

        val viewData = podcastViewModel.activePodcastViewData ?: return
        menuItem?.title =
            if (viewData.subscribed) getString(R.string.unsubscribe) else getString(R.string.subscribe)

    }


    override fun onSelectedEpisode(episodeViewData: PodcastViewModel.EpisodeViewData) {

        listener?.onShowEpisodePlayer(episodeViewData)
//        // 1
//        val fragmentActivity = activity as FragmentActivity
//        // 2
//        val controller =
//            MediaControllerCompat.getMediaController(fragmentActivity)
//// 3
//        if (controller.playbackState != null) {
//            if (controller.playbackState.state ==
//                PlaybackStateCompat.STATE_PLAYING
//            ) {
//// 4
//                controller.transportControls.pause()
//            } else {
//// 5
//                startPlaying(episodeViewData)
//            }
//        } else { // 6
//            startPlaying(episodeViewData)
//        }
    }

}
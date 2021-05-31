package snnafi.podplay.ui

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.*
import snnafi.podplay.R
import snnafi.podplay.adapter.PodcastListAdapter
import snnafi.podplay.databinding.ActivityPodcastBinding
import snnafi.podplay.db.PodPlayDatabase
import snnafi.podplay.repository.ItunesRepo
import snnafi.podplay.repository.PodcastRepo
import snnafi.podplay.service.ItunesService
import snnafi.podplay.service.RssFeedService
import snnafi.podplay.viewmodel.PodcastViewModel
import snnafi.podplay.viewmodel.SearchViewModel
import snnafi.podplay.worker.EpisodeUpdateWorker
import java.util.concurrent.TimeUnit

class PodcastActivity : AppCompatActivity(), PodcastListAdapter.PodcastListAdapterListener,
    PodcastDetailsFragment.OnPodcastDetailsListener {

    private val TAG = "PodcastActivity"
    private lateinit var binding: ActivityPodcastBinding
    private val searchViewModel by viewModels<SearchViewModel>()
    private lateinit var podcastListAdapter: PodcastListAdapter
    private lateinit var searchMenuItem: MenuItem
    private val podcastViewModel by viewModels<PodcastViewModel>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPodcastBinding.inflate(layoutInflater)
        setContentView(binding.root)
        scheduleJobs()
        setUpToolbar()
        setupViewModels()
        updateControls()
        setupPodcastListView()

        handleIntent(intent)
        addBackStackListener()
    }

    private fun setUpToolbar() {
        setSupportActionBar(binding.toolbar)
    }

    private fun setupViewModels() {
        val itunesService = ItunesService.instance
        searchViewModel.itunesRepo = ItunesRepo(itunesService)
        val db = PodPlayDatabase.getInstance(applicationContext)
        val rssFeedService = RssFeedService.instance
        podcastViewModel.podcastRepo =
            PodcastRepo(rssFeedService as RssFeedService, db.podcastDao())


    }

    private fun updateControls() {
        binding.podcastRecyclerView.setHasFixedSize(true)
        val layoutManager = LinearLayoutManager(this)
        binding.podcastRecyclerView.layoutManager = layoutManager
        val dividerItemDecoration = DividerItemDecoration(
            binding.podcastRecyclerView.context, layoutManager.orientation
        )
        binding.podcastRecyclerView.addItemDecoration(dividerItemDecoration)
        podcastListAdapter = PodcastListAdapter(null, this, this)
        binding.podcastRecyclerView.adapter = podcastListAdapter
    }

    private fun performSearch(term: String) {
        showProgressBar()
        searchViewModel.searchPodcasts(term) {
            hideProgressBar()
            binding.toolbar.title = term
            podcastListAdapter.setSearchData(it)
        }
    }

    private fun handleIntent(intent: Intent) {
        if (Intent.ACTION_SEARCH == intent.action) {
            val query = intent.getStringExtra(SearchManager.QUERY) ?: return
            Log.i(TAG, query)
            performSearch(query)
        }
        val podcastFeedUrl =
            intent.getStringExtra(EpisodeUpdateWorker.EXTRA_FEED_URL)
        if (podcastFeedUrl != null) {
            podcastViewModel.setActivePodcast(podcastFeedUrl) {
                it?.let { podcastSummaryView ->
                    onShowDetails(podcastSummaryView)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater.inflate(R.menu.main_menu, menu)

        searchMenuItem = menu.findItem(R.id.search_item)
        searchMenuItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(p0: MenuItem?): Boolean {
                return true
            }

            override fun onMenuItemActionCollapse(p0: MenuItem?): Boolean {
                showSubscribedPodcasts()
                return true
            }

        })

        val searchView = searchMenuItem.actionView as SearchView

        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        // 4
        searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))

        if (binding.podcastRecyclerView.visibility == View.INVISIBLE) {
            searchMenuItem.isVisible = false
        }
        if (supportFragmentManager.backStackEntryCount > 0) {
            binding.podcastRecyclerView.visibility = View.INVISIBLE
        }

        return true
    }

    override fun onShowDetails(podcastSummaryViewData: SearchViewModel.PodcastSummaryViewData) {
        val feedUrl = podcastSummaryViewData.feedUrl ?: return
        showProgressBar()
        podcastViewModel.getPodcast(podcastSummaryViewData) {
            hideProgressBar()
            if (it != null) {
                showDetailsFragment()
            } else {
                showError("Error loading feed $feedUrl")
            }
        }
    }

    private fun showProgressBar() {
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        binding.progressBar.visibility = View.INVISIBLE
    }

    companion object {
        private const val TAG_DETAILS_FRAGMENT = "DetailsFragment"
        private const val TAG_EPISODE_UPDATE_JOB = "snnafi.podplay.episodes"
        private const val TAG_PLAYER_FRAGMENT = "PlayerFragment"
    }

    private fun createPodcastDetailsFragment(): PodcastDetailsFragment {

        var podcastDetailsFragment =
            supportFragmentManager.findFragmentByTag(TAG_DETAILS_FRAGMENT) as PodcastDetailsFragment?
        if (podcastDetailsFragment == null) {
            podcastDetailsFragment = PodcastDetailsFragment.newInstance()
        }
        return podcastDetailsFragment
    }

    fun showDetailsFragment() {
        val podcastDetailsFragment = createPodcastDetailsFragment()
        supportFragmentManager.beginTransaction()
            .add(R.id.podcastDetailsContainer, podcastDetailsFragment, TAG_DETAILS_FRAGMENT)
            .addToBackStack("DetailsFragment")
            .commit()
        binding.podcastRecyclerView.visibility = View.INVISIBLE
        searchMenuItem.isVisible = false

    }

    fun addBackStackListener() {
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                binding.podcastRecyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(getString(R.string.ok_button), null)
            .create()
            .show()
    }

    override fun onSubscribe() {
        podcastViewModel.saveActivePodcast()
        supportFragmentManager.popBackStack()
    }

    override fun onUnsubscribe() {
        podcastViewModel.deleteActivePodcast()
        supportFragmentManager.popBackStack()
    }

    override fun onShowEpisodePlayer(episodeViewData: PodcastViewModel.EpisodeViewData) {
        podcastViewModel.activeEpisodeViewData = episodeViewData
        showPlayerFragment()
    }

    fun showSubscribedPodcasts() {

        val podcasts = podcastViewModel.getPodcast()?.value
        if (podcasts != null) {
            binding.toolbar.title = getString(R.string.subscribed_podcasts)
            podcastListAdapter.setSearchData(podcasts)
        }
    }

    fun setupPodcastListView() {
        podcastViewModel.getPodcast()?.observe(this) {
            showSubscribedPodcasts()
        }
    }

    private fun scheduleJobs() {
        // 1
        val constraints: Constraints = Constraints.Builder().apply {
            setRequiredNetworkType(NetworkType.CONNECTED)
            setRequiresCharging(true)
        }.build()
// 2
        val request = PeriodicWorkRequestBuilder<EpisodeUpdateWorker>(
            1, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TAG_EPISODE_UPDATE_JOB,
            ExistingPeriodicWorkPolicy.REPLACE, request
        )

    }

    private fun createEpisodePlayerFragment(): EpisodePlayerFragment {
        var episodePlayerFragment =
            supportFragmentManager.findFragmentByTag(TAG_PLAYER_FRAGMENT) as
                    EpisodePlayerFragment?
        if (episodePlayerFragment == null) {
            episodePlayerFragment = EpisodePlayerFragment.newInstance()
        }
        return episodePlayerFragment
    }

    private fun showPlayerFragment() {
        val episodePlayerFragment = createEpisodePlayerFragment()
        supportFragmentManager.beginTransaction().replace(
            R.id.podcastDetailsContainer,
            episodePlayerFragment,
            TAG_PLAYER_FRAGMENT
        ).addToBackStack("PlayerFragment").commit()
        binding.podcastRecyclerView.visibility = View.INVISIBLE
        searchMenuItem.isVisible = false
    }
}
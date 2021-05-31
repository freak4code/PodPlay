package snnafi.podplay.ui

import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.format.DateUtils
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import snnafi.podplay.R
import snnafi.podplay.databinding.FragmentEpisodePlayerBinding
import snnafi.podplay.service.PodplayMediaCallback
import snnafi.podplay.service.PodplayMediaCallback.Companion.CMD_CHANGESPEED
import snnafi.podplay.service.PodplayMediaCallback.Companion.CMD_EXTRA_SPEED
import snnafi.podplay.service.PodplayMediaService
import snnafi.podplay.util.HtmlUtils
import snnafi.podplay.viewmodel.PodcastViewModel

class EpisodePlayerFragment : Fragment() {

    private lateinit var binding: FragmentEpisodePlayerBinding
    private val podcastViewModel by activityViewModels<PodcastViewModel>()
    private lateinit var mediaBrowser: MediaBrowserCompat
    private var mediaControllerCallback: MediaControllerCallback? = null
    private var playerSpeed: Float = 1.0f
    private var episodeDuration: Long = 0
    private var draggingScrubber: Boolean = false
    private var progressAnimator: ValueAnimator? = null

    // VIDEO

    private var mediaSession: MediaSessionCompat? = null
    private var mediaPlayer: MediaPlayer? = null
    private var playOnPrepare: Boolean = false
    private var isVideo: Boolean = false


    companion object {
        fun newInstance() = EpisodePlayerFragment()

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            isVideo = podcastViewModel.activeEpisodeViewData?.isVideo ?: false
        } else {
            isVideo = false
        }

        if (!isVideo) {
            initMediaBrowser()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(
            R.layout.fragment_episode_player,
            container, false
        )
        binding = FragmentEpisodePlayerBinding.bind(view)
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setupControls()

        if (isVideo) {
            initMediaSession()
            initVideoPlayer()
        }

        updateControls()
    }

    override fun onStart() {
        super.onStart()
        if (!isVideo) {
            if (mediaBrowser.isConnected) {
                val fragmentActivity = activity as FragmentActivity
                if (MediaControllerCompat.getMediaController(fragmentActivity) == null) {

                    registerMediaController(mediaBrowser.sessionToken)
                    updateControlsFromController()
                }
            } else {
                mediaBrowser.connect()
            }

        }
    }

    override fun onStop() {
        super.onStop()
        progressAnimator?.cancel()
        val fragmentActivity = activity as FragmentActivity
        if (MediaControllerCompat.getMediaController(fragmentActivity)
            != null
        ) {
            mediaControllerCallback?.let {
                MediaControllerCompat.getMediaController(fragmentActivity)
                    .unregisterCallback(it)
            }
        }
        if (!isVideo) {
            mediaPlayer?.setDisplay(null)
        }

        if (!fragmentActivity.isChangingConfigurations) {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    private fun updateControls() {

        view?.let {

            it.findViewById<TextView>(R.id.episodeTitleTextView).text =
                podcastViewModel.activeEpisodeViewData?.title
// 2
            val htmlDesc =
                podcastViewModel.activeEpisodeViewData?.description ?: ""
            val descSpan = HtmlUtils.htmlToSpannable(htmlDesc)
            it.findViewById<TextView>(R.id.episodeDescTextView).text = descSpan
            it.findViewById<TextView>(R.id.episodeDescTextView).movementMethod =
                ScrollingMovementMethod()
// 3
            val fragmentActivity = activity as FragmentActivity
            Glide.with(fragmentActivity)
                .load(podcastViewModel.activePodcastViewData?.imageUrl)
                .into(it.findViewById<ImageView>(R.id.episodeImageView))
            binding.speedButton.text = "${playerSpeed}x"

            mediaPlayer?.let {
                updateControlsFromController()
            }
        }


    }

    private fun startPlaying(
        episodeViewData: PodcastViewModel.EpisodeViewData
    ) {
        val fragmentActivity = activity as FragmentActivity
        val controller =
            MediaControllerCompat.getMediaController(fragmentActivity)

        val viewData = podcastViewModel.activePodcastViewData ?: return
        val bundle = Bundle()
        bundle.putString(
            MediaMetadataCompat.METADATA_KEY_TITLE,
            episodeViewData.title
        )
        bundle.putString(
            MediaMetadataCompat.METADATA_KEY_ARTIST,
            viewData.feedTitle
        )
        bundle.putString(
            MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
            viewData.imageUrl
        )

        controller.transportControls.playFromUri(
            Uri.parse(episodeViewData.mediaUrl), bundle
        )

    }

    inner class MediaControllerCallback :
        MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            super.onMetadataChanged(metadata)
            println(
                "metadata changed to ${
                    metadata?.getString(
                        MediaMetadataCompat.METADATA_KEY_MEDIA_URI
                    )
                }"
            )
            metadata?.let { updateControlsFromMetadata(it) }
        }

        override fun onPlaybackStateChanged(
            state:
            PlaybackStateCompat?
        ) {
            super.onPlaybackStateChanged(state)
            println("state changed to $state")
            val state = state ?: return
            handleStateChange(state.state, state.position, state.playbackSpeed)

        }
    }

    private fun registerMediaController(
        token:
        MediaSessionCompat.Token
    ) {
// 1
        val fragmentActivity = activity as FragmentActivity
        // 2
        val mediaController = MediaControllerCompat(
            fragmentActivity,
            token
        )
        MediaControllerCompat.setMediaController(
            fragmentActivity,
            mediaController
        )
// 4
        mediaControllerCallback = MediaControllerCallback()
        mediaController.registerCallback(mediaControllerCallback!!)
    }

    inner class MediaBrowserCallBacks :
        MediaBrowserCompat.ConnectionCallback() {
        // 1
        override fun onConnected() {
            super.onConnected()
            // 2
            registerMediaController(mediaBrowser.sessionToken)
            println("onConnected")
            updateControlsFromController()
        }

        override fun onConnectionSuspended() {
            super.onConnectionSuspended()
            println("onConnectionSuspended")
            // Disable transport controls
        }

        override fun onConnectionFailed() {
            super.onConnectionFailed()
            println("onConnectionFailed")
            // Fatal error handling
        }
    }

    private fun initMediaBrowser() {
        val fragmentActivity = activity as FragmentActivity
        mediaBrowser = MediaBrowserCompat(
            fragmentActivity,
            ComponentName(
                fragmentActivity,
                PodplayMediaService::class.java
            ),
            MediaBrowserCallBacks(),
            null
        )
    }

    private fun togglePlayPause() {
        playOnPrepare = true
        val fragmentActivity = activity as FragmentActivity
        val controller =
            MediaControllerCompat.getMediaController(fragmentActivity)
        if (controller.playbackState != null) {
            if (controller.playbackState.state ==
                PlaybackStateCompat.STATE_PLAYING
            ) {
                controller.transportControls.pause()
            } else {
                podcastViewModel.activeEpisodeViewData?.let { startPlaying(it) }
            }
        } else {
            podcastViewModel.activeEpisodeViewData?.let { startPlaying(it) }
        }
    }

    private fun setupControls() {
        view?.let {
            it.findViewById<MaterialButton>(R.id.playToggleButton).setOnClickListener {
                togglePlayPause()
            }


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                binding.speedButton.setOnClickListener {
                    changeSpeed()
                }
            } else {
                binding.speedButton.visibility = View.INVISIBLE
            }

            binding.forwardButton.setOnClickListener {
                seekBy(30)
            }
            binding.replayButton.setOnClickListener {
                seekBy(-10)
            }
        }

        // 1
        binding.seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar, progress:
                    Int, fromUser: Boolean
                ) {
                    // 2
                    binding.currentTimeTextView.text = DateUtils.formatElapsedTime(
                        (progress / 1000).toLong()
                    )
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {
// 3
                    draggingScrubber = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    // 4
                    draggingScrubber = false
                    // 5
                    val fragmentActivity = activity as FragmentActivity
                    val controller =
                        MediaControllerCompat.getMediaController(fragmentActivity)
                    if (controller.playbackState != null) {
// 6
                        controller.transportControls.seekTo(seekBar.progress.toLong())
                    } else {
// 7
                        seekBar.progress = 0
                    }
                }
            })

    }

    private fun handleStateChange(
        state: Int, position: Long, speed:
        Float
    ) {
        progressAnimator?.let {
            it.cancel()
            progressAnimator = null
        }
        val isPlaying = state == PlaybackStateCompat.STATE_PLAYING
        binding.playToggleButton.isActivated = isPlaying

        val progress = position.toInt()
        binding.seekBar.progress = progress
        binding.speedButton.text = "${playerSpeed}x"
        if (isPlaying) {
            animateScrubber(progress, speed)
            if (isVideo) {
                setupVideoUI()
            }
        }
    }

    private fun changeSpeed() {
        // 1
        playerSpeed += 0.25f
        if (playerSpeed > 2.0f) {
            playerSpeed = 0.75f
        }
// 2
        val bundle = Bundle()
        bundle.putFloat(CMD_EXTRA_SPEED, playerSpeed)
        // 3
        val fragmentActivity = activity as FragmentActivity
        val controller =
            MediaControllerCompat.getMediaController(fragmentActivity)
        controller.sendCommand(CMD_CHANGESPEED, bundle, null)
        // 4
        binding.speedButton.text = "${playerSpeed}x"
    }

    private fun seekBy(seconds: Int) {
        val fragmentActivity = activity as FragmentActivity
        val controller =
            MediaControllerCompat.getMediaController(fragmentActivity)
        val newPosition = controller.playbackState.position +
                seconds * 1000
        controller.transportControls.seekTo(newPosition)
    }

    private fun updateControlsFromMetadata(
        metadata:
        MediaMetadataCompat
    ) {
        episodeDuration =
            metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
        binding.endTimeTextView.text = DateUtils.formatElapsedTime(
            episodeDuration / 1000
        )
        binding.seekBar.max = episodeDuration.toInt()
    }

    // 1
    private fun animateScrubber(progress: Int, speed: Float) {
        // 2
        val timeRemaining = ((episodeDuration - progress) /
                speed).toInt()
// 3
        if (timeRemaining < 0) {
            return; }
// 4
        progressAnimator = ValueAnimator.ofInt(
            progress, episodeDuration.toInt()
        )
        progressAnimator?.let { animator ->
// 5
            animator.duration = timeRemaining.toLong()
// 6
            animator.interpolator = LinearInterpolator()
// 7
            animator.addUpdateListener {
                if (draggingScrubber) {
// 8
                    animator.cancel()
                } else {
// 9
                    binding.seekBar.progress = animator.animatedValue as Int
                }
            }
// 10
            animator.start()
        }
    }

    private fun updateControlsFromController() {
        val fragmentActivity = activity as FragmentActivity
        val controller =
            MediaControllerCompat.getMediaController(fragmentActivity)
        if (controller != null) {
            val metadata = controller.metadata
            if (metadata != null) {
                handleStateChange(
                    controller.playbackState.state,
                    controller.playbackState.position, playerSpeed
                )
                updateControlsFromMetadata(controller.metadata)
            }
        }
    }

    //VIdeo

    private fun initMediaSession() {
        if (mediaSession == null) {
// 1
            mediaSession = MediaSessionCompat(
                activity as Context,
                "EpisodePlayerFragment"
            )
// 2
            mediaSession?.setMediaButtonReceiver(null)
        }
        registerMediaController(mediaSession!!.sessionToken)
    }

    private fun setSurfaceSize() {
        // 1
        val mediaPlayer = mediaPlayer ?: return
        // 2
        val videoWidth = mediaPlayer.videoWidth
        val videoHeight = mediaPlayer.videoHeight
        // 3
        val parent = binding.videoSurfaceView.parent as View
        val containerWidth = parent.width
        val containerHeight = parent.height
        // 4
        val layoutAspectRatio = containerWidth.toFloat() /
                containerHeight
        val videoAspectRatio = videoWidth.toFloat() / videoHeight
        // 5
        val layoutParams = binding.videoSurfaceView.layoutParams
        // 6
        if (videoAspectRatio > layoutAspectRatio) {
            layoutParams.height =
                (containerWidth / videoAspectRatio).toInt()
        } else {
            layoutParams.width =
                (containerHeight * videoAspectRatio).toInt()
        }
// 7
        binding.videoSurfaceView.layoutParams = layoutParams
    }

    private fun initMediaPlayer() {
        if (mediaPlayer == null) {
// 1
            mediaPlayer = MediaPlayer()
            mediaPlayer?.let {
// 2
                it.setAudioStreamType(AudioManager.STREAM_MUSIC)
// 3
                it.setDataSource(
                    podcastViewModel.activeEpisodeViewData?.mediaUrl
                )
// 4
                it.setOnPreparedListener {
// 5
                    val fragmentActivity = activity as FragmentActivity
                    val episodeMediaCallback = PodplayMediaCallback(
                        fragmentActivity, mediaSession!!, it
                    )
                    mediaSession!!.setCallback(episodeMediaCallback)
                    // 6
                    setSurfaceSize()
                    // 7
                    if (playOnPrepare) {
                        togglePlayPause()
                    }
                }
// 8
                it.prepareAsync()
            }
        } else { // 9
            setSurfaceSize()
        }
    }

    private fun initVideoPlayer() {
        // 1
        binding.videoSurfaceView.visibility = View.VISIBLE
        // 2
        val surfaceHolder = binding.videoSurfaceView.holder
        // 3
        surfaceHolder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                // 4
                initMediaPlayer()
                mediaPlayer?.setDisplay(holder)
            }

            override fun surfaceChanged(
                var1: SurfaceHolder, var2: Int,
                var3: Int, var4: Int
            ) {
            }

            override fun surfaceDestroyed(var1: SurfaceHolder) {
            }
        })
    }

    private fun setupVideoUI() {
        binding.episodeDescTextView.visibility = View.INVISIBLE
        binding.headerView.visibility = View.INVISIBLE
        val activity = activity as AppCompatActivity
        activity.supportActionBar?.hide()
        binding.playerControls.setBackgroundColor(Color.argb(255 / 2, 0, 0, 0))
    }
}
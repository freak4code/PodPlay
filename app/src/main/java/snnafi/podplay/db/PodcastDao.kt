package snnafi.podplay.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy.REPLACE
import androidx.room.Query
import snnafi.podplay.model.Episode
import snnafi.podplay.model.Podcast

@Dao
interface PodcastDao {

    @Query("SELECT * FROM podcast ORDER BY feed_title")
    fun loadPodcasts(): LiveData<List<Podcast>>

    @Query("SELECT * FROM Podcast ORDER BY feed_title")
    fun loadPodcastsStatic(): List<Podcast>

    @Query("SELECT * FROM episode WHERE podcastId = :podcastId ORDER BY releaseDate DESC")
    fun loadEpisodes(podcastId: Long): List<Episode>

    @Insert(onConflict = REPLACE)
    fun insertPodcast(podcast: Podcast): Long

    @Insert(onConflict = REPLACE)
    fun insertEpisod(episode: Episode): Long

    @Query("SELECT * FROM podcast WHERE feed_url = :url")
    fun loadPodcast(url: String): Podcast?

    @Delete
    fun deletePodcast(podcast: Podcast)
}
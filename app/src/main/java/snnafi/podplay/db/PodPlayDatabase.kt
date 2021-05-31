package snnafi.podplay.db

import android.content.Context
import androidx.room.*
import snnafi.podplay.model.Episode
import snnafi.podplay.model.Podcast
import java.util.*

class Converters {

    @TypeConverter
    fun timeStampToLong(value: Long?): Date? {
        return if (value == null) null else Date(value)
    }

    @TypeConverter
    fun toTimeStamp(date: Date?): Long? {
        return date?.time
    }
}

@Database(entities = [Podcast::class, Episode::class], version = 1)
@TypeConverters(Converters::class)
abstract class PodPlayDatabase : RoomDatabase() {

    abstract fun podcastDao(): PodcastDao

    companion object {
        private var instance: PodPlayDatabase? = null

        fun getInstance(context: Context): PodPlayDatabase {
            if (instance == null) {
                instance =
                    Room.databaseBuilder(context, PodPlayDatabase::class.java, "PodPlayDatabase")
                        .build()
            }
            return instance as PodPlayDatabase
        }
    }
}
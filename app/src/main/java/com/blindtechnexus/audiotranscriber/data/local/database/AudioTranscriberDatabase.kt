package com.blindtechnexus.audiotranscriber.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.blindtechnexus.audiotranscriber.data.local.dao.TranscriptDao
import com.blindtechnexus.audiotranscriber.data.local.entity.TranscriptEntity

@Database(
    entities = [TranscriptEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AudioTranscriberDatabase : RoomDatabase() {

    abstract fun transcriptDao(): TranscriptDao

    companion object {
        @Volatile
        private var INSTANCE: AudioTranscriberDatabase? = null

        fun getDatabase(context: Context): AudioTranscriberDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AudioTranscriberDatabase::class.java,
                    "audio_transcriber_transcripts_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

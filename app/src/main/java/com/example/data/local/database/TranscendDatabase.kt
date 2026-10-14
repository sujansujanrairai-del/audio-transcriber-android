package com.example.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.TranscriptDao
import com.example.data.local.entity.TranscriptEntity

@Database(
    entities = [TranscriptEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TranscendDatabase : RoomDatabase() {

    abstract fun transcriptDao(): TranscriptDao

    companion object {
        @Volatile
        private var INSTANCE: TranscendDatabase? = null

        fun getDatabase(context: Context): TranscendDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TranscendDatabase::class.java,
                    "transcend_transcripts_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.data.local.database.TranscendDatabase
import com.example.data.local.preferences.UserPreferencesManager
import com.example.data.local.security.SecureKeyStorage
import com.example.data.repository.TranscriptRepository

class TranscendApplication : Application() {

    lateinit var database: TranscendDatabase
        private set

    lateinit var secureKeyStorage: SecureKeyStorage
        private set

    lateinit var userPreferencesManager: UserPreferencesManager
        private set

    lateinit var transcriptRepository: TranscriptRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize core dependencies
        database = TranscendDatabase.getDatabase(this)
        secureKeyStorage = SecureKeyStorage(this)
        userPreferencesManager = UserPreferencesManager(this)
        transcriptRepository = TranscriptRepository(database.transcriptDao())

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Transcend Live Transcription",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live audio recording and transcription status"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "transcend_live_service_channel"
        lateinit var instance: TranscendApplication
            private set
    }
}

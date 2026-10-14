package com.blindtechnexus.audiotranscriber

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.blindtechnexus.audiotranscriber.data.local.database.AudioTranscriberDatabase
import com.blindtechnexus.audiotranscriber.data.local.preferences.UserPreferencesManager
import com.blindtechnexus.audiotranscriber.data.local.security.SecureKeyStorage
import com.blindtechnexus.audiotranscriber.data.repository.TranscriptRepository

class AudioTranscriberApplication : Application() {

    lateinit var database: AudioTranscriberDatabase
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
        database = AudioTranscriberDatabase.getDatabase(this)
        secureKeyStorage = SecureKeyStorage(this)
        userPreferencesManager = UserPreferencesManager(this)
        transcriptRepository = TranscriptRepository(database.transcriptDao())

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Transcriber Live Transcription",
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
        const val CHANNEL_ID = "audio_transcriber_live_service_channel"
        lateinit var instance: AudioTranscriberApplication
            private set
    }
}

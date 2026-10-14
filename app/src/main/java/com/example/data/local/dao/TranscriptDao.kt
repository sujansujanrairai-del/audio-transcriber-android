package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.TranscriptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptDao {

    @Query("SELECT * FROM transcripts ORDER BY createdAt DESC")
    fun getAllTranscripts(): Flow<List<TranscriptEntity>>

    @Query("SELECT * FROM transcripts WHERE id = :id LIMIT 1")
    fun getTranscriptById(id: String): Flow<TranscriptEntity?>

    @Query("SELECT * FROM transcripts WHERE fullText LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchTranscripts(query: String): Flow<List<TranscriptEntity>>

    @Query("SELECT * FROM transcripts WHERE isLive = :isLive ORDER BY createdAt DESC")
    fun getTranscriptsByType(isLive: Boolean): Flow<List<TranscriptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscript(transcript: TranscriptEntity)

    @Update
    suspend fun updateTranscript(transcript: TranscriptEntity)

    @Query("DELETE FROM transcripts WHERE id = :id")
    suspend fun deleteTranscriptById(id: String)

    @Query("DELETE FROM transcripts")
    suspend fun deleteAllTranscripts()
}

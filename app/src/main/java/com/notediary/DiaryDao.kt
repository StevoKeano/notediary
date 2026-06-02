package com.notediary

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DiaryEntry): Long

    @Update
    suspend fun update(entry: DiaryEntry)

    @Delete
    suspend fun delete(entry: DiaryEntry)

    @Query("SELECT * FROM diary_entries ORDER BY date DESC, createdAt DESC")
    fun getAllEntries(): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE id = :id")
    suspend fun getEntryById(id: Long): DiaryEntry?

    @Query("""
        SELECT * FROM diary_entries 
        WHERE id IN (SELECT docid FROM diary_entries_fts WHERE diary_entries_fts MATCH :query) 
        ORDER BY date DESC
    """)
    fun search(query: String): Flow<List<DiaryEntry>>

    @Query("SELECT DISTINCT entryId FROM diary_images")
    fun getEntryIdsWithAttachments(): Flow<List<Long>>

    @Insert
    suspend fun insertImage(image: DiaryImage): Long

    @Delete
    suspend fun deleteImage(image: DiaryImage)

    @Query("SELECT * FROM diary_images WHERE entryId = :entryId ORDER BY sortOrder")
    suspend fun getImagesForEntry(entryId: Long): List<DiaryImage>

    @Query("SELECT * FROM diary_images WHERE entryId = :entryId ORDER BY sortOrder")
    fun getImagesForEntryFlow(entryId: Long): Flow<List<DiaryImage>>
}

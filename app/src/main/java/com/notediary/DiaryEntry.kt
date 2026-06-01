package com.notediary

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Entity(tableName = "diary_entries")
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Fts4(contentEntity = DiaryEntry::class)
@Entity(tableName = "diary_entries_fts")
data class DiaryEntryFts(
    val content: String
)

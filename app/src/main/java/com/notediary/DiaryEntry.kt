package com.notediary

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
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

@Entity(
    tableName = "diary_images",
    foreignKeys = [ForeignKey(
        entity = DiaryEntry::class,
        parentColumns = ["id"],
        childColumns = ["entryId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("entryId")]
)
data class DiaryImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val imagePath: String,
    val mimeType: String = "application/octet-stream",
    val sortOrder: Int = 0
)

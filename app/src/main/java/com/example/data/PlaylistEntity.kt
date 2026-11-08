package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val tagNumber: Int, // 1, 2, 3... for TikTok vertical indicator
    val colorHex: String = "#58A6FF",
    val dateCreated: Long = System.currentTimeMillis()
)

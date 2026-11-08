package com.example.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "image_playlist_cross_ref",
    primaryKeys = ["imageId", "playlistId"],
    indices = [Index(value = ["playlistId"]), Index(value = ["imageId"])]
)
data class ImagePlaylistCrossRef(
    val imageId: String,
    val playlistId: Long
)

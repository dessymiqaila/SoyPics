package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "images")
data class ImageEntity(
    @PrimaryKey
    val id: String, // UUID
    val entryName: String, // e.g. "img_7f9a2b.dat" inside gamelog.logz
    val originalFileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val dateAdded: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val deletedDate: Long? = null
)

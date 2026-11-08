package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SoyPicsDao {

    // --- Images ---
    @Query("SELECT * FROM images WHERE isDeleted = 0 ORDER BY dateAdded DESC")
    fun getAllActiveImages(): Flow<List<ImageEntity>>

    @Query("SELECT * FROM images WHERE isDeleted = 1 ORDER BY deletedDate DESC")
    fun getDeletedImages(): Flow<List<ImageEntity>>

    @Query("SELECT COUNT(*) FROM images WHERE isDeleted = 0")
    fun getActiveImageCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM images WHERE isDeleted = 1")
    fun getDeletedImageCount(): Flow<Int>

    @Query("""
        SELECT i.* FROM images i
        INNER JOIN image_playlist_cross_ref ref ON i.id = ref.imageId
        WHERE ref.playlistId = :playlistId AND i.isDeleted = 0
        ORDER BY i.dateAdded DESC
    """)
    fun getImagesForPlaylist(playlistId: Long): Flow<List<ImageEntity>>

    @Query("SELECT * FROM images WHERE id = :id LIMIT 1")
    suspend fun getImageById(id: String): ImageEntity?

    @Query("SELECT * FROM images WHERE entryName = :entryName LIMIT 1")
    suspend fun getImageByEntryName(entryName: String): ImageEntity?

    @Query("SELECT * FROM images WHERE isDeleted = 0")
    suspend fun getActiveImagesList(): List<ImageEntity>

    @Query("SELECT * FROM images WHERE isDeleted = 1")
    suspend fun getDeletedImagesList(): List<ImageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: ImageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(images: List<ImageEntity>)

    @Query("UPDATE images SET isDeleted = 1, deletedDate = :timestamp WHERE id = :id")
    suspend fun softDeleteImage(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE images SET isDeleted = 0, deletedDate = NULL WHERE id = :id")
    suspend fun restoreImage(id: String)

    @Query("DELETE FROM images WHERE id = :id")
    suspend fun hardDeleteImage(id: String)

    @Query("DELETE FROM images WHERE isDeleted = 1")
    suspend fun hardDeleteAllTrash(): Int

    // --- Playlists ---
    @Query("""
        SELECT p.id, p.name, p.tagNumber, p.colorHex, p.dateCreated,
               COUNT(CASE WHEN i.isDeleted = 0 THEN ref.imageId ELSE NULL END) as imageCount
        FROM playlists p
        LEFT JOIN image_playlist_cross_ref ref ON p.id = ref.playlistId
        LEFT JOIN images i ON ref.imageId = i.id
        WHERE p.tagNumber BETWEEN 1 AND 6
        GROUP BY p.tagNumber
        ORDER BY p.tagNumber ASC
    """)
    fun getAllPlaylistsWithCount(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlists WHERE tagNumber BETWEEN 1 AND 6 GROUP BY tagNumber ORDER BY tagNumber ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY tagNumber ASC, id ASC")
    suspend fun getAllPlaylistsSync(): List<PlaylistEntity>

    @Query("UPDATE OR IGNORE image_playlist_cross_ref SET playlistId = :toPlaylistId WHERE playlistId = :fromPlaylistId")
    suspend fun migrateCrossRefs(fromPlaylistId: Long, toPlaylistId: Long)

    @Query("DELETE FROM image_playlist_cross_ref WHERE playlistId = :playlistId")
    suspend fun deleteCrossRefsForPlaylist(playlistId: Long)

    @Query("DELETE FROM playlists WHERE id NOT IN (SELECT MIN(id) FROM playlists WHERE tagNumber BETWEEN 1 AND 6 GROUP BY tagNumber)")
    suspend fun pruneDuplicatePlaylists()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT MAX(tagNumber) FROM playlists")
    suspend fun getMaxTagNumber(): Int?

    // --- CrossRef (Multi-Playlist Tagging) ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTag(crossRef: ImagePlaylistCrossRef)

    @Query("DELETE FROM image_playlist_cross_ref WHERE imageId = :imageId AND playlistId = :playlistId")
    suspend fun removeTag(imageId: String, playlistId: Long)

    @Query("SELECT playlistId FROM image_playlist_cross_ref WHERE imageId = :imageId")
    fun getTagIdsForImage(imageId: String): Flow<List<Long>>

    @Query("SELECT playlistId FROM image_playlist_cross_ref WHERE imageId = :imageId")
    suspend fun getTagIdsForImageSync(imageId: String): List<Long>

    @Query("DELETE FROM image_playlist_cross_ref WHERE imageId IN (SELECT id FROM images WHERE isDeleted = 1)")
    suspend fun cleanupTrashCrossRefs()
}

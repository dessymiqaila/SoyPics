package com.example.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.archive.ArchiveManager
import com.example.archive.ArchiveStats
import com.example.data.AppDatabase
import com.example.data.ImageEntity
import com.example.data.ImagePlaylistCrossRef
import com.example.data.PlaylistEntity
import com.example.data.PlaylistWithCount
import com.example.worker.ArchivePackWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class VaultRepository(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val dao = database.soyPicsDao()
    private val archiveManager = ArchiveManager(context)
    private val workManager = WorkManager.getInstance(context)

    // Flow streams for UI
    val activeImages: Flow<List<ImageEntity>> = dao.getAllActiveImages()
    val deletedImages: Flow<List<ImageEntity>> = dao.getDeletedImages()
    val activeImageCount: Flow<Int> = dao.getActiveImageCount()
    val deletedImageCount: Flow<Int> = dao.getDeletedImageCount()
    val playlistsWithCount: Flow<List<PlaylistWithCount>> = dao.getAllPlaylistsWithCount()
    val allPlaylists: Flow<List<PlaylistEntity>> = dao.getAllPlaylists()

    val preferences = archiveManager.preferences

    fun getArchiveStats(): ArchiveStats = archiveManager.getArchiveStats()

    fun getArchiveDirectoryPath(): String = preferences.getArchiveDisplayPath()

    fun relocateArchive(
        newDirPath: String? = null,
        newTreeUri: Uri? = null,
        displayPath: String = newDirPath ?: "Selected Folder"
    ): Boolean = archiveManager.relocateArchive(newDirPath, newTreeUri, displayPath)

    suspend fun clearAllAppData() = withContext(Dispatchers.IO) {
        archiveManager.clearArchive()
        dao.hardDeleteAllTrash()
        val active = dao.getActiveImagesList()
        active.forEach { dao.hardDeleteImage(it.id) }
        dao.cleanupTrashCrossRefs()
    }

    fun getVaultPassword(): String = preferences.getVaultPassword()

    fun setVaultPassword(password: String) = preferences.setVaultPassword(password)

    fun isFirstSetupCompleted(): Boolean = preferences.isFirstSetupCompleted()

    fun setFirstSetupCompleted(completed: Boolean) = preferences.setFirstSetupCompleted(completed)

    suspend fun ensureSixPlaylists() = withContext(Dispatchers.IO) {
        val existing = dao.getAllPlaylistsSync()
        // Deduplicate: if there are multiple playlists with the same tagNumber, migrate cross-refs to the first one
        val seenTags = mutableMapOf<Int, Long>()
        for (pl in existing) {
            if (pl.tagNumber in 1..6) {
                val primaryId = seenTags[pl.tagNumber]
                if (primaryId == null) {
                    seenTags[pl.tagNumber] = pl.id
                } else {
                    dao.migrateCrossRefs(fromPlaylistId = pl.id, toPlaylistId = primaryId)
                    dao.deletePlaylist(pl.id)
                }
            } else {
                dao.deletePlaylist(pl.id)
            }
        }
        dao.pruneDuplicatePlaylists()

        val defaultColors = listOf("#EC4899", "#3B82F6", "#10B981", "#8B5CF6", "#F97316", "#EAB308")
        for (num in 1..6) {
            if (!seenTags.containsKey(num)) {
                val color = defaultColors[(num - 1) % defaultColors.size]
                dao.insertPlaylist(PlaylistEntity(name = "$num", tagNumber = num, colorHex = color))
            }
        }
    }

    fun getImagesForPlaylist(playlistId: Long): Flow<List<ImageEntity>> =
        dao.getImagesForPlaylist(playlistId)

    fun getTagIdsForImage(imageId: String): Flow<List<Long>> =
        dao.getTagIdsForImage(imageId)

    suspend fun getTagIdsForImageSync(imageId: String): List<Long> =
        dao.getTagIdsForImageSync(imageId)

    suspend fun getImageById(id: String): ImageEntity? =
        dao.getImageById(id)

    suspend fun softDeleteImage(id: String) {
        dao.softDeleteImage(id)
    }

    suspend fun restoreImage(id: String) {
        dao.restoreImage(id)
    }

    suspend fun toggleTag(imageId: String, playlistId: Long): Boolean {
        val currentTags = dao.getTagIdsForImageSync(imageId)
        return if (currentTags.contains(playlistId)) {
            dao.removeTag(imageId, playlistId)
            false
        } else {
            dao.addTag(ImagePlaylistCrossRef(imageId, playlistId))
            true
        }
    }

    suspend fun createPlaylist(name: String, colorHex: String): Long {
        val maxTag = dao.getMaxTagNumber() ?: 0
        val newTagNumber = maxTag + 1
        return dao.insertPlaylist(
            PlaylistEntity(
                name = name,
                tagNumber = newTagNumber,
                colorHex = colorHex
            )
        )
    }

    suspend fun deletePlaylist(playlistId: Long) {
        dao.deletePlaylist(playlistId)
    }

    /**
     * Executes permanent cleanup (Empty Trash):
     * 1. Finds all non-deleted images in DB.
     * 2. Compacts gamelog.logz by keeping only active entry names.
     * 3. Drops deleted records and associated tags from Room Database.
     */
    suspend fun emptyTrash(password: String): Boolean = withContext(Dispatchers.IO) {
        val activeImages = dao.getActiveImagesList()
        val validEntryNames = activeImages.map { it.entryName }.toSet()

        // Compact physical archive
        val compacted = archiveManager.compactArchive(validEntryNames)
        if (compacted) {
            dao.cleanupTrashCrossRefs()
            dao.hardDeleteAllTrash()
            true
        } else {
            false
        }
    }

    suspend fun loadDecryptedBitmap(entryName: String, password: String): Bitmap? {
        return archiveManager.readDecryptedEntryToBitmap(entryName, password)
    }

    suspend fun hardDeleteImage(id: String) = withContext(Dispatchers.IO) {
        dao.hardDeleteImage(id)
    }

    /**
     * Reconciles Room Database against the physical gamelog.logz archive.
     * Deletes any phantom database records for images that do not exist inside the archive
     * (e.g. from an earlier interrupted batch import before commit completed).
     */
    suspend fun cleanOrphanedImages(): Int = withContext(Dispatchers.IO) {
        val archiveEntries = archiveManager.getAllArchiveEntryNames()
        if (archiveEntries.isEmpty()) {
            return@withContext 0
        }
        val allImages = dao.getAllImagesRawSync()
        var cleaned = 0
        for (img in allImages) {
            if (!archiveEntries.contains(img.entryName)) {
                dao.hardDeleteImage(img.id)
                cleaned++
            }
        }
        cleaned
    }

    suspend fun exportImageToDownloads(
        entryName: String,
        originalFileName: String,
        mimeType: String,
        password: String
    ): Uri? {
        return archiveManager.exportEntryToDownloads(entryName, originalFileName, mimeType, password)
    }

    /**
     * Enqueues batch background packing of files via WorkManager.
     * Writes URIs to a local cache file so batches of hundreds/thousands of images
     * never exceed WorkManager's 10KB Data limit, preventing crashes.
     */
    fun enqueuePackJob(uris: List<Uri>, password: String, playlistId: Long = -1L): java.util.UUID? {
        return try {
            val batchFile = java.io.File(context.cacheDir, "pack_batch_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}.txt")
            batchFile.bufferedWriter().use { writer ->
                for (uri in uris) {
                    writer.write(uri.toString())
                    writer.newLine()
                }
            }

            val request = OneTimeWorkRequestBuilder<ArchivePackWorker>()
                .setInputData(
                    workDataOf(
                        ArchivePackWorker.KEY_PASSWORD to password,
                        ArchivePackWorker.KEY_BATCH_FILE to batchFile.absolutePath,
                        ArchivePackWorker.KEY_PLAYLIST_ID to playlistId,
                        ArchivePackWorker.KEY_TOTAL to uris.size
                    )
                )
                .addTag(ArchivePackWorker.TAG)
                .build()

            workManager.enqueue(request)
            request.id
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getWorkInfoById(id: java.util.UUID): Flow<WorkInfo?> =
        workManager.getWorkInfoByIdFlow(id)

    /**
     * Seeds initial artistic sample files for instant live preview
     */
    suspend fun seedSampleVault(password: String) = withContext(Dispatchers.IO) {
        val seeded = archiveManager.seedSampleImages(password)
        val playlists = dao.getAllPlaylistsSync()

        for ((index, item) in seeded.withIndex()) {
            val entity = ImageEntity(
                id = item.id,
                entryName = item.entryName,
                originalFileName = item.originalFileName,
                mimeType = item.mimeType,
                fileSizeBytes = item.fileSizeBytes
            )
            dao.insertImage(entity)

            // Tag into default playlists
            if (playlists.isNotEmpty()) {
                val targetPlaylist = playlists[index % playlists.size]
                dao.addTag(ImagePlaylistCrossRef(item.id, targetPlaylist.id))
            }
        }
    }
}

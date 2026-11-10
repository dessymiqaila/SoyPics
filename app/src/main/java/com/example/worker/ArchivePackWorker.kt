package com.example.worker

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.archive.ArchiveManager
import com.example.data.AppDatabase
import com.example.data.ImageEntity
import com.example.data.ImagePlaylistCrossRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ArchivePackWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_PASSWORD = "key_password"
        const val KEY_URI_LIST = "key_uri_list"
        const val KEY_BATCH_FILE = "key_batch_file"
        const val KEY_PLAYLIST_ID = "key_playlist_id"
        const val KEY_PROGRESS = "key_progress"
        const val KEY_TOTAL = "key_total"
        const val KEY_CURRENT_NAME = "key_current_name"
        const val KEY_SUCCESS_COUNT = "key_success_count"
        const val TAG = "ArchivePackWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val password = inputData.getString(KEY_PASSWORD) ?: return@withContext Result.failure()
        val targetPlaylistId = inputData.getLong(KEY_PLAYLIST_ID, -1L)

        // Read URIs either from temporary batch file (bypasses WorkManager 10KB Data limit) or string array
        val batchFilePath = inputData.getString(KEY_BATCH_FILE)
        val uriStrings: List<String> = if (!batchFilePath.isNullOrBlank()) {
            val file = File(batchFilePath)
            if (file.exists()) {
                val lines = try {
                    file.readLines().filter { it.isNotBlank() }
                } catch (e: Exception) {
                    emptyList()
                }
                try { file.delete() } catch (_: Exception) {}
                lines
            } else {
                inputData.getStringArray(KEY_URI_LIST)?.toList() ?: emptyList()
            }
        } else {
            inputData.getStringArray(KEY_URI_LIST)?.toList() ?: emptyList()
        }

        if (uriStrings.isEmpty()) {
            return@withContext Result.success(workDataOf(KEY_SUCCESS_COUNT to 0))
        }

        val context = applicationContext
        val archiveManager = ArchiveManager(context)
        val database = AppDatabase.getDatabase(context)
        val dao = database.soyPicsDao()

        var successCount = 0
        val total = uriStrings.size

        // Start high-performance batch session (reads existing archive once)
        val session = archiveManager.startBatchSession()

        val pendingEntities = mutableListOf<ImageEntity>()
        val pendingTags = mutableListOf<ImagePlaylistCrossRef>()
        val packedUris = mutableListOf<Uri>()

        try {
            for ((index, uriString) in uriStrings.withIndex()) {
                val uri = Uri.parse(uriString)
                var fileName = "image_${System.currentTimeMillis()}_$index.jpg"
                var mimeType = "image/jpeg"

                try {
                    // Query metadata from content resolver
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                val queryName = cursor.getString(nameIndex)
                                if (!queryName.isNullOrBlank()) {
                                    fileName = queryName
                                }
                            }
                        }
                    }
                    context.contentResolver.getType(uri)?.let { mimeType = it }

                    // 1. Pack image into active batch session (fast streaming AES-256 GCM)
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val packed = inputStream.use { stream ->
                            archiveManager.addImageToBatch(
                                session = session,
                                input = stream,
                                originalFileName = fileName,
                                mimeType = mimeType,
                                password = password
                            )
                        }

                        // Collect for atomic commit
                        val entity = ImageEntity(
                            id = packed.id,
                            entryName = packed.entryName,
                            originalFileName = packed.originalFileName,
                            mimeType = packed.mimeType,
                            fileSizeBytes = packed.fileSizeBytes,
                            dateAdded = System.currentTimeMillis()
                        )
                        pendingEntities.add(entity)

                        if (targetPlaylistId > 0) {
                            pendingTags.add(ImagePlaylistCrossRef(packed.id, targetPlaylistId))
                        }

                        packedUris.add(uri)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Update progress notification
                try {
                    setProgress(
                        workDataOf(
                            KEY_PROGRESS to (index + 1),
                            KEY_TOTAL to total,
                            KEY_CURRENT_NAME to fileName
                        )
                    )
                } catch (_: Exception) {}
            }

            // Commit final archive to destination ONCE
            val committed = archiveManager.finishBatchSession(session)
            if (committed) {
                // Insert all records into Room Database only AFTER archive is committed to disk!
                dao.insertImages(pendingEntities)
                for (tag in pendingTags) {
                    try { dao.addTag(tag) } catch (_: Exception) {}
                }

                // Delete source files now that archive is guaranteed written
                for (uri in packedUris) {
                    try {
                        if (DocumentsContract.isDocumentUri(context, uri)) {
                            DocumentsContract.deleteDocument(context.contentResolver, uri)
                        } else if (uri.scheme == "file") {
                            uri.path?.let { File(it).delete() }
                        } else {
                            context.contentResolver.delete(uri, null, null)
                        }
                    } catch (deleteEx: Exception) {
                        deleteEx.printStackTrace()
                    }
                }
                successCount = pendingEntities.size
            } else {
                archiveManager.abortBatchSession(session)
                return@withContext Result.failure()
            }
        } catch (fatal: Exception) {
            fatal.printStackTrace()
            archiveManager.abortBatchSession(session)
            return@withContext Result.failure()
        }

        Result.success(workDataOf(KEY_SUCCESS_COUNT to successCount))
    }
}

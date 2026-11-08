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
        const val KEY_PLAYLIST_ID = "key_playlist_id"
        const val KEY_PROGRESS = "key_progress"
        const val KEY_TOTAL = "key_total"
        const val KEY_CURRENT_NAME = "key_current_name"
        const val KEY_SUCCESS_COUNT = "key_success_count"
        const val TAG = "ArchivePackWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val password = inputData.getString(KEY_PASSWORD) ?: return@withContext Result.failure()
        val uriStrings = inputData.getStringArray(KEY_URI_LIST) ?: return@withContext Result.failure()
        val targetPlaylistId = inputData.getLong(KEY_PLAYLIST_ID, -1L)

        val context = applicationContext
        val archiveManager = ArchiveManager(context)
        val database = AppDatabase.getDatabase(context)
        val dao = database.soyPicsDao()

        var successCount = 0
        val total = uriStrings.size

        for ((index, uriString) in uriStrings.withIndex()) {
            val uri = Uri.parse(uriString)
            var fileName = "image_${System.currentTimeMillis()}.jpg"
            var mimeType = "image/jpeg"

            try {
                // Query metadata from content resolver
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex) ?: fileName
                        }
                    }
                }
                context.contentResolver.getType(uri)?.let { mimeType = it }

                // 1. Pack image into gamelog.logz
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val packed = inputStream.use { stream ->
                        archiveManager.packImage(
                            input = stream,
                            originalFileName = fileName,
                            mimeType = mimeType,
                            password = password
                        )
                    }

                    // 2. Insert into Room Database
                    val entity = ImageEntity(
                        id = packed.id,
                        entryName = packed.entryName,
                        originalFileName = packed.originalFileName,
                        mimeType = packed.mimeType,
                        fileSizeBytes = packed.fileSizeBytes,
                        dateAdded = System.currentTimeMillis()
                    )
                    dao.insertImage(entity)

                    if (targetPlaylistId > 0) {
                        dao.addTag(ImagePlaylistCrossRef(packed.id, targetPlaylistId))
                    }

                    // 3. Delete original file upon 100% packing success
                    try {
                        if (DocumentsContract.isDocumentUri(context, uri)) {
                            DocumentsContract.deleteDocument(context.contentResolver, uri)
                        } else if (uri.scheme == "file") {
                            uri.path?.let { File(it).delete() }
                        } else {
                            // Attempt contentResolver deletion
                            context.contentResolver.delete(uri, null, null)
                        }
                    } catch (deleteEx: Exception) {
                        // In case OS restricts deletion of foreign tree, log and proceed
                        deleteEx.printStackTrace()
                    }

                    successCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            setProgress(
                workDataOf(
                    KEY_PROGRESS to (index + 1),
                    KEY_TOTAL to total,
                    KEY_CURRENT_NAME to fileName
                )
            )
        }

        Result.success(workDataOf(KEY_SUCCESS_COUNT to successCount))
    }
}

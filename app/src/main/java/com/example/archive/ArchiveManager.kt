package com.example.archive

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.example.crypto.CryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ArchiveStats(
    val exists: Boolean,
    val sizeBytes: Long,
    val formattedSize: String,
    val lastModifiedTimestamp: Long,
    val formattedLastModified: String,
    val filePath: String
)

data class PackedImageInfo(
    val id: String,
    val entryName: String,
    val originalFileName: String,
    val mimeType: String,
    val fileSizeBytes: Long
)

class ArchiveManager(private val context: Context) {

    val preferences = ArchivePreferences(context)
    private val lock = Any()

    fun getArchiveDisplayPath(): String = preferences.getArchiveDisplayPath()

    /**
     * Resolves DocumentFile for existing gamelog.logz (returns null if not found)
     */
    fun getArchiveDocumentFile(): DocumentFile? {
        val treeUriStr = preferences.getArchiveTreeUri()
        if (!treeUriStr.isNullOrBlank()) {
            try {
                val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUriStr))
                if (root != null) {
                    val existing = root.findFile("gamelog.logz")
                    if (existing != null && existing.exists()) {
                        return existing
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // Do NOT fall back to internal storage if user selected a custom SAF / SD card folder
            return null
        }
        val file = File(preferences.getArchiveDirectoryPath(), "gamelog.logz")
        return if (file.exists()) DocumentFile.fromFile(file) else null
    }

    /**
     * Resolves or creates DocumentFile for gamelog.logz (for writing)
     */
    fun getOrCreateArchiveDocumentFile(): DocumentFile? {
        val treeUriStr = preferences.getArchiveTreeUri()
        if (!treeUriStr.isNullOrBlank()) {
            try {
                val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUriStr))
                if (root != null) {
                    val existing = root.findFile("gamelog.logz")
                    if (existing != null && existing.exists()) {
                        return existing
                    }
                    val created = root.createFile("application/octet-stream", "gamelog.logz")
                    if (created != null) {
                        return created
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val dir = File(preferences.getArchiveDirectoryPath())
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "gamelog.logz")
        if (!file.exists()) {
            try { file.createNewFile() } catch (e: Exception) { e.printStackTrace() }
        }
        return DocumentFile.fromFile(file)
    }

    fun openArchiveInputStream(): InputStream? {
        val docFile = getArchiveDocumentFile() ?: return null
        return try {
            context.contentResolver.openInputStream(docFile.uri)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun openArchiveOutputStream(): OutputStream? {
        val docFile = getOrCreateArchiveDocumentFile() ?: return null
        return try {
            context.contentResolver.openOutputStream(docFile.uri, "wt")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Relocates archive to a new directory or SD card tree URI
     */
    fun relocateArchive(
        newDirectoryPath: String? = null,
        newTreeUri: Uri? = null,
        displayPath: String
    ): Boolean {
        synchronized(lock) {
            try {
                // 1. Read existing archive before updating preferences
                val oldDoc = getArchiveDocumentFile()
                val existingBytes = oldDoc?.let { doc ->
                    try {
                        context.contentResolver.openInputStream(doc.uri)?.use { it.readBytes() }
                    } catch (e: Exception) {
                        null
                    }
                }

                // 2. Update preferences
                if (newTreeUri != null) {
                    preferences.setArchiveTreeUri(newTreeUri.toString(), displayPath)
                } else if (!newDirectoryPath.isNullOrBlank()) {
                    preferences.setArchiveDirectoryPath(newDirectoryPath, displayPath)
                }

                // 3. Ensure archive file exists in new location
                val newDoc = getOrCreateArchiveDocumentFile()

                // 4. Transfer existing data to new location
                if (existingBytes != null && existingBytes.isNotEmpty() && newDoc != null) {
                    context.contentResolver.openOutputStream(newDoc.uri, "wt")?.use { os ->
                        os.write(existingBytes)
                        os.flush()
                    }
                    if (oldDoc != null && oldDoc.uri != newDoc.uri) {
                        try { oldDoc.delete() } catch (_: Exception) {}
                    }
                }
                return true
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }
    }

    /**
     * Reads metadata and statistics of gamelog.logz
     */
    fun getArchiveStats(): ArchiveStats {
        val docFile = getArchiveDocumentFile()
        val displayPath = preferences.getArchiveDisplayPath()

        if (docFile == null || !docFile.exists() || docFile.length() == 0L) {
            return ArchiveStats(
                exists = false,
                sizeBytes = 0L,
                formattedSize = "0.00 KB",
                lastModifiedTimestamp = 0L,
                formattedLastModified = "No archive yet",
                filePath = displayPath
            )
        }

        val sizeBytes = docFile.length()
        val formattedSize = when {
            sizeBytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", sizeBytes / (1024.0 * 1024.0))
            sizeBytes >= 1024 -> String.format(Locale.US, "%.1f KB", sizeBytes / 1024.0)
            else -> "$sizeBytes B"
        }

        val lastModified = docFile.lastModified()
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(lastModified))

        return ArchiveStats(
            exists = true,
            sizeBytes = sizeBytes,
            formattedSize = formattedSize,
            lastModifiedTimestamp = lastModified,
            formattedLastModified = formattedDate,
            filePath = displayPath
        )
    }

    /**
     * Encrypts and packs an image into gamelog.logz.
     * Byte obfuscation/encryption using AES-256 GCM with user password.
     */
    suspend fun packImage(
        input: InputStream,
        originalFileName: String,
        mimeType: String,
        password: String
    ): PackedImageInfo = withContext(Dispatchers.IO) {
        val rawBytes = input.readBytes()
        val encryptedBytes = CryptoUtils.encryptBytes(rawBytes, password)

        val id = UUID.randomUUID().toString()
        val randomTag = UUID.randomUUID().toString().replace("-", "").take(8)
        val entryName = "img_$randomTag.dat"

        synchronized(lock) {
            appendEntryToArchive(entryName, encryptedBytes)
        }

        PackedImageInfo(
            id = id,
            entryName = entryName,
            originalFileName = originalFileName,
            mimeType = mimeType,
            fileSizeBytes = rawBytes.size.toLong()
        )
    }

    /**
     * Appends an encrypted entry into the gamelog.logz container.
     * Uses local cache temp file for fast atomic operations.
     */
    private fun appendEntryToArchive(entryName: String, data: ByteArray) {
        val tempFile = File(context.cacheDir, "gamelog.logz.tmp")
        val inStream = openArchiveInputStream()

        if (inStream != null) {
            // Read existing entries and append new entry
            inStream.use { rawIn ->
                ZipInputStream(rawIn).use { zis ->
                    ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (entry.name != entryName) {
                                zos.putNextEntry(ZipEntry(entry.name))
                                zis.copyTo(zos)
                                zos.closeEntry()
                            }
                            entry = zis.nextEntry
                        }
                        // Add new entry
                        val newEntry = ZipEntry(entryName)
                        zos.putNextEntry(newEntry)
                        zos.write(data)
                        zos.closeEntry()
                    }
                }
            }
        } else {
            // First entry
            ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
                val newEntry = ZipEntry(entryName)
                zos.putNextEntry(newEntry)
                zos.write(data)
                zos.closeEntry()
            }
        }

        // Commit tempFile to target archive (supports SD card via content resolver)
        openArchiveOutputStream()?.use { os ->
            FileInputStream(tempFile).use { it.copyTo(os) }
            os.flush()
        }
        tempFile.delete()
    }

    /**
     * Reads raw encrypted bytes of an entry from gamelog.logz
     */
    fun readEncryptedEntry(entryName: String): ByteArray? {
        val inStream = openArchiveInputStream() ?: return null
        return synchronized(lock) {
            try {
                inStream.use { rawIn ->
                    ZipInputStream(rawIn).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (entry.name == entryName) {
                                val buffer = ByteArrayOutputStream()
                                zis.copyTo(buffer)
                                return@synchronized buffer.toByteArray()
                            }
                            entry = zis.nextEntry
                        }
                    }
                }
                null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Reads, decrypts, and decodes an entry into an Android Bitmap
     */
    suspend fun readDecryptedEntryToBitmap(entryName: String, password: String): Bitmap? = withContext(Dispatchers.IO) {
        val encrypted = readEncryptedEntry(entryName) ?: return@withContext null
        CryptoUtils.decryptToBitmap(encrypted, password)
    }

    /**
     * Rewrites gamelog.logz excluding deleted files to reclaim storage.
     */
    suspend fun compactArchive(validEntryNames: Set<String>): Boolean = withContext(Dispatchers.IO) {
        val inStream = openArchiveInputStream() ?: return@withContext true
        synchronized(lock) {
            val tempFile = File(context.cacheDir, "gamelog.logz.compact")
            try {
                inStream.use { rawIn ->
                    ZipInputStream(rawIn).use { zis ->
                        ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (validEntryNames.contains(entry.name)) {
                                    zos.putNextEntry(ZipEntry(entry.name))
                                    zis.copyTo(zos)
                                    zos.closeEntry()
                                }
                                entry = zis.nextEntry
                            }
                        }
                    }
                }

                // Commit compacted file to archive
                openArchiveOutputStream()?.use { os ->
                    FileInputStream(tempFile).use { it.copyTo(os) }
                    os.flush()
                }
                tempFile.delete()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                tempFile.delete()
                false
            }
        }
    }

    /**
     * Exports a decrypted image directly into public Downloads folder using MediaStore
     */
    suspend fun exportEntryToDownloads(
        entryName: String,
        originalFileName: String,
        mimeType: String,
        password: String
    ): Uri? = withContext(Dispatchers.IO) {
        val encrypted = readEncryptedEntry(entryName) ?: return@withContext null
        val decryptedBytes = try {
            CryptoUtils.decryptBytes(encrypted, password)
        } catch (e: Exception) {
            return@withContext null
        }

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, originalFileName.ifBlank { "SoyPics_${System.currentTimeMillis()}.jpg" })
            put(MediaStore.MediaColumns.MIME_TYPE, if (mimeType.isNotBlank()) mimeType else "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SoyPics")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, contentValues) ?: return@withContext null

        try {
            resolver.openOutputStream(uri)?.use { os ->
                os.write(decryptedBytes)
                os.flush()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            resolver.delete(uri, null, null)
            null
        }
    }

    /**
     * Clears archive file and preferences to wipe stale data
     */
    fun clearArchive() {
        synchronized(lock) {
            try { getArchiveDocumentFile()?.delete() } catch (_: Exception) {}
            try { File(context.filesDir, "gamelog.logz").delete() } catch (_: Exception) {}
            try {
                val publicDocs = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "SoyPics/gamelog.logz")
                publicDocs.delete()
            } catch (_: Exception) {}
            try {
                val publicDownloads = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SoyPics/gamelog.logz")
                publicDownloads.delete()
            } catch (_: Exception) {}
            try {
                context.cacheDir.listFiles()?.forEach { it.delete() }
            } catch (_: Exception) {}
            preferences.clearAllData()
        }
    }

    /**
     * Seeds sample images into archive for instant testing on fresh launch
     */
    suspend fun seedSampleImages(password: String): List<PackedImageInfo> = withContext(Dispatchers.IO) {
        val samples = listOf(
            SampleSeed(
                title = "Metallic Peak Sunrise.jpg",
                bgColor = 0xFF0D1B2A.toInt(),
                accentColor = 0xFF58A6FF.toInt(),
                label = "SOYPICS // VAULT ARCHIVE #01"
            ),
            SampleSeed(
                title = "Cybernetic Alpine.png",
                bgColor = 0xFF161B22.toInt(),
                accentColor = 0xFFEC4899.toInt(),
                label = "TOP SECRET // FILE #02"
            ),
            SampleSeed(
                title = "Titanium Glacier.jpg",
                bgColor = 0xFF0F172A.toInt(),
                accentColor = 0xFF10B981.toInt(),
                label = "ENCRYPTED METADATA // #03"
            ),
            SampleSeed(
                title = "Obsidian Ridge.jpg",
                bgColor = 0xFF1E1B4B.toInt(),
                accentColor = 0xFF8B5CF6.toInt(),
                label = "RESERVE VAULT // #04"
            )
        )

        val results = mutableListOf<PackedImageInfo>()
        for (sample in samples) {
            val bitmap = createSampleBitmap(sample)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
            val packed = packImage(
                input = ByteArrayInputStream(stream.toByteArray()),
                originalFileName = sample.title,
                mimeType = "image/jpeg",
                password = password
            )
            results.add(packed)
        }
        results
    }

    private data class SampleSeed(
        val title: String,
        val bgColor: Int,
        val accentColor: Int,
        val label: String
    )

    private fun createSampleBitmap(seed: SampleSeed): Bitmap {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(seed.bgColor)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = seed.accentColor
        paint.style = Paint.Style.FILL

        val path = android.graphics.Path().apply {
            moveTo(100f, 1600f)
            lineTo(450f, 800f)
            lineTo(800f, 1600f)
            close()
        }
        canvas.drawPath(path, paint)

        val path2 = android.graphics.Path().apply {
            paint.color = Color.argb(180, 200, 215, 230)
            moveTo(400f, 1600f)
            lineTo(720f, 950f)
            lineTo(1000f, 1600f)
            close()
        }
        canvas.drawPath(path2, paint)

        paint.color = Color.WHITE
        paint.textSize = 48f
        paint.textAlign = Paint.Align.CENTER
        paint.letterSpacing = 0.15f
        canvas.drawText(seed.label, (width / 2).toFloat(), 600f, paint)

        paint.textSize = 32f
        paint.color = Color.argb(160, 255, 255, 255)
        canvas.drawText("AES-256-GCM PROTECTED ARCHIVE", (width / 2).toFloat(), 670f, paint)

        return bitmap
    }
}

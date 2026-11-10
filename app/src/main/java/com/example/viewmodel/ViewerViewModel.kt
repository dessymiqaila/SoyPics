package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.archive.ArchivePreferences
import com.example.data.ImageEntity
import com.example.data.PlaylistEntity
import com.example.repository.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ViewerUiState(
    val images: List<ImageEntity> = emptyList(),
    val currentIndex: Int = 0,
    val isNext: Boolean = true,
    val slideOrientation: String = "VERTICAL", // "VERTICAL" or "HORIZONTAL"
    val smoothnessMs: Int = 400,
    val isShuffleEnabled: Boolean = true,
    val currentBitmap: Bitmap? = null,
    val isLoadingBitmap: Boolean = false,
    val isDeletedMode: Boolean = false,
    val activePlaylistId: Long = -1L,
    val activePlaylistName: String = "All Vault Photos",
    val activeTagIds: Set<Long> = emptySet(),
    val errorMessage: String? = null
)

class ViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VaultRepository(application)
    private val prefs = ArchivePreferences(application)

    private val _uiState = MutableStateFlow(
        ViewerUiState(
            slideOrientation = prefs.getSlideOrientation(),
            smoothnessMs = prefs.getSmoothnessMs(),
            isShuffleEnabled = prefs.isShuffleEnabled()
        )
    )
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    val allPlaylists: StateFlow<List<PlaylistEntity>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage = _userMessage.asSharedFlow()

    private var currentPassword: String = "soypics2026"
    private val memoryCache = mutableMapOf<String, Bitmap>()

    fun initViewer(
        playlistId: Long,
        playlistName: String,
        isDeletedMode: Boolean,
        initialImageId: String?,
        password: String
    ) {
        currentPassword = password
        memoryCache.clear()

        val shuffle = prefs.isShuffleEnabled()
        val orientation = prefs.getSlideOrientation()
        val smoothness = prefs.getSmoothnessMs()

        _uiState.update {
            it.copy(
                isDeletedMode = isDeletedMode,
                activePlaylistId = playlistId,
                activePlaylistName = playlistName,
                isShuffleEnabled = shuffle,
                slideOrientation = orientation,
                smoothnessMs = smoothness
            )
        }

        viewModelScope.launch {
            try {
                repository.cleanOrphanedImages()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val flow = when {
                isDeletedMode -> repository.deletedImages
                playlistId > 0 -> repository.getImagesForPlaylist(playlistId)
                else -> repository.activeImages
            }

            flow.collect { rawList ->
                // Apply randomized order if shuffle is enabled (default TRUE per user request)
                val processedList = if (shuffle) {
                    if (initialImageId != null) {
                        val selected = rawList.find { it.id == initialImageId }
                        val others = rawList.filterNot { it.id == initialImageId }.shuffled()
                        if (selected != null) listOf(selected) + others else rawList.shuffled()
                    } else {
                        rawList.shuffled()
                    }
                } else {
                    rawList
                }

                val targetIndex = if (initialImageId != null) {
                    val found = processedList.indexOfFirst { it.id == initialImageId }
                    if (found >= 0) found else 0
                } else {
                    val curr = _uiState.value.currentIndex
                    if (curr in processedList.indices) curr else 0
                }

                _uiState.update {
                    it.copy(
                        images = processedList,
                        currentIndex = targetIndex
                    )
                }

                if (processedList.isNotEmpty()) {
                    onPageChanged(targetIndex)
                } else {
                    _uiState.update { it.copy(currentBitmap = null) }
                }
            }
        }
    }

    suspend fun loadBitmap(entryName: String): Bitmap? = withContext(Dispatchers.IO) {
        memoryCache[entryName]?.let { return@withContext it }
        val effectivePassword = currentPassword.ifBlank { repository.getVaultPassword() }
        val bitmap = repository.loadDecryptedBitmap(entryName, effectivePassword)
        if (bitmap != null) {
            if (memoryCache.size > 50) {
                memoryCache.keys.firstOrNull()?.let { memoryCache.remove(it) }
            }
            memoryCache[entryName] = bitmap
        }
        bitmap
    }

    fun removeBrokenImage(imageId: String) {
        viewModelScope.launch {
            repository.hardDeleteImage(imageId)
            _userMessage.emit("Removed invalid image entry")
        }
    }

    fun onPageChanged(newIndex: Int) {
        val list = _uiState.value.images
        if (newIndex !in list.indices) return
        val currentImage = list[newIndex]

        _uiState.update { it.copy(currentIndex = newIndex) }

        viewModelScope.launch {
            val tags = repository.getTagIdsForImageSync(currentImage.id).toSet()
            _uiState.update { it.copy(activeTagIds = tags) }

            // Prefetch adjacent images (circular wrap) in background for instant zero-delay slide
            if (list.size > 1) {
                val nextIdx = (newIndex + 1) % list.size
                val prevIdx = if (newIndex - 1 < 0) list.size - 1 else newIndex - 1
                loadBitmap(list[nextIdx].entryName)
                loadBitmap(list[prevIdx].entryName)
            }
        }
    }

    fun goToNext() {
        val list = _uiState.value.images
        if (list.isEmpty()) return
        val nextIdx = (_uiState.value.currentIndex + 1) % list.size
        _uiState.update { it.copy(isNext = true) }
        onPageChanged(nextIdx)
    }

    fun goToPrev() {
        val list = _uiState.value.images
        if (list.isEmpty()) return
        val prevIdx = if (_uiState.value.currentIndex - 1 < 0) list.size - 1 else _uiState.value.currentIndex - 1
        _uiState.update { it.copy(isNext = false) }
        onPageChanged(prevIdx)
    }

    fun reshuffle() {
        val list = _uiState.value.images
        if (list.size <= 1) return
        val shuffled = list.shuffled()
        memoryCache.clear()
        _uiState.update {
            it.copy(
                images = shuffled,
                currentIndex = 0,
                isNext = true
            )
        }
        onPageChanged(0)
        viewModelScope.launch {
            _userMessage.emit("Reshuffled ${shuffled.size} images in random order")
        }
    }

    fun updateSettings(slideOrientation: String, smoothnessMs: Int, shuffle: Boolean) {
        prefs.setSlideOrientation(slideOrientation)
        prefs.setSmoothnessMs(smoothnessMs)
        prefs.setShuffleEnabled(shuffle)
        _uiState.update {
            it.copy(
                slideOrientation = slideOrientation,
                smoothnessMs = smoothnessMs,
                isShuffleEnabled = shuffle
            )
        }
        if (shuffle) {
            reshuffle()
        }
    }

    fun toggleDeleteOrRestore() {
        val list = _uiState.value.images
        val idx = _uiState.value.currentIndex
        if (idx !in list.indices) return
        val currentImage = list[idx]

        viewModelScope.launch {
            if (_uiState.value.isDeletedMode) {
                repository.restoreImage(currentImage.id)
                _userMessage.emit("Image restored from trash")
            } else {
                repository.softDeleteImage(currentImage.id)
                _userMessage.emit("Moved image to Deleted playlist")
            }
        }
    }

    fun toggleTagForCurrent(playlistId: Long) {
        val list = _uiState.value.images
        val idx = _uiState.value.currentIndex
        if (idx !in list.indices) return
        val currentImage = list[idx]

        viewModelScope.launch {
            repository.toggleTag(currentImage.id, playlistId)
            val updatedTags = repository.getTagIdsForImageSync(currentImage.id).toSet()
            _uiState.update { it.copy(activeTagIds = updatedTags) }
        }
    }

    fun exportActiveImage() {
        val list = _uiState.value.images
        val idx = _uiState.value.currentIndex
        if (idx !in list.indices) return
        val currentImage = list[idx]

        viewModelScope.launch {
            val uri = repository.exportImageToDownloads(
                entryName = currentImage.entryName,
                originalFileName = currentImage.originalFileName,
                mimeType = currentImage.mimeType,
                password = currentPassword
            )
            if (uri != null) {
                _userMessage.emit("Saved to Downloads/SoyPics: ${currentImage.originalFileName}")
            } else {
                _userMessage.emit("Export failed. Please check password.")
            }
        }
    }
}

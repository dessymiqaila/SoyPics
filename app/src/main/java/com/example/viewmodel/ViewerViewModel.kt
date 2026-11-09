package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    private val _uiState = MutableStateFlow(ViewerUiState())
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
        _uiState.update {
            it.copy(
                isDeletedMode = isDeletedMode,
                activePlaylistId = playlistId,
                activePlaylistName = playlistName
            )
        }

        viewModelScope.launch {
            val flow = when {
                isDeletedMode -> repository.deletedImages
                playlistId > 0 -> repository.getImagesForPlaylist(playlistId)
                else -> repository.activeImages
            }

            flow.collect { imageList ->
                val targetIndex = if (initialImageId != null) {
                    val found = imageList.indexOfFirst { it.id == initialImageId }
                    if (found >= 0) found else 0
                } else {
                    val curr = _uiState.value.currentIndex
                    if (curr in imageList.indices) curr else 0
                }

                _uiState.update {
                    it.copy(
                        images = imageList,
                        currentIndex = targetIndex
                    )
                }

                if (imageList.isNotEmpty()) {
                    onPageChanged(targetIndex)
                } else {
                    _uiState.update { it.copy(currentBitmap = null) }
                }
            }
        }
    }

    suspend fun loadBitmap(entryName: String): Bitmap? = withContext(Dispatchers.IO) {
        memoryCache[entryName]?.let { return@withContext it }
        val bitmap = repository.loadDecryptedBitmap(entryName, currentPassword)
        if (bitmap != null) {
            if (memoryCache.size > 40) {
                memoryCache.keys.firstOrNull()?.let { memoryCache.remove(it) }
            }
            memoryCache[entryName] = bitmap
        }
        bitmap
    }

    fun onPageChanged(newIndex: Int) {
        val list = _uiState.value.images
        if (newIndex !in list.indices) return
        val currentImage = list[newIndex]

        _uiState.update { it.copy(currentIndex = newIndex) }

        viewModelScope.launch {
            val tags = repository.getTagIdsForImageSync(currentImage.id).toSet()
            _uiState.update { it.copy(activeTagIds = tags) }

            // Prefetch adjacent images in background for instant zero-delay slide
            if (newIndex + 1 < list.size) {
                loadBitmap(list[newIndex + 1].entryName)
            }
            if (newIndex - 1 >= 0) {
                loadBitmap(list[newIndex - 1].entryName)
            }
        }
    }

    fun nextImage() {
        val list = _uiState.value.images
        if (list.isEmpty()) return
        val nextIdx = (_uiState.value.currentIndex + 1).coerceAtMost(list.size - 1)
        onPageChanged(nextIdx)
    }

    fun previousImage() {
        val list = _uiState.value.images
        if (list.isEmpty()) return
        val prevIdx = (_uiState.value.currentIndex - 1).coerceAtLeast(0)
        onPageChanged(prevIdx)
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

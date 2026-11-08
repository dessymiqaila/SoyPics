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

    fun initViewer(
        playlistId: Long,
        playlistName: String,
        isDeletedMode: Boolean,
        initialImageId: String?,
        password: String
    ) {
        currentPassword = password
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
                    loadBitmapForCurrent(targetIndex, imageList)
                } else {
                    _uiState.update { it.copy(currentBitmap = null) }
                }
            }
        }
    }

    private fun loadBitmapForCurrent(index: Int, list: List<ImageEntity> = _uiState.value.images) {
        if (index !in list.indices) return
        val image = list[index]

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBitmap = true, errorMessage = null) }
            val bitmap = repository.loadDecryptedBitmap(image.entryName, currentPassword)
            val tags = repository.getTagIdsForImageSync(image.id).toSet()

            _uiState.update {
                it.copy(
                    currentBitmap = bitmap,
                    isLoadingBitmap = false,
                    activeTagIds = tags,
                    errorMessage = if (bitmap == null) "Decryption failed (incorrect password?)" else null
                )
            }
        }
    }

    fun nextImage() {
        val list = _uiState.value.images
        if (list.isEmpty()) return
        val nextIdx = (_uiState.value.currentIndex + 1) % list.size
        _uiState.update { it.copy(currentIndex = nextIdx) }
        loadBitmapForCurrent(nextIdx)
    }

    fun previousImage() {
        val list = _uiState.value.images
        if (list.isEmpty()) return
        val prevIdx = if (_uiState.value.currentIndex - 1 < 0) list.size - 1 else _uiState.value.currentIndex - 1
        _uiState.update { it.copy(currentIndex = prevIdx) }
        loadBitmapForCurrent(prevIdx)
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

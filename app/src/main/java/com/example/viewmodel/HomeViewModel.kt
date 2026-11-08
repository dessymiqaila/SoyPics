package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.example.archive.ArchiveStats
import com.example.data.PlaylistWithCount
import com.example.repository.VaultRepository
import com.example.worker.ArchivePackWorker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class HomeUiState(
    val passwordInput: String = "soypics2026",
    val archiveDirectoryPath: String = "",
    val isVaultUnlocked: Boolean = true,
    val archiveStats: ArchiveStats = ArchiveStats(false, 0L, "0.00 KB", 0L, "No archive yet", ""),
    val isPackingActive: Boolean = false,
    val packingProgress: Int = 0,
    val packingTotal: Int = 0,
    val packingCurrentFile: String = "",
    val showEmptyTrashDialog: Boolean = false,
    val showCreatePlaylistDialog: Boolean = false,
    val showPasswordSheet: Boolean = false,
    val showArchiveLocationDialog: Boolean = false,
    val showFirstRunSetupDialog: Boolean = false,
    val isCompacting: Boolean = false,
    val statusMessage: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VaultRepository(application)

    private val _uiState = MutableStateFlow(
        HomeUiState(
            passwordInput = repository.getVaultPassword(),
            archiveDirectoryPath = repository.getArchiveDirectoryPath(),
            showFirstRunSetupDialog = !repository.isFirstSetupCompleted()
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val playlists: StateFlow<List<PlaylistWithCount>> = repository.playlistsWithCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalActiveImages: StateFlow<Int> = repository.activeImageCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalDeletedImages: StateFlow<Int> = repository.deletedImageCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage = _userMessage.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.ensureSixPlaylists()
            refreshStats()
        }
    }

    fun refreshStats() {
        val stats = repository.getArchiveStats()
        _uiState.update {
            it.copy(
                archiveStats = stats,
                archiveDirectoryPath = repository.getArchiveDirectoryPath(),
                passwordInput = repository.getVaultPassword()
            )
        }
    }

    fun onPasswordChanged(password: String) {
        repository.setVaultPassword(password)
        _uiState.update { it.copy(passwordInput = password) }
    }

    fun showEmptyTrashDialog(show: Boolean) {
        _uiState.update { it.copy(showEmptyTrashDialog = show) }
    }

    fun showCreatePlaylistDialog(show: Boolean) {
        _uiState.update { it.copy(showCreatePlaylistDialog = show) }
    }

    fun showPasswordSheet(show: Boolean) {
        _uiState.update { it.copy(showPasswordSheet = show) }
    }

    fun showArchiveLocationDialog(show: Boolean) {
        _uiState.update { it.copy(showArchiveLocationDialog = show) }
    }

    fun showFirstRunSetupDialog(show: Boolean) {
        _uiState.update { it.copy(showFirstRunSetupDialog = show) }
    }

    fun setArchiveLocation(newPath: String? = null, treeUri: Uri? = null, displayPath: String) {
        viewModelScope.launch {
            val success = repository.relocateArchive(newPath, treeUri, displayPath)
            refreshStats()
            if (success) {
                _userMessage.emit("Archive location updated: $displayPath")
            } else {
                _userMessage.emit("Failed to relocate archive.")
            }
        }
    }

    fun completeFirstRunSetup(password: String, folderPath: String, treeUri: Uri? = null, displayPath: String = folderPath) {
        viewModelScope.launch {
            if (password.isNotBlank()) {
                repository.setVaultPassword(password)
            }
            if (treeUri != null || folderPath.isNotBlank()) {
                repository.relocateArchive(folderPath, treeUri, displayPath)
            }
            repository.setFirstSetupCompleted(true)
            _uiState.update {
                it.copy(
                    showFirstRunSetupDialog = false,
                    passwordInput = repository.getVaultPassword(),
                    archiveDirectoryPath = repository.getArchiveDirectoryPath()
                )
            }
            refreshStats()
            _userMessage.emit("Vault setup complete! Location: $displayPath")
        }
    }

    fun clearAllAppData() {
        viewModelScope.launch {
            repository.clearAllAppData()
            repository.ensureSixPlaylists()
            _uiState.update {
                it.copy(
                    showFirstRunSetupDialog = true,
                    archiveDirectoryPath = repository.getArchiveDirectoryPath()
                )
            }
            refreshStats()
            _userMessage.emit("App preview data cleared. Initial setup reopened.")
        }
    }

    fun createPlaylist(name: String, colorHex: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name.trim(), colorHex)
            _uiState.update { it.copy(showCreatePlaylistDialog = false) }
            _userMessage.emit("Playlist '$name' created")
        }
    }

    fun emptyTrash() {
        val password = _uiState.value.passwordInput
        if (password.isBlank()) {
            viewModelScope.launch { _userMessage.emit("Please enter your vault password first") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCompacting = true, showEmptyTrashDialog = false) }
            val success = repository.emptyTrash(password)
            refreshStats()
            _uiState.update { it.copy(isCompacting = false) }
            if (success) {
                _userMessage.emit("Trash permanently purged and archive compacted!")
            } else {
                _userMessage.emit("Failed to compact archive.")
            }
        }
    }

    fun loadUris(uris: List<Uri>, targetPlaylistId: Long = -1L) {
        val password = _uiState.value.passwordInput
        if (password.isBlank()) {
            viewModelScope.launch { _userMessage.emit("Enter encryption password before packing images") }
            return
        }
        if (uris.isEmpty()) return

        val jobId = repository.enqueuePackJob(uris, password, targetPlaylistId)
        _uiState.update {
            it.copy(
                isPackingActive = true,
                packingProgress = 0,
                packingTotal = uris.size,
                packingCurrentFile = "Starting background packing..."
            )
        }

        // Observe WorkManager progress
        viewModelScope.launch {
            repository.getWorkInfoById(jobId).collect { workInfo ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            val progress = workInfo.progress.getInt(ArchivePackWorker.KEY_PROGRESS, 0)
                            val total = workInfo.progress.getInt(ArchivePackWorker.KEY_TOTAL, uris.size)
                            val currentName = workInfo.progress.getString(ArchivePackWorker.KEY_CURRENT_NAME) ?: ""
                            _uiState.update {
                                it.copy(
                                    isPackingActive = true,
                                    packingProgress = progress,
                                    packingTotal = total,
                                    packingCurrentFile = currentName
                                )
                            }
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            val count = workInfo.outputData.getInt(ArchivePackWorker.KEY_SUCCESS_COUNT, 0)
                            refreshStats()
                            _uiState.update { it.copy(isPackingActive = false) }
                            _userMessage.emit("Successfully packed $count image(s) into gamelog.logz!")
                        }
                        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                            refreshStats()
                            _uiState.update { it.copy(isPackingActive = false) }
                            _userMessage.emit("Packing ended or was interrupted")
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    fun seedSampleVault() {
        val password = _uiState.value.passwordInput
        if (password.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isPackingActive = true, packingCurrentFile = "Generating sample vault images...") }
            repository.seedSampleVault(password)
            refreshStats()
            _uiState.update { it.copy(isPackingActive = false) }
            _userMessage.emit("Sample encrypted vault images loaded!")
        }
    }
}

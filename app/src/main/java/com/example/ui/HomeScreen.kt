package com.example.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PlaylistWithCount
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ChromeWhite
import com.example.ui.theme.CrimsonTrash
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GunmetalElevated
import com.example.ui.theme.GunmetalSurface
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SteelBlueAccent
import com.example.ui.theme.TitaniumBorder
import com.example.viewmodel.HomeViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToViewer: (playlistId: Long, playlistName: String, isDeleted: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val activeCount by viewModel.totalActiveImages.collectAsStateWithLifecycle()
    val deletedCount by viewModel.totalDeletedImages.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.userMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var pendingTreeUri by remember { mutableStateOf<Uri?>(null) }
    var pendingDisplayPath by remember { mutableStateOf("") }

    // Launcher for selecting custom archive directory (supports SD cards & folders outside /Android/)
    val archiveFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val docId = try {
                DocumentsContract.getTreeDocumentId(treeUri)
            } catch (e: Exception) {
                treeUri.lastPathSegment ?: ""
            }
            val resolvedDisplay = if (docId.startsWith("primary:")) {
                val rel = docId.removePrefix("primary:")
                Environment.getExternalStorageDirectory().absolutePath + if (rel.startsWith("/")) rel else "/$rel"
            } else if (docId.contains(":")) {
                val vol = docId.substringBefore(":")
                val sub = docId.substringAfter(":")
                "SD Card ($vol): " + if (sub.isBlank()) "/" else "/$sub"
            } else if (docId.isNotBlank()) {
                "SD Card: /$docId"
            } else {
                "SD Card / Custom Storage"
            }
            pendingTreeUri = treeUri
            pendingDisplayPath = resolvedDisplay
            viewModel.setArchiveLocation(newPath = null, treeUri = treeUri, displayPath = resolvedDisplay)
            viewModel.showArchiveLocationDialog(false)
        }
    }

    // Launcher for folder picker to import images (ACTION_OPEN_DOCUMENT_TREE)
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            scope.launch {
                val uris = mutableListOf<Uri>()
                try {
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                        treeUri,
                        DocumentsContract.getTreeDocumentId(treeUri)
                    )
                    context.contentResolver.query(
                        childrenUri,
                        arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        while (cursor.moveToNext()) {
                            val docId = cursor.getString(idIndex)
                            val mime = cursor.getString(mimeIndex)
                            if (mime != null && (mime.startsWith("image/") || mime == "application/octet-stream")) {
                                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                                uris.add(docUri)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (uris.isNotEmpty()) {
                    viewModel.loadUris(uris)
                } else {
                    snackbarHostState.showSnackbar("No images found in the selected folder")
                }
            }
        }
    }

    // Direct image multi-picker fallback
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.loadUris(uris)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen"),
        containerColor = ObsidianBlack,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(SteelBlueAccent, Color(0xFF1E293B))
                                    )
                                )
                                .border(1.dp, TitaniumBorder, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Logo",
                                tint = ChromeWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            // Changed to Saidz Rehulka as requested
                            Text(
                                text = "Saidz Rehulka",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = ChromeWhite
                            )
                            Text(
                                text = "ENCRYPTED ARCHIVE // .logz",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp
                                ),
                                color = SteelBlueAccent
                            )
                        }
                    }
                },
                actions = {
                    // Archive directory relocation button
                    IconButton(
                        onClick = { viewModel.showArchiveLocationDialog(true) },
                        modifier = Modifier.testTag("archive_location_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Archive Location",
                            tint = SteelBlueAccent
                        )
                    }

                    // Password/Key configuration button
                    IconButton(
                        onClick = { viewModel.showPasswordSheet(true) },
                        modifier = Modifier.testTag("password_key_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Vault Key",
                            tint = if (uiState.passwordInput.isNotBlank()) SteelBlueAccent else MutedSlate
                        )
                    }

                    // Exit application button with confirmation
                    IconButton(
                        onClick = { showExitConfirmDialog = true },
                        modifier = Modifier.testTag("exit_app_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Exit Application",
                            tint = CrimsonTrash.copy(alpha = 0.85f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianBlack
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Bottom Action Bar: Load Folder & Import
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .background(ObsidianBlack)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("load_folder_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SteelBlueAccent,
                            contentColor = ObsidianBlack
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Folder",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Load Folder",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    OutlinedButton(
                        onClick = { filePickerLauncher.launch(arrayOf("image/*")) },
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("pick_images_button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ChromeWhite
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.linearGradient(listOf(TitaniumBorder, SteelBlueAccent))
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Pick Files",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Item 1: Telemetry & Stats Info Block (Full Width) with Archive Path Info
            item(span = { GridItemSpan(2) }) {
                TelemetryCard(
                    activeCount = activeCount,
                    formattedSize = uiState.archiveStats.formattedSize,
                    lastModified = uiState.archiveStats.formattedLastModified,
                    filePath = uiState.archiveStats.filePath,
                    archiveExists = uiState.archiveStats.exists,
                    onChangeLocationClick = { viewModel.showArchiveLocationDialog(true) }
                )
            }

            // Item 2: WorkManager Background Packing Indicator (Full Width)
            if (uiState.isPackingActive) {
                item(span = { GridItemSpan(2) }) {
                    PackingProgressCard(
                        progress = uiState.packingProgress,
                        total = uiState.packingTotal,
                        fileName = uiState.packingCurrentFile
                    )
                }
            }

            // Item 3: Quick Action / Browse All Card (Full Width)
            item(span = { GridItemSpan(2) }) {
                AllPhotosCard(
                    totalCount = activeCount,
                    onClick = {
                        onNavigateToViewer(-1L, "All Vault Photos", false)
                    }
                )
            }

            // Item 4: Header for 6 Playlists
            item(span = { GridItemSpan(2) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PLAYLISTS (1 - 6)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MutedSlate
                    )
                }
            }

            // 2-Column Grid of exactly 6 Playlists (1..6)
            items(playlists.distinctBy { it.tagNumber }.take(6), key = { it.tagNumber }) { playlist ->
                PlaylistGridCard(
                    playlist = playlist,
                    onClick = {
                        onNavigateToViewer(playlist.id, "Playlist ${playlist.tagNumber}", false)
                    }
                )
            }

            // Zero State for Empty Vault: Seed Sample Media Button
            if (activeCount == 0 && !uiState.isPackingActive) {
                item(span = { GridItemSpan(2) }) {
                    EmptyVaultSeedCard(
                        onSeedClick = { viewModel.seedSampleVault() }
                    )
                }
            }

            // Full-Width Card: "Deleted" (Trash)
            item(span = { GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(8.dp))
                DeletedBinCard(
                    deletedCount = deletedCount,
                    onClick = {
                        onNavigateToViewer(-2L, "Deleted Images", true)
                    }
                )
            }

            // Full-Width Button: "Empty Trash" with Cleanup Confirmation Dialog
            item(span = { GridItemSpan(2) }) {
                OutlinedButton(
                    onClick = { viewModel.showEmptyTrashDialog(true) },
                    enabled = deletedCount > 0 && !uiState.isCompacting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("empty_trash_button"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CrimsonTrash,
                        disabledContentColor = MutedSlate.copy(alpha = 0.5f)
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.linearGradient(
                            if (deletedCount > 0) listOf(CrimsonTrash, AmberWarning)
                            else listOf(TitaniumBorder, TitaniumBorder)
                        )
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "Empty Trash",
                        tint = if (deletedCount > 0) CrimsonTrash else MutedSlate,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isCompacting) "Compacting Archive..." else "Empty Trash (${deletedCount})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    // Dialog 1: Empty Trash Confirmation
    if (uiState.showEmptyTrashDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showEmptyTrashDialog(false) },
            containerColor = GunmetalSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = CrimsonTrash
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Permanent Wipe", color = ChromeWhite, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently empty the trash? This will rewrite gamelog.logz to purge $deletedCount deleted image(s) and reclaim physical disk space. This action cannot be undone.",
                    color = MutedSlate,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.emptyTrash() },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonTrash),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_empty_trash_button")
                ) {
                    Text("Permanently Erase", color = ChromeWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showEmptyTrashDialog(false) }) {
                    Text("Cancel", color = MutedSlate)
                }
            }
        )
    }

    // Dialog 2: Vault Encryption Key / Password Configuration
    if (uiState.showPasswordSheet) {
        var tempPassword by remember { mutableStateOf(uiState.passwordInput) }
        var showPasswordText by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { viewModel.showPasswordSheet(false) },
            containerColor = GunmetalSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = SteelBlueAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Vault Security Key", color = ChromeWhite, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Images in gamelog.logz are encrypted with AES-256 GCM using this master key. You must use the exact same key to decrypt previously saved archives.",
                        color = MutedSlate,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = tempPassword,
                        onValueChange = { tempPassword = it },
                        label = { Text("Encryption Password") },
                        singleLine = true,
                        visualTransformation = if (showPasswordText) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showPasswordText = !showPasswordText }) {
                                Text(if (showPasswordText) "Hide" else "Show", color = SteelBlueAccent, fontSize = 12.sp)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ChromeWhite,
                            unfocusedTextColor = ChromeWhite,
                            focusedBorderColor = SteelBlueAccent,
                            unfocusedBorderColor = TitaniumBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.onPasswordChanged(tempPassword)
                        viewModel.showPasswordSheet(false)
                        viewModel.refreshStats()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SteelBlueAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("save_password_button")
                ) {
                    Text("Apply Key", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showPasswordSheet(false) }) {
                    Text("Close", color = MutedSlate)
                }
            }
        )
    }

    // Dialog 3: Archive Location Configuration
    if (uiState.showArchiveLocationDialog) {
        var customPathInput by remember(pendingDisplayPath, uiState.archiveStats.filePath) {
            mutableStateOf(if (pendingDisplayPath.isNotBlank()) pendingDisplayPath else uiState.archiveStats.filePath)
        }
        val internalDefault = context.filesDir.absolutePath
        val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath + "/SoyPics"
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/SoyPics"

        AlertDialog(
            onDismissRequest = { viewModel.showArchiveLocationDialog(false) },
            containerColor = GunmetalSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = SteelBlueAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Archive Storage Location", color = ChromeWhite, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Specify where gamelog.logz will be stored. You can select an SD card or any folder outside /Android/.",
                        color = MutedSlate,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = customPathInput,
                        onValueChange = {
                            customPathInput = it
                            pendingDisplayPath = it
                            pendingTreeUri = null
                        },
                        label = { Text("Selected Archive Location") },
                        singleLine = false,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ChromeWhite,
                            unfocusedTextColor = ChromeWhite,
                            focusedBorderColor = SteelBlueAccent,
                            unfocusedBorderColor = TitaniumBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { archiveFolderPickerLauncher.launch(null) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SteelBlueAccent.copy(alpha = 0.2f),
                            contentColor = SteelBlueAccent
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Select SD Card or Custom Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Quick Presets:", color = MutedSlate, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                customPathInput = publicDocs
                                pendingDisplayPath = publicDocs
                                pendingTreeUri = null
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Documents", fontSize = 10.sp, color = SteelBlueAccent, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                customPathInput = publicDownloads
                                pendingDisplayPath = publicDownloads
                                pendingTreeUri = null
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Downloads", fontSize = 10.sp, color = SteelBlueAccent, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                customPathInput = internalDefault
                                pendingDisplayPath = internalDefault
                                pendingTreeUri = null
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Internal", fontSize = 10.sp, color = SteelBlueAccent, maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    // Option to wipe stale preview data
                    OutlinedButton(
                        onClick = {
                            viewModel.clearAllAppData()
                            viewModel.showArchiveLocationDialog(false)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonTrash),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonTrash.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clean Preview App Data", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CrimsonTrash)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setArchiveLocation(
                            newPath = if (pendingTreeUri == null) customPathInput else null,
                            treeUri = pendingTreeUri,
                            displayPath = customPathInput
                        )
                        viewModel.showArchiveLocationDialog(false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SteelBlueAccent),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Apply Location", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showArchiveLocationDialog(false) }) {
                    Text("Cancel", color = MutedSlate)
                }
            }
        )
    }

    // Dialog 4: First-Run Mandatory Setup (Choose Archive Location & Set Vault Key)
    if (uiState.showFirstRunSetupDialog) {
        var setupPassword by remember { mutableStateOf(uiState.passwordInput) }
        var setupDirectory by remember(pendingDisplayPath) {
            mutableStateOf(
                if (pendingDisplayPath.isNotBlank()) pendingDisplayPath
                else uiState.archiveStats.filePath.ifBlank {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath + "/SoyPics"
                }
            )
        }
        var showPasswordText by remember { mutableStateOf(false) }

        val internalDefault = context.filesDir.absolutePath
        val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath + "/SoyPics"
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/SoyPics"

        AlertDialog(
            onDismissRequest = { /* Require user to complete setup */ },
            containerColor = GunmetalElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = SteelBlueAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Welcome to Saidz Rehulka", color = ChromeWhite, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "To get started, please confirm the folder location for the gamelog.logz archive and set your master encryption key.",
                        color = MutedSlate,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Text("1. Archive Folder Location", color = SteelBlueAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = setupDirectory,
                        onValueChange = {
                            setupDirectory = it
                            pendingDisplayPath = it
                            pendingTreeUri = null
                        },
                        label = { Text("Storage Directory (e.g. SD Card or Documents)") },
                        singleLine = false,
                        maxLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ChromeWhite,
                            unfocusedTextColor = ChromeWhite,
                            focusedBorderColor = SteelBlueAccent,
                            unfocusedBorderColor = TitaniumBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = { archiveFolderPickerLauncher.launch(null) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SteelBlueAccent.copy(alpha = 0.2f),
                            contentColor = SteelBlueAccent
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Select SD Card or Custom Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                setupDirectory = publicDocs
                                pendingDisplayPath = publicDocs
                                pendingTreeUri = null
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Documents", fontSize = 10.sp, color = SteelBlueAccent, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                setupDirectory = publicDownloads
                                pendingDisplayPath = publicDownloads
                                pendingTreeUri = null
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Downloads", fontSize = 10.sp, color = SteelBlueAccent, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                setupDirectory = internalDefault
                                pendingDisplayPath = internalDefault
                                pendingTreeUri = null
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Internal", fontSize = 10.sp, color = SteelBlueAccent, maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("2. Master Encryption Key", color = SteelBlueAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = setupPassword,
                        onValueChange = { setupPassword = it },
                        label = { Text("Encryption Password") },
                        singleLine = true,
                        visualTransformation = if (showPasswordText) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showPasswordText = !showPasswordText }) {
                                Text(if (showPasswordText) "Hide" else "Show", color = SteelBlueAccent, fontSize = 11.sp)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ChromeWhite,
                            unfocusedTextColor = ChromeWhite,
                            focusedBorderColor = SteelBlueAccent,
                            unfocusedBorderColor = TitaniumBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.completeFirstRunSetup(
                            password = setupPassword,
                            folderPath = setupDirectory,
                            treeUri = pendingTreeUri,
                            displayPath = setupDirectory
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SteelBlueAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("complete_first_setup_button")
                ) {
                    Text("Initialize Vault", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Dialog 5: Exit Application Confirmation
    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            containerColor = GunmetalSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = CrimsonTrash)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exit Saidz Rehulka", color = ChromeWhite, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to lock the vault and exit the application? All encrypted images in gamelog.logz remain safely protected.",
                    color = MutedSlate,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmDialog = false
                        (context as? Activity)?.finishAffinity()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonTrash),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_exit_button")
                ) {
                    Text("Exit", color = ChromeWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmDialog = false }) {
                    Text("Cancel", color = MutedSlate)
                }
            }
        )
    }
}

@Composable
fun TelemetryCard(
    activeCount: Int,
    formattedSize: String,
    lastModified: String,
    filePath: String,
    archiveExists: Boolean,
    onChangeLocationClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("telemetry_card"),
        colors = CardDefaults.cardColors(containerColor = GunmetalSurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(TitaniumBorder, SteelBlueAccent.copy(alpha = 0.4f))))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = SteelBlueAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "gamelog.logz ARCHIVE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = ChromeWhite
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (archiveExists) EmeraldSuccess.copy(alpha = 0.15f) else AmberWarning.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (archiveExists) "ONLINE" else "STANDBY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = if (archiveExists) EmeraldSuccess else AmberWarning
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("TOTAL IMAGES", style = MaterialTheme.typography.labelSmall, color = MutedSlate)
                    Text(
                        text = "$activeCount",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = ChromeWhite
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("ARCHIVE SIZE", style = MaterialTheme.typography.labelSmall, color = MutedSlate)
                    Text(
                        text = formattedSize,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = SteelBlueAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TitaniumBorder)
            )
            Spacer(modifier = Modifier.height(10.dp))

            // File path and relocation info
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ARCHIVE LOCATION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MutedSlate
                    )
                    TextButton(
                        onClick = onChangeLocationClick,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DriveFileMove,
                            contentDescription = "Change Location",
                            tint = SteelBlueAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Change", fontSize = 11.sp, color = SteelBlueAccent)
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = filePath,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = ChromeWhite.copy(alpha = 0.85f),
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Modified: $lastModified",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MutedSlate
                )

                Text(
                    text = "AES-256 GCM",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = SteelBlueAccent
                )
            }
        }
    }
}

@Composable
fun PackingProgressCard(
    progress: Int,
    total: Int,
    fileName: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("packing_progress_card"),
        colors = CardDefaults.cardColors(containerColor = GunmetalElevated),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(SteelBlueAccent, EmeraldSuccess)))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Packing Archive & Purging Originals...",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = ChromeWhite
                )
                Text(
                    text = "$progress / $total",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = SteelBlueAccent
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            val fraction = if (total > 0) progress.toFloat() / total.toFloat() else 0f
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = SteelBlueAccent,
                trackColor = TitaniumBorder
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MutedSlate,
                maxLines = 1
            )
        }
    }
}

@Composable
fun AllPhotosCard(
    totalCount: Int,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("all_photos_card"),
        colors = CardDefaults.cardColors(containerColor = GunmetalElevated),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(TitaniumBorder, TitaniumBorder)))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SteelBlueAccent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Collections,
                        contentDescription = null,
                        tint = SteelBlueAccent
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "All Vault Photos",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ChromeWhite
                    )
                    Text(
                        text = "Entire encrypted archive",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSlate
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(GunmetalSurface)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "$totalCount",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = ChromeWhite
                )
            }
        }
    }
}

// 2-Column Grid Card for Playlists (NO NAME, PURE MINIMAL NUMBER 1..6)
@Composable
fun PlaylistGridCard(
    playlist: PlaylistWithCount,
    onClick: () -> Unit
) {
    val tagColor = try {
        Color(android.graphics.Color.parseColor(playlist.colorHex))
    } catch (e: Exception) {
        SteelBlueAccent
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(115.dp)
            .testTag("playlist_card_${playlist.tagNumber}"),
        colors = CardDefaults.cardColors(containerColor = GunmetalSurface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(TitaniumBorder, tagColor.copy(alpha = 0.35f))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large Tag Number Badge (1, 2, 3, 4, 5, 6)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(tagColor.copy(alpha = 0.2f))
                        .border(1.5.dp, tagColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${playlist.tagNumber}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = tagColor
                    )
                }

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(tagColor)
                )
            }

            // Image Count (No playlist name displayed, per user specification)
            Text(
                text = "${playlist.imageCount} IMAGES",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = ChromeWhite
            )
        }
    }
}

@Composable
fun DeletedBinCard(
    deletedCount: Int,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("deleted_bin_card"),
        colors = CardDefaults.cardColors(containerColor = GunmetalSurface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(TitaniumBorder, CrimsonTrash.copy(alpha = 0.4f))))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CrimsonTrash.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Deleted Bin",
                        tint = CrimsonTrash
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Deleted",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ChromeWhite
                    )
                    Text(
                        text = "Recycle bin (can be restored)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSlate
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(CrimsonTrash.copy(alpha = 0.2f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "$deletedCount",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = CrimsonTrash
                )
            }
        }
    }
}

@Composable
fun EmptyVaultSeedCard(
    onSeedClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("seed_sample_card"),
        colors = CardDefaults.cardColors(containerColor = GunmetalSurface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(TitaniumBorder, SteelBlueAccent)))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = SteelBlueAccent,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Vault is Empty",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = ChromeWhite
            )
            Text(
                text = "Load a folder of photos to encrypt into gamelog.logz, or seed demo images to test immediately.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedSlate,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(
                onClick = onSeedClick,
                colors = ButtonDefaults.buttonColors(containerColor = SteelBlueAccent),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("seed_sample_vault_button")
            ) {
                Text("Seed Demo Vault Images", color = ObsidianBlack, fontWeight = FontWeight.Bold)
            }
        }
    }
}

package com.example.ui

import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PlaylistEntity
import com.example.ui.theme.ChromeWhite
import com.example.ui.theme.CrimsonTrash
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GunmetalElevated
import com.example.ui.theme.GunmetalSurface
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SteelBlueAccent
import com.example.ui.theme.TitaniumBorder
import com.example.viewmodel.ViewerViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ImageViewerScreen(
    viewModel: ViewerViewModel,
    playlistId: Long,
    playlistName: String,
    isDeletedMode: Boolean,
    initialImageId: String? = null,
    vaultPassword: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allPlaylists by viewModel.allPlaylists.collectAsStateWithLifecycle()

    BackHandler {
        onBack()
    }

    // Initialize viewer
    LaunchedEffect(playlistId, isDeletedMode) {
        viewModel.initViewer(
            playlistId = playlistId,
            playlistName = playlistName,
            isDeletedMode = isDeletedMode,
            initialImageId = initialImageId,
            password = vaultPassword
        )
    }

    // Show toast / snackbar messages in English
    LaunchedEffect(Unit) {
        viewModel.userMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Automated Immersive Mode (Hide status & navigation bars)
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            val window = (context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .testTag("image_viewer_screen")
    ) {
        val screenHeight = maxHeight
        val density = LocalDensity.current
        val maxDragOffset = with(density) {
            ((screenHeight - 380.dp) / 2).coerceAtLeast(0.dp).toPx()
        }

        // Fullscreen Image Area: NO RIGHT PADDING! The image fills 100% of the screen.
        var totalDragX by remember { mutableFloatStateOf(0f) }
        var totalDragY by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(uiState.currentIndex) {
                    detectTapGestures(
                        onTap = {
                            // Tap to navigate Next
                            viewModel.nextImage()
                        }
                    )
                }
                .pointerInput(uiState.currentIndex) {
                    detectDragGestures(
                        onDragStart = {
                            totalDragX = 0f
                            totalDragY = 0f
                        },
                        onDragEnd = {
                            val threshold = 50f
                            // Swipe Left or Swipe Up -> Prev
                            // Swipe Right or Swipe Down -> Next
                            if (totalDragX < -threshold || totalDragY < -threshold) {
                                viewModel.previousImage()
                            } else if (totalDragX > threshold || totalDragY > threshold) {
                                viewModel.nextImage()
                            }
                            totalDragX = 0f
                            totalDragY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount.x
                            totalDragY += dragAmount.y
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (uiState.images.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MutedSlate,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isDeletedMode) "No Deleted Images" else "No Images in this Playlist",
                        color = ChromeWhite,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Import or tag photos to populate",
                        color = MutedSlate,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                // Smooth Depth & Fade Transition between images
                AnimatedContent(
                    targetState = uiState.currentIndex,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(260)) + scaleIn(initialScale = 0.94f, animationSpec = tween(260)))
                            .togetherWith(fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 1.05f, animationSpec = tween(180)))
                    },
                    label = "image_depth_fade_transition",
                    modifier = Modifier.fillMaxSize()
                ) { targetIdx ->
                    val bitmap = uiState.currentBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Encrypted Vault Image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("viewer_image")
                        )
                    } else if (uiState.isLoadingBitmap) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = SteelBlueAccent)
                        }
                    } else if (uiState.errorMessage != null) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Decryption Failed",
                                color = CrimsonTrash,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Ensure the current vault password matches the archive key.",
                                color = MutedSlate,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Top Floating Action Bar (Overlay)
        TopFloatingActionBar(
            title = uiState.activePlaylistName,
            currentIndex = if (uiState.images.isNotEmpty()) uiState.currentIndex + 1 else 0,
            totalCount = uiState.images.size,
            isDeletedMode = uiState.isDeletedMode,
            hasImages = uiState.images.isNotEmpty(),
            onBack = onBack,
            onDeleteOrRestore = { viewModel.toggleDeleteOrRestore() },
            onDownload = { viewModel.exportActiveImage() }
        )

        // Right Sidebar OVERLAY: Floating directly on top of the image (TikTok style numbers 1..6)
        // Can be slid/dragged up and down freely along the right edge!
        if (!uiState.isDeletedMode && uiState.images.isNotEmpty()) {
            TikTokPlaylistOverlay(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                maxDragOffset = maxDragOffset,
                playlists = allPlaylists.distinctBy { it.tagNumber }.take(6),
                activeTagIds = uiState.activeTagIds,
                onToggleTag = { playlistId ->
                    viewModel.toggleTagForCurrent(playlistId)
                }
            )
        }

        // Snackbar host for download / delete confirmation
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

@Composable
fun TopFloatingActionBar(
    title: String,
    currentIndex: Int,
    totalCount: Int,
    isDeletedMode: Boolean,
    hasImages: Boolean,
    onBack: () -> Unit,
    onDeleteOrRestore: () -> Unit,
    onDownload: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = GunmetalSurface.copy(alpha = 0.88f),
            border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumBorder.copy(alpha = 0.8f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("viewer_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Home",
                        tint = ChromeWhite
                    )
                }

                // Middle Info: Title and index count
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = ChromeWhite,
                        maxLines = 1
                    )
                    if (totalCount > 0) {
                        Text(
                            text = "$currentIndex / $totalCount",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = SteelBlueAccent
                        )
                    }
                }

                // Actions: Delete/Restore & Download
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasImages) {
                        // Delete or Restore Button
                        IconButton(
                            onClick = onDeleteOrRestore,
                            modifier = Modifier.testTag("viewer_delete_restore_button")
                        ) {
                            if (isDeletedMode) {
                                Icon(
                                    imageVector = Icons.Default.RestoreFromTrash,
                                    contentDescription = "Restore Image",
                                    tint = EmeraldSuccess
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Image",
                                    tint = CrimsonTrash
                                )
                            }
                        }

                        // Download to Public Downloads Button
                        IconButton(
                            onClick = onDownload,
                            modifier = Modifier.testTag("viewer_download_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download to Storage",
                                tint = SteelBlueAccent
                            )
                        }
                    }
                }
            }
        }
    }
}

// Floating TikTok-style Overlay (Floating directly over the right edge of the image)
// Supports vertical sliding/dragging up and down so user can reposition it freely!
@Composable
fun TikTokPlaylistOverlay(
    modifier: Modifier = Modifier,
    maxDragOffset: Float,
    playlists: List<PlaylistEntity>,
    activeTagIds: Set<Long>,
    onToggleTag: (playlistId: Long) -> Unit
) {
    var offsetY by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .pointerInput(maxDragOffset) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    offsetY = (offsetY + dragAmount).coerceIn(-maxDragOffset, maxDragOffset)
                }
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Subtle top drag handle
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.35f))
        )

        playlists.forEach { playlist ->
            val isTagged = activeTagIds.contains(playlist.id)
            val playlistColor = try {
                Color(android.graphics.Color.parseColor(playlist.colorHex))
            } catch (e: Exception) {
                SteelBlueAccent
            }

            // Sleek round number button (1, 2, 3, 4, 5, 6)
            // NO outer box, NO rectangular shadow. Pure floating round circle!
            // Inactive: highly transparent (alpha = 0.15f) with faint border so it blends into the photo
            // Active: vivid solid playlist color with sharp border
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (isTagged) playlistColor else Color.Black.copy(alpha = 0.15f)
                    )
                    .border(
                        width = if (isTagged) 2.dp else 1.dp,
                        color = if (isTagged) ChromeWhite else Color.White.copy(alpha = 0.22f),
                        shape = CircleShape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onToggleTag(playlist.id)
                    }
                    .testTag("tiktok_tag_${playlist.tagNumber}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${playlist.tagNumber}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isTagged) FontWeight.ExtraBold else FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp
                    ),
                    color = if (isTagged) ChromeWhite else ChromeWhite.copy(alpha = 0.35f)
                )
            }
        }

        // Subtle bottom drag handle
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.35f))
        )
    }
}

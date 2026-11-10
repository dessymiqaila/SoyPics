package com.example.ui

import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.archive.ArchivePreferences
import com.example.data.ImageEntity
import com.example.data.PlaylistEntity
import com.example.ui.theme.ChromeWhite
import com.example.ui.theme.CrimsonTrash
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SteelBlueAccent
import com.example.viewmodel.SlideDirection
import com.example.viewmodel.ViewerViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ImageViewerScreen(
    viewModel: ViewerViewModel,
    playlistId: Long,
    playlistName: String,
    isDeletedMode: Boolean,
    initialImageId: String? = null,
    vaultPassword: String,
    onThemeChanged: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }
    val prefs = remember { ArchivePreferences(context) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allPlaylists by viewModel.allPlaylists.collectAsStateWithLifecycle()

    var showSettingsDialog by remember { mutableStateOf(false) }

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

        if (uiState.images.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
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
            }
        } else {
            // Main Photo Area with Custom Gestures & Configurable Smooth Slide
            // Gestures per user request:
            // 1. Sentuh di mana aja kecuali ikon atas & playlist -> NEXT img
            // 2. Geser ke atas = PREV
            // 3. Geser ke bawah = NEXT
            // 4. Geser ke kiri = PREV
            // 5. Geser ke kanan = NEXT
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(uiState.images.size) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startPos = down.position
                            var totalX = 0f
                            var totalY = 0f
                            var hasMoved = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.isConsumed) break

                                if (!change.pressed) {
                                    // Pointer released (Up event)
                                    val minSwipe = 35f
                                    val absX = abs(totalX)
                                    val absY = abs(totalY)

                                    if (hasMoved && (absX > minSwipe || absY > minSwipe)) {
                                        change.consume()
                                        if (absY >= absX) {
                                            // Vertical Swipe (TikTok style)
                                            if (totalY < -minSwipe) {
                                                // Geser ke ATAS = NEXT, muncul dr BAWAH
                                                viewModel.goToNext(SlideDirection.FROM_BOTTOM)
                                            } else {
                                                // Geser ke BAWAH = PREV, muncul dr ATAS
                                                viewModel.goToPrev(SlideDirection.FROM_TOP)
                                            }
                                        } else {
                                            // Horizontal Swipe
                                            if (totalX < -minSwipe) {
                                                // Geser ke KIRI = NEXT, muncul dr KANAN
                                                viewModel.goToNext(SlideDirection.FROM_RIGHT)
                                            } else {
                                                // Geser ke KANAN = PREV, muncul dr KIRI
                                                viewModel.goToPrev(SlideDirection.FROM_LEFT)
                                            }
                                        }
                                    } else {
                                        // Sentuh biasa = NEXT dengan img dr BAWAH
                                        change.consume()
                                        viewModel.goToNext(SlideDirection.FROM_BOTTOM)
                                    }
                                    break
                                } else {
                                    val delta = change.position - startPos
                                    totalX = delta.x
                                    totalY = delta.y
                                    if (abs(totalX) > 12f || abs(totalY) > 12f) {
                                        hasMoved = true
                                    }
                                }
                            }
                        }
                    }
            ) {
                val currentIndex = uiState.currentIndex
                val smoothness = uiState.smoothnessMs

                // AnimatedContent with zero-bounce FastOutSlowInEasing & exact physical directional slide
                AnimatedContent(
                    targetState = currentIndex,
                    transitionSpec = {
                        val duration = smoothness.coerceIn(150, 1000)
                        val spec = tween<IntOffset>(durationMillis = duration, easing = FastOutSlowInEasing)
                        val fadeSpec = tween<Float>(durationMillis = duration / 2)

                        when (uiState.slideDirection) {
                            SlideDirection.FROM_BOTTOM -> {
                                // Enters from bottom, exits to top
                                (slideInVertically(animationSpec = spec) { it } + fadeIn(fadeSpec))
                                    .togetherWith(slideOutVertically(animationSpec = spec) { -it } + fadeOut(fadeSpec))
                            }
                            SlideDirection.FROM_TOP -> {
                                // Enters from top, exits to bottom
                                (slideInVertically(animationSpec = spec) { -it } + fadeIn(fadeSpec))
                                    .togetherWith(slideOutVertically(animationSpec = spec) { it } + fadeOut(fadeSpec))
                            }
                            SlideDirection.FROM_RIGHT -> {
                                // Enters from right, exits to left
                                (slideInHorizontally(animationSpec = spec) { it } + fadeIn(fadeSpec))
                                    .togetherWith(slideOutHorizontally(animationSpec = spec) { -it } + fadeOut(fadeSpec))
                            }
                            SlideDirection.FROM_LEFT -> {
                                // Enters from left, exits to right
                                (slideInHorizontally(animationSpec = spec) { -it } + fadeIn(fadeSpec))
                                    .togetherWith(slideOutHorizontally(animationSpec = spec) { it } + fadeOut(fadeSpec))
                            }
                        }
                    },
                    label = "image_slide_transition",
                    modifier = Modifier.fillMaxSize()
                ) { targetIdx ->
                    if (targetIdx in uiState.images.indices) {
                        val image = uiState.images[targetIdx]
                        ViewerImageDisplay(
                            image = image,
                            loadBitmap = { viewModel.loadBitmap(image.entryName) },
                            onRemoveBroken = { viewModel.removeBrokenImage(image.id) }
                        )
                    }
                }
            }
        }

        // Top Floating Action Icons (NO background block! Pure floating translucent icons with touch feedback)
        TopFloatingOverlayIcons(
            currentIndex = if (uiState.images.isNotEmpty()) uiState.currentIndex + 1 else 0,
            totalCount = uiState.images.size,
            isDeletedMode = uiState.isDeletedMode,
            hasImages = uiState.images.isNotEmpty(),
            onBack = onBack,
            onOpenSettings = { showSettingsDialog = true },
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

        // Settings Dialog Modal
        if (showSettingsDialog) {
            SettingsDialog(
                currentTheme = prefs.getThemeMode(),
                currentSlideOrientation = uiState.slideOrientation,
                currentSmoothnessMs = uiState.smoothnessMs,
                currentShuffleEnabled = uiState.isShuffleEnabled,
                onThemeChanged = { newTheme ->
                    prefs.setThemeMode(newTheme)
                    onThemeChanged(newTheme)
                },
                onSlideOrientationChanged = { newOrientation ->
                    viewModel.updateSettings(
                        slideOrientation = newOrientation,
                        smoothnessMs = uiState.smoothnessMs,
                        shuffle = uiState.isShuffleEnabled
                    )
                },
                onSmoothnessChanged = { newMs ->
                    viewModel.updateSettings(
                        slideOrientation = uiState.slideOrientation,
                        smoothnessMs = newMs,
                        shuffle = uiState.isShuffleEnabled
                    )
                },
                onShuffleChanged = { newShuffle ->
                    viewModel.updateSettings(
                        slideOrientation = uiState.slideOrientation,
                        smoothnessMs = uiState.smoothnessMs,
                        shuffle = newShuffle
                    )
                },
                onReshuffleNow = {
                    viewModel.reshuffle()
                },
                onDismiss = { showSettingsDialog = false }
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

/**
 * Top Floating Overlay Icons:
 * Completely removes any rectangular bar or block from the top of the photo.
 * Only leaves standalone, semi-transparent circular buttons with touch feedback (Material ripple),
 * styled identically to the playlist pills for a seamless aesthetic.
 */
@Composable
fun TopFloatingOverlayIcons(
    currentIndex: Int,
    totalCount: Int,
    isDeletedMode: Boolean,
    hasImages: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onDeleteOrRestore: () -> Unit,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Floating Back Button
        FloatingTranslucentIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = ChromeWhite,
            testTag = "viewer_back_button",
            onClick = onBack
        )

        // Middle: Floating Index Badge Capsule
        if (totalCount > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$currentIndex / $totalCount",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = ChromeWhite
                )
            }
        } else {
            Spacer(modifier = Modifier.width(1.dp))
        }

        // Right: Floating Action Buttons (Settings, Download, Delete/Restore)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Settings Button
            FloatingTranslucentIconButton(
                icon = Icons.Default.Settings,
                contentDescription = "Viewer Settings",
                tint = SteelBlueAccent,
                testTag = "viewer_settings_button",
                onClick = onOpenSettings
            )

            if (hasImages) {
                // Download Button
                FloatingTranslucentIconButton(
                    icon = Icons.Default.Download,
                    contentDescription = "Download Image",
                    tint = SteelBlueAccent,
                    testTag = "viewer_download_button",
                    onClick = onDownload
                )

                // Delete / Restore Button
                FloatingTranslucentIconButton(
                    icon = if (isDeletedMode) Icons.Default.RestoreFromTrash else Icons.Default.Delete,
                    contentDescription = if (isDeletedMode) "Restore Image" else "Delete Image",
                    tint = if (isDeletedMode) EmeraldSuccess else CrimsonTrash,
                    testTag = "viewer_delete_restore_button",
                    onClick = onDeleteOrRestore
                )
            }
        }
    }
}

/**
 * Translucent circular icon button with touch ripple effect,
 * visually harmonized with the TikTok-style playlist numbers.
 */
@Composable
fun FloatingTranslucentIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.22f),
                shape = CircleShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = ChromeWhite),
                onClick = onClick
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
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
            // Pure floating round circle with touch ripple effect
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (isTagged) playlistColor else Color.Black.copy(alpha = 0.18f)
                    )
                    .border(
                        width = if (isTagged) 2.dp else 1.dp,
                        color = if (isTagged) ChromeWhite else Color.White.copy(alpha = 0.22f),
                        shape = CircleShape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = ChromeWhite)
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
                    color = if (isTagged) ChromeWhite else ChromeWhite.copy(alpha = 0.4f)
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

/**
 * Loads, caches, and renders its decrypted bitmap
 * for instant zero-latency slide without flickering or bounce.
 */
@Composable
private fun ViewerImageDisplay(
    image: ImageEntity,
    loadBitmap: suspend () -> Bitmap?,
    onRemoveBroken: () -> Unit
) {
    var bitmap by remember(image.id) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(image.id) { mutableStateOf(true) }
    var failed by remember(image.id) { mutableStateOf(false) }

    LaunchedEffect(image.id) {
        val loaded = loadBitmap()
        if (loaded != null) {
            bitmap = loaded
            isLoading = false
            failed = false
        } else {
            isLoading = false
            failed = true
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = image.originalFileName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("viewer_image")
            )
        } else if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = SteelBlueAccent,
                    modifier = Modifier.size(36.dp)
                )
            }
        } else if (failed) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = CrimsonTrash,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Image Unavailable",
                    color = CrimsonTrash,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "File missing from archive (aborted import) or memory decode failed.",
                    color = MutedSlate,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onRemoveBroken,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = CrimsonTrash,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Remove Broken Entry",
                        color = CrimsonTrash,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

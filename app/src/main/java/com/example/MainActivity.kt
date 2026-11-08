package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.HomeScreen
import com.example.ui.ImageViewerScreen
import com.example.ui.theme.SoyPicsTheme
import com.example.viewmodel.HomeViewModel
import com.example.viewmodel.ViewerViewModel

sealed class AppDestination {
    data object Home : AppDestination()
    data class Viewer(
        val playlistId: Long,
        val playlistName: String,
        val isDeleted: Boolean
    ) : AppDestination()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SoyPicsTheme {
                SoyPicsApp()
            }
        }
    }
}

@Composable
fun SoyPicsApp() {
    val homeViewModel: HomeViewModel = viewModel()
    val viewerViewModel: ViewerViewModel = viewModel()

    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()

    var currentScreen by remember { mutableStateOf<AppDestination>(AppDestination.Home) }

    Crossfade(targetState = currentScreen, label = "screen_transition", modifier = Modifier.fillMaxSize()) { destination ->
        when (destination) {
            is AppDestination.Home -> {
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToViewer = { playlistId, playlistName, isDeleted ->
                        currentScreen = AppDestination.Viewer(playlistId, playlistName, isDeleted)
                    }
                )
            }
            is AppDestination.Viewer -> {
                ImageViewerScreen(
                    viewModel = viewerViewModel,
                    playlistId = destination.playlistId,
                    playlistName = destination.playlistName,
                    isDeletedMode = destination.isDeleted,
                    vaultPassword = homeUiState.passwordInput,
                    onBack = {
                        currentScreen = AppDestination.Home
                        homeViewModel.refreshStats()
                    }
                )
            }
        }
    }
}

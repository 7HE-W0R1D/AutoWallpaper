package com.example.autowallpaper.ui.main

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.autowallpaper.ThemeWallpaperService
import com.example.autowallpaper.data.SettingsManager

@Composable
fun MainNavigation() {
  val context = LocalContext.current
  val settingsManager = remember { SettingsManager(context.applicationContext) }
  val viewModel: MainScreenViewModel = viewModel {
    MainScreenViewModel(settingsManager, context.applicationContext)
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var currentScreen by remember { mutableStateOf(Screen.Home) }

  Box(modifier = Modifier.fillMaxSize()) {
    // Main Content - Scaffold now only handles the main surface
    Scaffold(
      containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
      // Pass the padding to screens so they can handle their own content spacing
      Box(modifier = Modifier.padding(innerPadding)) {
        when (currentScreen) {
          Screen.Home -> HomeScreen(state = state, viewModel = viewModel)
          Screen.Settings -> SettingsScreen(state = state, viewModel = viewModel)
        }
      }
    }

    // Truly Floating Navigation Bar - decoulped from Scaffold and constrained to content width
    Row(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .padding(bottom = 16.dp)
        .wrapContentSize(), // Only takes up space for the pill and FAB
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      // Compact Nav Pill (Left)
      FloatingNavigationBar(
        currentScreen = currentScreen,
        onScreenSelected = { currentScreen = it }
      )

      Spacer(modifier = Modifier.width(12.dp))

      // Set Wallpaper FAB (Right)
      Surface(
        modifier = Modifier
          .size(64.dp)
          .clickable { launchLiveWallpaperChooser(context) },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 6.dp,
        tonalElevation = 3.dp
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = "Set Wallpaper",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(32.dp)
          )
        }
      }
    }
  }
}

enum class Screen(val icon: ImageVector, val label: String) {
  Home(Icons.Rounded.Wallpaper, "Preview"),
  Settings(Icons.Rounded.Settings, "Settings")
}

@Composable
fun FloatingNavigationBar(
  currentScreen: Screen,
  onScreenSelected: (Screen) -> Unit
) {
  Surface(
    modifier = Modifier
      .height(64.dp)
      .wrapContentWidth(),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
    shadowElevation = 6.dp,
    tonalElevation = 3.dp
  ) {
    Row(
      modifier = Modifier
        .padding(horizontal = 8.dp)
        .fillMaxHeight(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Screen.entries.forEach { screen ->
        val selected = currentScreen == screen
        val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .clip(CircleShape)
            .clickable { onScreenSelected(screen) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
          Icon(
            imageVector = screen.icon,
            contentDescription = screen.label,
            tint = color,
            modifier = Modifier.size(28.dp)
          )
        }
      }
    }
  }
}

private fun launchLiveWallpaperChooser(context: Context) {
  try {
    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
      putExtra(
        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
        ComponentName(context, ThemeWallpaperService::class.java)
      )
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  } catch (e: Exception) {
    try {
      val intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (ex: Exception) {
      ex.printStackTrace()
    }
  }
}

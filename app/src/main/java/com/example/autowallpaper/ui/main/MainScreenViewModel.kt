package com.example.autowallpaper.ui.main

import android.app.WallpaperManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.autowallpaper.ThemeWallpaperService
import com.example.autowallpaper.data.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class MainUiState(
  val blurEnabled: Boolean = false,
  val blurRadius: Int = 15,
  val lightWallpaperFile: File? = null,
  val darkWallpaperFile: File? = null,
  val usingCustomLight: Boolean = false,
  val usingCustomDark: Boolean = false,
  val isServiceActive: Boolean = false,
  val refreshTrigger: Int = 0
)

class MainScreenViewModel(
  private val settingsManager: SettingsManager,
  private val context: Context
) : ViewModel() {

  private val _uiState = MutableStateFlow(MainUiState())
  val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

  init {
    refreshState()
  }

  fun refreshState() {
    viewModelScope.launch {
      _uiState.update {
        MainUiState(
          blurEnabled = settingsManager.blurEnabled,
          blurRadius = settingsManager.blurRadius,
          lightWallpaperFile = settingsManager.getLightWallpaperFile(),
          darkWallpaperFile = settingsManager.getDarkWallpaperFile(),
          usingCustomLight = settingsManager.usingCustomLight,
          usingCustomDark = settingsManager.usingCustomDark,
          isServiceActive = checkServiceActive(),
          refreshTrigger = it.refreshTrigger + 1
        )
      }
    }
  }

  fun setBlurEnabled(enabled: Boolean) {
    settingsManager.blurEnabled = enabled
    _uiState.update { it.copy(blurEnabled = enabled) }
  }

  fun setBlurRadius(radius: Int) {
    settingsManager.blurRadius = radius
    _uiState.update { it.copy(blurRadius = radius) }
  }

  fun saveCustomWallpaper(isDark: Boolean, uri: Uri): Boolean {
    val success = settingsManager.saveCustomWallpaper(isDark, uri)
    if (success) {
      refreshState()
    }
    return success
  }

  fun getWallpaperFile(isDark: Boolean): File {
    return if (isDark) settingsManager.getDarkWallpaperFile() else settingsManager.getLightWallpaperFile()
  }

  fun resetWallpaper(isDark: Boolean) {
    if (settingsManager.resetWallpaper(isDark)) {
      refreshState()
    }
  }

  private fun checkServiceActive(): Boolean {
    return try {
      val wpm = WallpaperManager.getInstance(context)
      val info = wpm.wallpaperInfo
      info != null && info.packageName == context.packageName && info.serviceName == ThemeWallpaperService::class.java.name
    } catch (e: Exception) {
      false
    }
  }
}

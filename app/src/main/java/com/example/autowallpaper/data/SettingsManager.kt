package com.example.autowallpaper.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.example.autowallpaper.utils.WallpaperGenerator
import java.io.File

class SettingsManager(private val context: Context) {

  private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  companion object {
    private const val PREFS_NAME = "auto_wallpaper_prefs"
    private const val KEY_BLUR_ENABLED = "blur_enabled"
    private const val KEY_BLUR_RADIUS = "blur_radius"
    private const val KEY_USING_CUSTOM_LIGHT = "using_custom_light"
    private const val KEY_USING_CUSTOM_DARK = "using_custom_dark"
    
    const val ACTION_SETTINGS_CHANGED = "com.example.autowallpaper.ACTION_SETTINGS_CHANGED"
    
    const val LIGHT_WALLPAPER_FILENAME = "light_wallpaper.png"
    const val DARK_WALLPAPER_FILENAME = "dark_wallpaper.png"
  }

  var blurEnabled: Boolean
    get() = prefs.getBoolean(KEY_BLUR_ENABLED, false)
    set(value) {
      prefs.edit().putBoolean(KEY_BLUR_ENABLED, value).apply()
      notifyChanged()
    }

  var blurRadius: Int
    get() = prefs.getInt(KEY_BLUR_RADIUS, 15)
    set(value) {
      val clamped = value.coerceIn(1, 25)
      prefs.edit().putInt(KEY_BLUR_RADIUS, clamped).apply()
      notifyChanged()
    }

  var usingCustomLight: Boolean
    get() = prefs.getBoolean(KEY_USING_CUSTOM_LIGHT, false)
    set(value) = prefs.edit().putBoolean(KEY_USING_CUSTOM_LIGHT, value).apply()

  var usingCustomDark: Boolean
    get() = prefs.getBoolean(KEY_USING_CUSTOM_DARK, false)
    set(value) = prefs.edit().putBoolean(KEY_USING_CUSTOM_DARK, value).apply()

  fun getLightWallpaperFile(): File {
    val file = File(context.filesDir, LIGHT_WALLPAPER_FILENAME)
    if (!file.exists()) {
      WallpaperGenerator.generateDefaultWallpaper(context, false)
    }
    return file
  }

  fun getDarkWallpaperFile(): File {
    val file = File(context.filesDir, DARK_WALLPAPER_FILENAME)
    if (!file.exists()) {
      WallpaperGenerator.generateDefaultWallpaper(context, true)
    }
    return file
  }

  fun saveCustomWallpaper(isDark: Boolean, uri: Uri): Boolean {
    val fileName = if (isDark) DARK_WALLPAPER_FILENAME else LIGHT_WALLPAPER_FILENAME
    val destFile = File(context.filesDir, fileName)
    return try {
      context.contentResolver.openInputStream(uri)?.use { input ->
        destFile.outputStream().use { output ->
          input.copyTo(output)
        }
      }
      if (isDark) usingCustomDark = true else usingCustomLight = true
      notifyChanged()
      true
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }

  fun resetWallpaper(isDark: Boolean): Boolean {
    val fileName = if (isDark) DARK_WALLPAPER_FILENAME else LIGHT_WALLPAPER_FILENAME
    val file = File(context.filesDir, fileName)
    if (file.exists()) {
      file.delete()
    }
    if (isDark) usingCustomDark = false else usingCustomLight = false
    WallpaperGenerator.generateDefaultWallpaper(context, isDark)
    notifyChanged()
    return true
  }

  fun notifyChanged() {
    val intent = Intent(ACTION_SETTINGS_CHANGED).apply {
      setPackage(context.packageName)
    }
    context.sendBroadcast(intent)
  }
}

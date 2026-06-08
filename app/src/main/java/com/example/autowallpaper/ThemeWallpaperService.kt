package com.example.autowallpaper

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.app.WallpaperColors
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.view.animation.LinearInterpolator
import com.example.autowallpaper.data.SettingsManager
import com.example.autowallpaper.utils.BlurUtils

class ThemeWallpaperService : WallpaperService() {

  private val activeEngines = java.util.Collections.synchronizedSet(mutableSetOf<ThemeEngine>())

  override fun onCreateEngine(): Engine {
    return ThemeEngine()
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    synchronized(activeEngines) {
      activeEngines.forEach { it.onConfigChanged() }
    }
  }

  inner class ThemeEngine : Engine() {
    private val handler = Handler(Looper.getMainLooper())
    private val settingsManager by lazy { SettingsManager(applicationContext) }
    private val keyguardManager by lazy { getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }

    private var currentBitmap: Bitmap? = null
    private var blurredBitmap: Bitmap? = null
    private var currentFilePath: String? = null
    private var currentBlurRadius = -1

    private var isLocked = true
    private var isReceiverRegistered = false

    private var blurTransitionValue = 0f // 0f = sharp, 1f = blurred
    private var transitionAnimator: ValueAnimator? = null

    private val paint = Paint().apply {
      isFilterBitmap = true // Bilinear filtering for smooth upscale
    }

    private val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
          Intent.ACTION_USER_PRESENT -> {
            updateLockStateWithAnimation()
          }
          Intent.ACTION_SCREEN_OFF -> {
            updateLockStateWithAnimation()
          }
          Intent.ACTION_SCREEN_ON -> {
            updateLockStateWithAnimation()
          }
          SettingsManager.ACTION_SETTINGS_CHANGED -> {
            loadWallpaperAndBlur(forceReload = true)
            triggerRedraw()
          }
        }
      }
    }

    override fun onCreate(surfaceHolder: SurfaceHolder?) {
      super.onCreate(surfaceHolder)
      activeEngines.add(this)
      updateLockState()
      blurTransitionValue = if (isLocked) 0f else 1f
      loadWallpaperAndBlur(forceReload = true)

      // Register receiver
      val filter = IntentFilter().apply {
        addAction(Intent.ACTION_USER_PRESENT)
        addAction(Intent.ACTION_SCREEN_OFF)
        addAction(Intent.ACTION_SCREEN_ON)
        addAction(SettingsManager.ACTION_SETTINGS_CHANGED)
      }
      
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
      } else {
        registerReceiver(receiver, filter)
      }
      isReceiverRegistered = true
    }

    override fun onDestroy() {
      super.onDestroy()
      activeEngines.remove(this)
      if (isReceiverRegistered) {
        unregisterReceiver(receiver)
        isReceiverRegistered = false
      }
      transitionAnimator?.cancel()
      recycleBitmaps()
    }

    override fun onVisibilityChanged(visible: Boolean) {
      if (visible) {
        updateLockStateWithAnimation()
        loadWallpaperAndBlur(forceReload = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
          notifyColorsChanged()
        }
        triggerRedraw()
      }
    }

    override fun onComputeColors(): WallpaperColors? {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
      
      return try {
        currentBitmap?.let { bitmap ->
          if (!bitmap.isRecycled) {
            // Extraction from a smaller version is faster and often more accurate for theme palettes
            val width = bitmap.width
            val height = bitmap.height
            val scale = 0.1f
            val smallBitmap = Bitmap.createScaledBitmap(
              bitmap,
              (width * scale).toInt().coerceAtLeast(1),
              (height * scale).toInt().coerceAtLeast(1),
              true
            )
            val colors = WallpaperColors.fromBitmap(smallBitmap)
            smallBitmap.recycle()
            colors
          } else null
        }
      } catch (e: Exception) {
        null
      }
    }

    override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
      super.onSurfaceChanged(holder, format, width, height)
      triggerRedraw()
    }

    override fun onOffsetsChanged(
      xOffset: Float,
      yOffset: Float,
      xOffsetStep: Float,
      yOffsetStep: Float,
      xPixelOffset: Int,
      yPixelOffset: Int
    ) {
      super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
      updateLockStateWithAnimation()
    }

    fun onConfigChanged() {
      // Regenerate default wallpapers if using them (to pick up system color changes)
      if (!settingsManager.usingCustomLight) {
        com.example.autowallpaper.utils.WallpaperGenerator.generateDefaultWallpaper(applicationContext, false)
      }
      if (!settingsManager.usingCustomDark) {
        com.example.autowallpaper.utils.WallpaperGenerator.generateDefaultWallpaper(applicationContext, true)
      }
      loadWallpaperAndBlur(forceReload = true)
      triggerRedraw()
    }

    private fun updateLockState() {
      isLocked = keyguardManager.isKeyguardLocked
    }

    private fun updateLockStateWithAnimation() {
      val wasLocked = isLocked
      updateLockState()
      
      // Force sharp version in system preview
      val targetBlurValue = if (isLocked || !settingsManager.blurEnabled || isPreview) 0f else 1f
      
      if (blurTransitionValue != targetBlurValue) {
        transitionAnimator?.cancel()
        transitionAnimator = ValueAnimator.ofFloat(blurTransitionValue, targetBlurValue).apply {
          duration = 400 // Smooth 400ms transition
          interpolator = LinearInterpolator()
          addUpdateListener { animator ->
            blurTransitionValue = animator.animatedValue as Float
            triggerRedraw()
          }
          start()
        }
      } else if (wasLocked != isLocked) {
        triggerRedraw()
      }
    }

    private fun triggerRedraw() {
      handler.post { draw() }
    }

    private fun recycleBitmaps() {
      currentBitmap?.recycle()
      currentBitmap = null
      blurredBitmap?.recycle()
      blurredBitmap = null
      currentFilePath = null
      currentBlurRadius = -1
    }

    private fun loadWallpaperAndBlur(forceReload: Boolean) {
      val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
      val wallpaperFile = if (isDarkTheme) {
        settingsManager.getDarkWallpaperFile()
      } else {
        settingsManager.getLightWallpaperFile()
      }

      val filePath = wallpaperFile.absolutePath
      val blurRadius = settingsManager.blurRadius
      val blurEnabled = settingsManager.blurEnabled

      val fileChanged = filePath != currentFilePath
      val radiusChanged = blurRadius != currentBlurRadius

      if (forceReload || fileChanged) {
        recycleBitmaps()
        try {
          if (wallpaperFile.exists()) {
            val options = BitmapFactory.Options().apply {
              inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            currentBitmap = BitmapFactory.decodeFile(filePath, options)
            currentFilePath = filePath
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
              notifyColorsChanged()
            }
          }
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }

      // If blur is enabled and blurred bitmap is missing or parameters changed, recreate it
      if (blurEnabled && currentBitmap != null && (blurredBitmap == null || fileChanged || radiusChanged)) {
        blurredBitmap?.recycle()
        try {
          // Optimization: Downscale the bitmap before blurring to make stack blur extremely fast
          val scale = 0.15f
          val width = (currentBitmap!!.width * scale).toInt().coerceAtLeast(1)
          val height = (currentBitmap!!.height * scale).toInt().coerceAtLeast(1)
          val scaledBitmap = Bitmap.createScaledBitmap(currentBitmap!!, width, height, true)
          blurredBitmap = BlurUtils.blur(scaledBitmap, blurRadius)
          scaledBitmap.recycle()
          currentBlurRadius = blurRadius
        } catch (e: Exception) {
          e.printStackTrace()
        }
      } else if (!blurEnabled) {
        blurredBitmap?.recycle()
        blurredBitmap = null
        currentBlurRadius = -1
      }
    }

    private fun draw() {
      val holder = surfaceHolder ?: return
      var canvas: Canvas? = null
      try {
        canvas = holder.lockCanvas()
        if (canvas != null) {
          if (currentBitmap != null && !currentBitmap!!.isRecycled) {
            val srcSharp = Rect(0, 0, currentBitmap!!.width, currentBitmap!!.height)
            
            // Calculate scale based on the original sharp bitmap
            val scaleX = canvas.width.toFloat() / currentBitmap!!.width
            val scaleY = canvas.height.toFloat() / currentBitmap!!.height
            val scale = Math.max(scaleX, scaleY)
            
            val newWidth = currentBitmap!!.width * scale
            val newHeight = currentBitmap!!.height * scale
            val left = (canvas.width - newWidth) / 2
            val top = (canvas.height - newHeight) / 2
            
            val destRect = Rect(
              left.toInt(), 
              top.toInt(), 
              (left + newWidth).toInt(), 
              (top + newHeight).toInt()
            )

            // Draw based on transition
            if (blurTransitionValue <= 0f) {
              // Only draw sharp
              paint.alpha = 255
              canvas.drawBitmap(currentBitmap!!, srcSharp, destRect, paint)
            } else if (blurTransitionValue >= 1f && blurredBitmap != null && !blurredBitmap!!.isRecycled) {
              // Only draw blurred
              val srcBlurred = Rect(0, 0, blurredBitmap!!.width, blurredBitmap!!.height)
              paint.alpha = 255
              canvas.drawBitmap(blurredBitmap!!, srcBlurred, destRect, paint)
            } else if (blurredBitmap != null && !blurredBitmap!!.isRecycled) {
              // Crossfade transition
              val srcBlurred = Rect(0, 0, blurredBitmap!!.width, blurredBitmap!!.height)
              
              paint.alpha = 255
              canvas.drawBitmap(currentBitmap!!, srcSharp, destRect, paint)
              
              paint.alpha = (blurTransitionValue * 255).toInt()
              canvas.drawBitmap(blurredBitmap!!, srcBlurred, destRect, paint)
            } else {
              // Fallback to sharp
              paint.alpha = 255
              canvas.drawBitmap(currentBitmap!!, srcSharp, destRect, paint)
            }
          } else {
            val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            canvas.drawColor(if (isDarkTheme) 0xFF1A1C1E.toInt() else 0xFFFCFCFF.toInt())
          }
        }
      } catch (e: Exception) {
        e.printStackTrace()
      } finally {
        if (canvas != null) {
          try {
            holder.unlockCanvasAndPost(canvas)
          } catch (e: Exception) {
            e.printStackTrace()
          }
        }
      }
    }
  }
}

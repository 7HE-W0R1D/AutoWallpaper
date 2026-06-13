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
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import com.example.autowallpaper.data.SettingsManager
import com.example.autowallpaper.utils.BlurUtils
import java.util.concurrent.Executors

class ThemeWallpaperService : WallpaperService() {

  private val activeEngines = java.util.Collections.synchronizedSet(mutableSetOf<ThemeEngine>())
  private val workerExecutor = Executors.newSingleThreadExecutor()

  override fun onCreateEngine(): Engine {
    return ThemeEngine()
  }

  override fun onDestroy() {
    super.onDestroy()
    workerExecutor.shutdown()
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
    private var isVisible = false

    private var blurTransitionValue = 0f // 0f = sharp, 1f = blurred
    private var transitionAnimator: ValueAnimator? = null

    private var isRedrawPending = false

    // Cached rectangles for drawing
    private val destRect = Rect()
    private val srcSharp = Rect()
    private val srcBlurred = Rect()

    private val paint = Paint().apply {
      isFilterBitmap = true
      isAntiAlias = false
    }

    private val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
          Intent.ACTION_USER_PRESENT -> updateLockStateWithAnimation()
          Intent.ACTION_SCREEN_OFF -> updateLockStateWithAnimation()
          Intent.ACTION_SCREEN_ON -> updateLockStateWithAnimation()
          SettingsManager.ACTION_SETTINGS_CHANGED -> loadWallpaperAsync(forceReload = true)
        }
      }
    }

    override fun onCreate(surfaceHolder: SurfaceHolder?) {
      super.onCreate(surfaceHolder)
      activeEngines.add(this)
      updateLockState()
      blurTransitionValue = if (isLocked) 0f else 1f
      loadWallpaperAsync(forceReload = true)

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
      handler.removeCallbacksAndMessages(null)
      recycleBitmaps()
    }

    override fun onVisibilityChanged(visible: Boolean) {
      isVisible = visible
      if (visible) {
        updateLockStateWithAnimation()
        loadWallpaperAsync(forceReload = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
          notifyColorsChanged()
        }
        triggerRedraw()
      } else {
        transitionAnimator?.cancel()
      }
    }

    override fun onComputeColors(): WallpaperColors? {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
      val bitmap = currentBitmap ?: return null
      if (bitmap.isRecycled) return null
      return try {
        // Fast color extraction using small scale
        val small = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
        val colors = WallpaperColors.fromBitmap(small)
        small.recycle()
        colors
      } catch (e: Exception) { null }
    }

    override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
      super.onSurfaceChanged(holder, format, width, height)
      updateDrawingRects()
      triggerRedraw()
    }

    override fun onSurfaceRedrawNeeded(holder: SurfaceHolder?) {
      super.onSurfaceRedrawNeeded(holder)
      triggerRedraw()
    }

    override fun onSurfaceCreated(holder: SurfaceHolder?) {
      super.onSurfaceCreated(holder)
      updateDrawingRects()
      triggerRedraw()
    }

    override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int) {
      super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
      // Only check lock state if visible
      if (isVisible) {
        val wasLocked = isLocked
        updateLockState()
        if (wasLocked != isLocked) {
          updateLockStateWithAnimation()
        }
      }
    }

    fun onConfigChanged() {
      workerExecutor.execute {
        if (!settingsManager.usingCustomLight) {
          com.example.autowallpaper.utils.WallpaperGenerator.generateDefaultWallpaper(applicationContext, false)
        }
        if (!settingsManager.usingCustomDark) {
          com.example.autowallpaper.utils.WallpaperGenerator.generateDefaultWallpaper(applicationContext, true)
        }
        handler.post {
          loadWallpaperAsync(forceReload = true)
        }
      }
    }

    private fun updateLockState() {
      isLocked = keyguardManager.isKeyguardLocked
    }

    private fun updateLockStateWithAnimation() {
      updateLockState()
      
      // Force sharp version in system preview
      val targetBlurValue = if (isLocked || !settingsManager.blurEnabled || isPreview) 0f else 1f
      
      if (blurTransitionValue != targetBlurValue) {
        transitionAnimator?.cancel()
        transitionAnimator = ValueAnimator.ofFloat(blurTransitionValue, targetBlurValue).apply {
          duration = 300 // Snappier 300ms transition
          interpolator = LinearInterpolator()
          addUpdateListener { animator ->
            blurTransitionValue = animator.animatedValue as Float
            triggerRedraw()
          }
          start()
        }
      }
    }

    private fun triggerRedraw() {
      if (!isVisible || isRedrawPending) return
      isRedrawPending = true
      handler.post {
        isRedrawPending = false
        draw()
      }
    }

    private fun recycleBitmaps() {
      currentBitmap?.recycle()
      currentBitmap = null
      blurredBitmap?.recycle()
      blurredBitmap = null
      currentFilePath = null
      currentBlurRadius = -1
    }

    private fun loadWallpaperAsync(forceReload: Boolean) {
      val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
      val wallpaperFile = if (isDarkTheme) settingsManager.getDarkWallpaperFile() else settingsManager.getLightWallpaperFile()
      val filePath = wallpaperFile.absolutePath
      val blurRadius = settingsManager.blurRadius
      val blurEnabled = settingsManager.blurEnabled

      if (!forceReload && filePath == currentFilePath && blurRadius == currentBlurRadius) return

      workerExecutor.execute {
        try {
          var newBitmap: Bitmap? = null
          if (forceReload || filePath != currentFilePath) {
            if (wallpaperFile.exists()) {
              val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = 1
              }
              newBitmap = BitmapFactory.decodeFile(filePath, options)
            }
          }

          var newBlurred: Bitmap? = null
          if (blurEnabled && (newBitmap != null || (currentBitmap != null && blurRadius != currentBlurRadius))) {
            val source = newBitmap ?: currentBitmap
            if (source != null) {
              val scale = 0.1f // Smaller scale for even faster blur
              val w = (source.width * scale).toInt().coerceAtLeast(1)
              val h = (source.height * scale).toInt().coerceAtLeast(1)
              val scaled = Bitmap.createScaledBitmap(source, w, h, true)
              newBlurred = BlurUtils.blur(scaled, blurRadius)
              scaled.recycle()
            }
          }

          handler.post {
            var changed = false
            if (newBitmap != null) {
              currentBitmap?.recycle()
              currentBitmap = newBitmap
              currentFilePath = filePath
              changed = true
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) notifyColorsChanged()
            }
            
            if (blurEnabled) {
              if (newBlurred != null || (blurRadius != currentBlurRadius)) {
                blurredBitmap?.recycle()
                blurredBitmap = newBlurred
                currentBlurRadius = blurRadius
                changed = true
              }
            } else if (blurredBitmap != null) {
              blurredBitmap?.recycle()
              blurredBitmap = null
              currentBlurRadius = -1
              changed = true
            }

            if (changed) {
              updateDrawingRects()
              triggerRedraw()
            }
          }
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }
    }

    private fun updateDrawingRects() {
      val holder = surfaceHolder ?: return
      val canvasWidth = holder.surfaceFrame.width()
      val canvasHeight = holder.surfaceFrame.height()
      if (canvasWidth <= 0 || canvasHeight <= 0) return

      currentBitmap?.let { bmp ->
        if (!bmp.isRecycled) {
          srcSharp.set(0, 0, bmp.width, bmp.height)
          val scale = Math.max(canvasWidth.toFloat() / bmp.width, canvasHeight.toFloat() / bmp.height)
          val w = (bmp.width * scale).toInt()
          val h = (bmp.height * scale).toInt()
          val left = (canvasWidth - w) / 2
          val top = (canvasHeight - h) / 2
          destRect.set(left, top, left + w, top + h)
        }
      }

      blurredBitmap?.let { bmp ->
        if (!bmp.isRecycled) {
          srcBlurred.set(0, 0, bmp.width, bmp.height)
        }
      }
    }

    private fun draw() {
      val holder = surfaceHolder ?: return
      var canvas: Canvas? = null
      try {
        canvas = holder.lockCanvas()
        if (canvas != null) {
          val bmp = currentBitmap
          if (bmp != null && !bmp.isRecycled) {
            if (blurTransitionValue <= 0f) {
              paint.alpha = 255
              canvas.drawBitmap(bmp, srcSharp, destRect, paint)
            } else {
              val blurred = blurredBitmap
              if (blurred != null && !blurred.isRecycled) {
                // Crossfade
                paint.alpha = 255
                canvas.drawBitmap(bmp, srcSharp, destRect, paint)
                paint.alpha = (blurTransitionValue * 255).toInt()
                canvas.drawBitmap(blurred, srcBlurred, destRect, paint)
              } else {
                paint.alpha = 255
                canvas.drawBitmap(bmp, srcSharp, destRect, paint)
              }
            }
          } else {
            drawFallback(canvas)
          }
        }
      } catch (e: Exception) {
        e.printStackTrace()
      } finally {
        if (canvas != null) {
          try {
            holder.unlockCanvasAndPost(canvas)
          } catch (e: Exception) { }
        }
      }
    }

    private fun drawFallback(canvas: Canvas) {
      val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
      val colorSurface = if (isDarkTheme) 0xFF0D0614 else 0xFFFFF8F6 
      val colorPrimary = if (isDarkTheme) 0xFFE040FB else 0xFF8E24AA
      val colorSecondary = if (isDarkTheme) 0xFFFF8A65 else 0xFFD84315
      val colorTertiary = if (isDarkTheme) 0xFF26C6DA else 0xFF00838F

      canvas.drawColor(colorSurface.toInt())
      val blobPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
      }
      val w = canvas.width.toFloat()
      val h = canvas.height.toFloat()
      blobPaint.color = colorPrimary.toInt()
      blobPaint.alpha = 150
      canvas.drawCircle(w * 0.2f, h * 0.3f, w * 0.8f, blobPaint)
      blobPaint.color = colorSecondary.toInt()
      blobPaint.alpha = 130
      canvas.drawCircle(w * 0.8f, h * 0.6f, w * 0.9f, blobPaint)
      blobPaint.color = colorTertiary.toInt()
      blobPaint.alpha = 110
      canvas.drawCircle(w * 0.4f, h * 0.9f, w * 0.7f, blobPaint)
    }
  }
}

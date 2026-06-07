package com.example.autowallpaper.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import java.io.File
import java.io.FileOutputStream

object WallpaperGenerator {

    fun generateDefaultWallpaper(context: Context, isDark: Boolean): File {
        val fileName = if (isDark) "dark_wallpaper.png" else "light_wallpaper.png"
        val file = File(context.filesDir, fileName)
        
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1080)
        val height = metrics.heightPixels.coerceAtLeast(1920)
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Fallback colors matching Material 3 palettes
        val colorSurface = if (isDark) 0xFF1A1C1E else 0xFFFCFCFF
        val colorPrimary = if (isDark) 0xFF384948 else 0xFFCCE8E7
        val colorSecondary = if (isDark) 0xFF3F4948 else 0xFFDCE5E3
        val colorTertiary = if (isDark) 0xFF404652 else 0xFFDEE2F2

        canvas.drawColor(colorSurface.toInt())

        val blobPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val w = width.toFloat()
        val h = height.toFloat()

        blobPaint.color = colorPrimary.toInt()
        blobPaint.alpha = 150
        canvas.drawCircle(w * 0.2f, h * 0.3f, w * 0.8f, blobPaint)

        blobPaint.color = colorSecondary.toInt()
        blobPaint.alpha = 130
        canvas.drawCircle(w * 0.8f, h * 0.6f, w * 0.9f, blobPaint)

        blobPaint.color = colorTertiary.toInt()
        blobPaint.alpha = 110
        canvas.drawCircle(w * 0.4f, h * 0.9f, w * 0.7f, blobPaint)

        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            bitmap.recycle()
        }
        
        return file
    }
}

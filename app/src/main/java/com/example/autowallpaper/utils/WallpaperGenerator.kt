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
        
        // Use more distinct colors for light/dark to trigger visible system theme changes
        val colorSurface = if (isDark) 0xFF0D0614 else 0xFFFFF8F6 
        val colorPrimary = if (isDark) 0xFFE040FB else 0xFF8E24AA
        val colorSecondary = if (isDark) 0xFFFF8A65 else 0xFFD84315
        val colorTertiary = if (isDark) 0xFF26C6DA else 0xFF00838F

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

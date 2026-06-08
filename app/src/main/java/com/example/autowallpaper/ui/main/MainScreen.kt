@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.autowallpaper.ui.main

import android.content.Intent
import android.content.pm.PackageManager
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.autowallpaper.ThemeWallpaperService
import com.example.autowallpaper.data.DefaultDataRepository
import com.example.autowallpaper.data.SettingsManager
import com.example.autowallpaper.theme.AutoWallpaperTheme
import com.example.autowallpaper.utils.BlurUtils
import java.io.File

@Composable
fun HomeScreen(
  state: MainUiState,
  viewModel: MainScreenViewModel
) {
  // Make sure we trigger refresh when activity resumes (eg. returning from live wallpaper chooser)
  DisposableEffect(Unit) {
    viewModel.refreshState()
    onDispose {}
  }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(bottom = 120.dp), // spacing for floating bottom bar
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(24.dp))
      
      // Status indicator card
      ServiceStatusCard(isActive = state.isServiceActive)

      Spacer(modifier = Modifier.height(24.dp))

      // Large Wallpaper Preview & Tabs
      WallpaperPreviewSection(state = state)

      // Spacer at the bottom to allow scrolling past the floating bar
      Spacer(modifier = Modifier.height(140.dp))
    }
  }
}

@Composable
fun SettingsScreen(
  state: MainUiState,
  viewModel: MainScreenViewModel
) {
  val context = LocalContext.current
  var lastCroppedIsDark by remember { mutableStateOf(false) }
  val cropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    viewModel.setUsingCustom(lastCroppedIsDark, true)
    viewModel.refreshState()
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 20.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = "Wallpaper Settings",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 24.dp),
      color = MaterialTheme.colorScheme.onBackground
    )

    Spacer(modifier = Modifier.height(24.dp))

    // Wallpaper Picker Card
    WallpaperPickers(
      state = state,
      onWallpaperSelected = { isDark, uri ->
        // 1. Save to a temporary file first to handle permissions robustly
        val tempFile = File(context.cacheDir, "crop_source.png")
        try {
          context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
              input.copyTo(output)
            }
          }
        } catch (e: Exception) {
          e.printStackTrace()
        }

        val sourceUri = FileProvider.getUriForFile(
          context,
          "${context.packageName}.fileprovider",
          tempFile
        )

        val outputFile = viewModel.getWallpaperFile(isDark)
        if (!outputFile.exists()) {
          outputFile.parentFile?.mkdirs()
          outputFile.createNewFile()
        }
        
        val outputUri = FileProvider.getUriForFile(
          context,
          "${context.packageName}.fileprovider",
          outputFile
        )
        
        val intent = Intent("com.android.camera.action.CROP").apply {
          setDataAndType(sourceUri, "image/*")
          putExtra("crop", "true")
          putExtra("aspectX", 9)
          putExtra("aspectY", 19)
          putExtra("scale", true)
          putExtra("return-data", false)
          putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
          putExtra("outputFormat", Bitmap.CompressFormat.PNG.toString())
          putExtra("noFaceDetection", true)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        
        // Grant permissions to all potential handlers
        val resInfoList = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        for (resolveInfo in resInfoList) {
          val packageName = resolveInfo.activityInfo.packageName
          context.grantUriPermission(packageName, sourceUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
          context.grantUriPermission(packageName, outputUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
          if (resInfoList.isNotEmpty()) {
            val title = if (isDark) "Crop Dark Wallpaper" else "Crop Light Wallpaper"
            lastCroppedIsDark = isDark
            cropLauncher.launch(Intent.createChooser(intent, title))
          } else {
            // No system cropper found, fallback to direct save
            viewModel.saveCustomWallpaper(isDark, uri)
          }
        } catch (e: Exception) {
          viewModel.saveCustomWallpaper(isDark, uri)
        }
      },
      onReset = { isDark -> viewModel.resetWallpaper(isDark) }
    )

    Spacer(modifier = Modifier.height(24.dp))

    // Blur Control Card
    BlurSettingsCard(
      state = state,
      onBlurToggled = { viewModel.setBlurEnabled(it) },
      onRadiusChanged = { viewModel.setBlurRadius(it) }
    )

    Spacer(modifier = Modifier.height(24.dp))

    // About Section
    AboutSection()

    // Spacer at the bottom to allow scrolling past the floating bar
    Spacer(modifier = Modifier.height(140.dp))
  }
}

@Composable
fun AboutSection() {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      Text(
        text = "About",
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground
      )
      
      Spacer(modifier = Modifier.height(8.dp))
      
      Text(
        text = "AutoWallpaper automatically switches between light and dark wallpapers based on your system theme. It also supports blurring your home screen wallpaper for a cleaner look.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
      )
      
      Spacer(modifier = Modifier.height(16.dp))
      
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        val uriHandler = LocalUriHandler.current
        Text(
          text = "Version",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "1.0.4",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.clickable {
            uriHandler.openUri("https://github.com/7HE-W0R1D/AutoWallpaper/releases/tag/v1.0.4")
          }
        )
      }
    }
  }
}



@Composable
fun ServiceStatusCard(isActive: Boolean) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = if (isActive) {
      MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
    } else {
      MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    },
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Warning,
        contentDescription = null,
        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.size(32.dp)
      )
      Spacer(modifier = Modifier.width(16.dp))
      Column {
        Text(
          text = if (isActive) "Live Wallpaper is Active" else "Live Wallpaper Inactive",
          fontWeight = FontWeight.Bold,
          style = MaterialTheme.typography.bodyLarge,
          color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = if (isActive) "Automatically switching based on system theme" else "Set this app as active wallpaper to enable switching",
          style = MaterialTheme.typography.bodySmall,
          color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
      }
    }
  }
}

@Composable
fun WallpaperPreviewSection(state: MainUiState) {
  var selectedTab by remember { mutableStateOf(0) } // 0 = Lock Screen, 1 = Home Screen
  var previewThemeIsDark by remember { mutableStateOf(false) } // let user toggle light/dark preview
  
  // Track system theme so preview defaults to matches it
  val systemIsDark = isSystemInDarkTheme()
  LaunchedEffect(systemIsDark) {
    previewThemeIsDark = systemIsDark
  }

  val activeFile = if (previewThemeIsDark) state.darkWallpaperFile else state.lightWallpaperFile

  // Load and scale preview bitmap
  val previewBitmap = remember(activeFile, state.refreshTrigger) {
    activeFile?.absolutePath?.let { path ->
      try {
        if (File(path).exists()) {
          val options = BitmapFactory.Options().apply {
            inSampleSize = 2 // downsample for UI preview performance
          }
          BitmapFactory.decodeFile(path, options)
        } else null
      } catch (e: Exception) {
        null
      }
    }
  }

  // Create blurred bitmap for Home Screen preview if enabled
  val blurredPreviewBitmap = remember(previewBitmap, state.blurEnabled, state.blurRadius) {
    if (state.blurEnabled && previewBitmap != null) {
      try {
        // Downsample heavily for fast preview blur
        val scale = 0.25f
        val w = (previewBitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (previewBitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(previewBitmap, w, h, true)
        val blurred = BlurUtils.blur(scaled, state.blurRadius)
        scaled.recycle()
        blurred
      } catch (e: Exception) {
        null
      }
    } else {
      null
    }
  }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      
      // Tab control (Pill styled button row) - More native feel
      Row(
        modifier = Modifier
          .padding(horizontal = 16.dp)
          .fillMaxWidth()
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
          .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
      ) {
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(CircleShape)
            .background(if (selectedTab == 0) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { selectedTab = 0 }
            .padding(vertical = 10.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            "Lock screen",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
            color = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(CircleShape)
            .background(if (selectedTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { selectedTab = 1 }
            .padding(vertical = 10.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            "Home screen",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
            color = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Preview Section (Clean, modern look)
      Box(
        modifier = Modifier
          .width(220.dp)
          .aspectRatio(9f / 19.5f)
          .shadow(16.dp, RoundedCornerShape(44.dp))
          .clip(RoundedCornerShape(44.dp))
          .background(Color.Black),
        contentAlignment = Alignment.Center
      ) {
        val bitmapToDraw = if (selectedTab == 1 && state.blurEnabled) blurredPreviewBitmap else previewBitmap

        if (bitmapToDraw != null) {
          Image(
            bitmap = bitmapToDraw.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        } else {
          MaterialBlobBackground(isDark = previewThemeIsDark)
        }

        // Mock Home Screen overlay (Clean app dock)
        if (selectedTab == 1) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(bottom = 36.dp),
            contentAlignment = Alignment.BottomCenter
          ) {
            Row(
              horizontalArrangement = Arrangement.SpaceEvenly,
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
            ) {
              repeat(4) {
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.45f))
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Toggle to preview light vs dark wallpapers manually
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
          .clip(CircleShape)
          .clickable { previewThemeIsDark = !previewThemeIsDark }
          .padding(8.dp)
      ) {
        val lightSelected = !previewThemeIsDark
        
        Surface(
          shape = CircleShape,
          color = if (lightSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
          modifier = Modifier.clickable { previewThemeIsDark = false }
        ) {
          Text(
            "Light",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (lightSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        
        Spacer(modifier = Modifier.width(8.dp))

        Surface(
          shape = CircleShape,
          color = if (previewThemeIsDark) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
          modifier = Modifier.clickable { previewThemeIsDark = true }
        ) {
          Text(
            "Dark",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (previewThemeIsDark) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
}


@Composable
fun MaterialBlobBackground(isDark: Boolean = isSystemInDarkTheme()) {
  AutoWallpaperTheme(darkTheme = isDark) {
    val colorScheme = MaterialTheme.colorScheme
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
      drawRect(colorScheme.surface)
      
      // Draw organic blobs using primary, secondary and tertiary colors
      drawCircle(
        color = colorScheme.primaryContainer.copy(alpha = 0.6f),
        radius = size.width * 0.7f,
        center = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.3f)
      )
      
      drawCircle(
        color = colorScheme.secondaryContainer.copy(alpha = 0.5f),
        radius = size.width * 0.8f,
        center = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.6f)
      )
      
      drawCircle(
        color = colorScheme.tertiaryContainer.copy(alpha = 0.4f),
        radius = size.width * 0.6f,
        center = androidx.compose.ui.geometry.Offset(size.width * 0.4f, size.height * 0.9f)
      )
    }
  }
}

@Composable
fun WallpaperPickers(
  state: MainUiState,
  onWallpaperSelected: (Boolean, android.net.Uri) -> Unit,
  onReset: (Boolean) -> Unit
) {
  val pickLightWallpaperLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      onWallpaperSelected(false, uri)
    }
  }

  val pickDarkWallpaperLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      onWallpaperSelected(true, uri)
    }
  }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      Text(
        "Wallpapers",
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(bottom = 12.dp)
      )

      // Light wallpaper selection card
      WallpaperItemRow(
        title = "Light Wallpaper",
        file = state.lightWallpaperFile,
        refreshTrigger = state.refreshTrigger,
        isDark = false,
        usingCustom = state.usingCustomLight,
        onClick = {
          pickLightWallpaperLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
          )
        },
        onReset = { onReset(false) }
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Dark wallpaper selection card
      WallpaperItemRow(
        title = "Dark Wallpaper",
        file = state.darkWallpaperFile,
        refreshTrigger = state.refreshTrigger,
        isDark = true,
        usingCustom = state.usingCustomDark,
        onClick = {
          pickDarkWallpaperLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
          )
        },
        onReset = { onReset(true) }
      )
    }
  }
}

@Composable
fun WallpaperItemRow(
  title: String,
  file: File?,
  refreshTrigger: Int,
  isDark: Boolean,
  usingCustom: Boolean,
  onClick: () -> Unit,
  onReset: () -> Unit
) {
  val bitmap = remember(file, refreshTrigger) {
    file?.absolutePath?.let { path ->
      try {
        if (File(path).exists()) {
          val options = BitmapFactory.Options().apply {
            inSampleSize = 4 // downscale thumbnail
          }
          BitmapFactory.decodeFile(path, options)
        } else null
      } catch (e: Exception) {
        null
      }
    }
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
      .clickable { onClick() }
      .padding(8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(60.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center
    ) {
      if (bitmap != null) {
        Image(
          bitmap = bitmap.asImageBitmap(),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
      } else {
        MaterialBlobBackground(isDark = isDark)
      }
    }

    Spacer(modifier = Modifier.width(12.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodyMedium
      )
    }

    if (usingCustom) {
      TextButton(
        onClick = onReset,
        modifier = Modifier.padding(end = 4.dp)
      ) {
        Text("Reset", fontSize = 12.sp)
      }
    }

    Button(
      onClick = onClick,
      shape = RoundedCornerShape(8.dp),
      colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
      )
    ) {
      Text("Change", fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
  }
}

@Composable
fun BlurSettingsCard(
  state: MainUiState,
  onBlurToggled: (Boolean) -> Unit,
  onRadiusChanged: (Int) -> Unit
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            "Blur When Unlocked",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge
          )
          Text(
            "Blur the wallpaper when the device is unlocked",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
          )
        }
        Switch(
          checked = state.blurEnabled,
          onCheckedChange = onBlurToggled
        )
      }

      AnimatedVisibility(
        visible = state.blurEnabled,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              "Blur Intensity",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          Slider(
            value = state.blurRadius.toFloat(),
            onValueChange = { onRadiusChanged(it.toInt()) },
            valueRange = 2f..18f,
            steps = 3,
            modifier = Modifier.fillMaxWidth()
          )

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              "less blur",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
              "more blur",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
          }

          Spacer(modifier = Modifier.height(24.dp))
          
          BlurComparisonPreview(state = state)
        }
      }
    }
  }
}

@Composable
fun BlurComparisonPreview(state: MainUiState) {
  val isDark = isSystemInDarkTheme()
  val activeFile = if (isDark) state.darkWallpaperFile else state.lightWallpaperFile
  
  val bitmap = remember(activeFile, state.refreshTrigger) {
    activeFile?.absolutePath?.let { path ->
      try {
        if (File(path).exists()) {
          val options = BitmapFactory.Options().apply { inSampleSize = 4 }
          BitmapFactory.decodeFile(path, options)
        } else null
      } catch (e: Exception) { null }
    }
  }

  val blurredBitmap = remember(bitmap, state.blurRadius) {
    if (bitmap != null) {
      try {
        val scale = 0.5f
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val blurred = BlurUtils.blur(scaled, state.blurRadius)
        scaled.recycle()
        blurred
      } catch (e: Exception) { null }
    } else null
  }

  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      "Preview",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
      modifier = Modifier.padding(bottom = 8.dp)
    )
    
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(120.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
      if (bitmap != null && blurredBitmap != null) {
        Row(modifier = Modifier.fillMaxSize()) {
          // Sharp half
          Box(modifier = Modifier.weight(1f)) {
            Image(
              bitmap = bitmap.asImageBitmap(),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          }
          
          // Vertical divider line
          Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.5f)))

          // Blurred half
          Box(modifier = Modifier.weight(1f)) {
            Image(
              bitmap = blurredBitmap.asImageBitmap(),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          }
        }
      } else {
        MaterialBlobBackground(isDark = isDark)
      }
    }
  }
}


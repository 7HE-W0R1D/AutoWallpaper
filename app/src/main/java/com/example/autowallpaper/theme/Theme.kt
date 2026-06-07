package com.example.autowallpaper.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// 1. Expressive Color Schemes
private val LightExpressiveColorScheme = lightColorScheme(
  primary = LightExpressivePrimary,
  onPrimary = LightExpressiveOnPrimary,
  primaryContainer = LightExpressivePrimaryContainer,
  onPrimaryContainer = LightExpressiveOnPrimaryContainer,
  secondary = LightExpressiveSecondary,
  onSecondary = LightExpressiveOnSecondary,
  secondaryContainer = LightExpressiveSecondaryContainer,
  onSecondaryContainer = LightExpressiveOnSecondaryContainer,
  tertiary = LightExpressiveTertiary,
  onTertiary = LightExpressiveOnTertiary,
  tertiaryContainer = LightExpressiveTertiaryContainer,
  onTertiaryContainer = LightExpressiveOnTertiaryContainer,
  background = LightExpressiveBackground,
  onBackground = LightExpressiveOnBackground,
  surface = LightExpressiveSurface,
  onSurface = LightExpressiveOnSurface
)

private val DarkExpressiveColorScheme = darkColorScheme(
  primary = DarkExpressivePrimary,
  onPrimary = DarkExpressiveOnPrimary,
  primaryContainer = DarkExpressivePrimaryContainer,
  onPrimaryContainer = DarkExpressiveOnPrimaryContainer,
  secondary = DarkExpressiveSecondary,
  onSecondary = DarkExpressiveOnSecondary,
  secondaryContainer = DarkExpressiveSecondaryContainer,
  onSecondaryContainer = DarkExpressiveOnSecondaryContainer,
  tertiary = DarkExpressiveTertiary,
  onTertiary = DarkExpressiveOnTertiary,
  tertiaryContainer = DarkExpressiveTertiaryContainer,
  onTertiaryContainer = DarkExpressiveOnTertiaryContainer,
  background = DarkExpressiveBackground,
  onBackground = DarkExpressiveOnBackground,
  surface = DarkExpressiveSurface,
  onSurface = DarkExpressiveOnSurface
)

@Composable
fun AutoWallpaperTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val colorScheme: ColorScheme = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    else -> {
      if (darkTheme) DarkExpressiveColorScheme else LightExpressiveColorScheme
    }
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}

package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = ElectricBlue,
    onPrimary = MidnightBlue,
    primaryContainer = SkyBlueContainer,
    onPrimaryContainer = ElectricBlue,
    secondary = Color(0xFF7DD3FC),
    onSecondary = MidnightBlue,
    secondaryContainer = SlateBorder,
    onSecondaryContainer = Color.White,
    tertiary = StatusEmerald,
    onTertiary = MidnightBlue,
    background = MidnightBlue,
    onBackground = Color(0xFFF1F5F9),
    surface = SlateNavy,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = SlateBorder,
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = StatusCrimson,
    onError = Color.White,
    outline = SlateBorder
  )

private val LightColorScheme =
  lightColorScheme(
    primary = ElectricBlueGlow,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = SlateNavy,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = SlateNavy,
    tertiary = StatusEmerald,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = MidnightBlue,
    surface = LightSurface,
    onSurface = MidnightBlue,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = SlateNavy,
    error = StatusCrimson,
    onError = Color.White,
    outline = LightBorder
  )

@Composable
fun VitualEmuTheme(
  darkTheme: Boolean = true, // Gaming dashboard defaults to immersive dark theme
  dynamicColor: Boolean = false, // Preserve brand identity tokens
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

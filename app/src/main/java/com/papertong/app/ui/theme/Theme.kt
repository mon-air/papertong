package com.papertong.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentSoft,
    onPrimaryContainer = Ink,
    secondary = InkMuted,
    onSecondary = Color.White,
    secondaryContainer = PaperDim,
    onSecondaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperDim,
    onSurfaceVariant = InkMuted,
    outline = OutlineWarm,
    outlineVariant = OutlineWarmSoft,
    error = DangerRed,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF0E2337),
    primaryContainer = AccentSoftDark,
    onPrimaryContainer = InkDark,
    secondary = InkMutedDark,
    onSecondary = Color(0xFF14131A),
    secondaryContainer = SurfaceDarkHigh,
    onSecondaryContainer = InkDark,
    background = PaperDark,
    onBackground = InkDark,
    surface = PaperDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = InkMutedDark,
    outline = OutlineDark,
    outlineVariant = SurfaceDarkHigh,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

/**
 * 不使用 Material You 动态取色：这个 App 的视觉主张是"纸张"，
 * 系统壁纸取色会破坏统一的纸感。
 */
@Composable
fun PaperTongTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PaperTypography,
        content = content
    )
}

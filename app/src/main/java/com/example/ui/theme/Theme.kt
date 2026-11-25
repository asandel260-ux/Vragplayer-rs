package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode(val key: String, val label: String) {
    DARK_STUDIO("dark_studio", "Obsidian"),
    OLED_BLACK("oled_black", "AMOLED"),
    LIGHT_STUDIO("light_studio", "Paper"),
    SYSTEM_DYNAMIC("system_dynamic", "System");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.find { it.key == key } ?: DARK_STUDIO
    }
}

private fun buildDarkScheme(accent: Color, oledBlack: Boolean): ColorScheme {
    val bg = if (oledBlack) OledBg else DarkBg
    val surface = if (oledBlack) OledSurface else DarkSurface
    val surfaceHigh = if (oledBlack) OledSurfaceHigh else DarkSurfaceHigh
    val surfaceHighest = if (oledBlack) OledSurfaceHighest else DarkSurfaceHighest
    val primaryContainer = lerp(surfaceHigh, accent, 0.22f)

    return darkColorScheme(
        primary = accent,
        onPrimary = accent.contrastingOnColor(),
        primaryContainer = primaryContainer,
        onPrimaryContainer = DarkTextPrimary,
        secondary = lerp(accent, Color.White, 0.32f),
        onSecondary = Color(0xFF111116),
        secondaryContainer = surfaceHigh,
        onSecondaryContainer = DarkTextPrimary,
        tertiary = AccentEmerald,
        onTertiary = Color(0xFF062012),
        background = bg,
        onBackground = DarkTextPrimary,
        surface = surface,
        onSurface = DarkTextPrimary,
        surfaceVariant = surfaceHigh,
        onSurfaceVariant = DarkTextSecondary,
        surfaceContainerLowest = bg,
        surfaceContainerLow = surface,
        surfaceContainer = surfaceHigh,
        surfaceContainerHigh = surfaceHighest,
        surfaceContainerHighest = lerp(surfaceHighest, Color.White, 0.07f),
        surfaceTint = Color.Transparent,
        outline = DarkOutline,
        outlineVariant = DarkOutlineSubtle,
        error = Color(0xFFEF4444),
        onError = Color.White
    )
}

private fun buildLightScheme(accent: Color): ColorScheme {
    val resolvedAccent = if (accent.relativeLuminanceAccurate() > 0.72f) Color(0xFF18181B) else accent
    val primaryContainer = lerp(LightSurface, resolvedAccent, 0.14f)

    return lightColorScheme(
        primary = resolvedAccent,
        onPrimary = resolvedAccent.contrastingOnColor(),
        primaryContainer = primaryContainer,
        onPrimaryContainer = LightTextPrimary,
        secondary = lerp(resolvedAccent, Color(0xFF18181B), 0.25f),
        onSecondary = Color.White,
        secondaryContainer = LightSurfaceHigh,
        onSecondaryContainer = LightTextPrimary,
        tertiary = Color(0xFF15803D),
        onTertiary = Color.White,
        background = LightBg,
        onBackground = LightTextPrimary,
        surface = LightSurface,
        onSurface = LightTextPrimary,
        surfaceVariant = LightSurfaceHigh,
        onSurfaceVariant = LightTextSecondary,
        surfaceContainerLowest = LightSurface,
        surfaceContainerLow = LightBg,
        surfaceContainer = LightSurfaceHigh,
        surfaceContainerHigh = LightSurfaceHighest,
        surfaceContainerHighest = lerp(LightSurfaceHighest, Color.Black, 0.05f),
        surfaceTint = Color.Transparent,
        outline = LightOutline,
        outlineVariant = LightOutlineSubtle,
        error = Color(0xFFDC2626),
        onError = Color.White
    )
}

@Composable
fun VragTheme(
    themeMode: ThemeMode = ThemeMode.DARK_STUDIO,
    accentColor: Color = AccentCrimson,
    glassmorphismEnabled: Boolean = true,
    albumPalette: AlbumPalette = remember(accentColor) { AlbumColorExtractor.fallbackPalette(accentColor) },
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val context = LocalContext.current

    val isLightAppearance = when (themeMode) {
        ThemeMode.LIGHT_STUDIO -> true
        ThemeMode.SYSTEM_DYNAMIC -> !systemDark
        else -> false
    }

    val colorScheme = remember(themeMode, accentColor, systemDark) {
        when (themeMode) {
            ThemeMode.DARK_STUDIO -> buildDarkScheme(accentColor, oledBlack = false)
            ThemeMode.OLED_BLACK -> buildDarkScheme(accentColor, oledBlack = true)
            ThemeMode.LIGHT_STUDIO -> buildLightScheme(accentColor)
            ThemeMode.SYSTEM_DYNAMIC -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } else {
                    if (systemDark) buildDarkScheme(accentColor, oledBlack = false)
                    else buildLightScheme(accentColor)
                }
            }
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = isLightAppearance
                controller.isAppearanceLightNavigationBars = isLightAppearance
            }
        }
    }

    CompositionLocalProvider(
        LocalVragSpacing provides VragSpacing(),
        LocalVragElevation provides VragElevation(),
        LocalGlassEnabled provides glassmorphismEnabled,
        LocalIsLightTheme provides isLightAppearance,
        LocalAlbumPalette provides albumPalette
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VragTypography,
            shapes = VragShapes,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode = ThemeMode.DARK_STUDIO,
    accentColor: Color = AccentCrimson,
    glassmorphismEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    VragTheme(
        themeMode = themeMode,
        accentColor = accentColor,
        glassmorphismEnabled = glassmorphismEnabled,
        content = content
    )
}

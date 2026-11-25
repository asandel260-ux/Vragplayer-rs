package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.screens.StudioMotion
import com.example.util.DevicePerf

val LocalGlassEnabled = compositionLocalOf { true }
val LocalIsLightTheme = compositionLocalOf { false }
val LocalAlbumPalette = compositionLocalOf {
    AlbumPalette(
        primary = AccentCrimson,
        secondary = AccentViolet,
        tertiary = AccentCobalt
    )
}

enum class GlassLevel {
    Subtle,   // Search bars, filter pills, secondary inline tiles
    Card,     // Bento metrics, EQ decks, settings groups, quick tiles
    Elevated, // Featured stage, prominent cards
    Floating, // Mini-player, bottom bar, floating docks
    Modal     // Dialogs, bottom sheets, menus
}

/**
 * Hardware-accelerated native blur helper:
 * Uses Android 12+ RenderEffect GPU blur ONLY when supported and not low-RAM;
 * zero overhead on older/low-RAM GPUs like PowerVR GE8100.
 */
fun Modifier.nativeHardwareBlur(radius: Dp): Modifier =
    if (DevicePerf.supportsNativeBlur && radius > 0.dp) {
        this.blur(radius = radius, edgeTreatment = BlurredEdgeTreatment.Rectangle)
    } else {
        this
    }

/**
 * Dynamic album-art reactive ambient backdrop:
 * - Zero 60fps infinite animation loops! Colors smoothly crossfade ONLY when switching songs,
 *   then drop to 0 GPU redraws/sec so scrolling stays locked at 60fps even on entry-level GPUs.
 * - On low-end GPUs (DevicePerf.isLowEnd), draws in a single lightweight vertical gradient pass
 *   (1x pixel fill-rate instead of 4x overlapping radial blurs).
 * - Collapses to a flat solid background when Liquid Glass is toggled off in Settings.
 */
@Composable
fun DynamicGlassBackdrop(
    palette: AlbumPalette,
    isPlaying: Boolean,
    themeMode: ThemeMode,
    modifier: Modifier = Modifier
) {
    val glassEnabled = LocalGlassEnabled.current
    val isLight = LocalIsLightTheme.current
    val baseBg = MaterialTheme.colorScheme.background

    if (!glassEnabled) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(baseBg)
        )
        return
    }

    val primary by animateColorAsState(
        targetValue = palette.primary,
        animationSpec = tween(520, easing = StudioMotion.STANDARD_CUBIC),
        label = "glass_bg_primary"
    )
    val secondary by animateColorAsState(
        targetValue = palette.secondary,
        animationSpec = tween(600, easing = StudioMotion.STANDARD_CUBIC),
        label = "glass_bg_secondary"
    )
    val tertiary by animateColorAsState(
        targetValue = palette.tertiary,
        animationSpec = tween(680, easing = StudioMotion.STANDARD_CUBIC),
        label = "glass_bg_tertiary"
    )

    val isOled = themeMode == ThemeMode.OLED_BLACK
    val topWash = lerp(baseBg, primary, if (isLight) 0.14f else if (isOled) 0.18f else 0.25f)
    val midWash = lerp(baseBg, secondary, if (isLight) 0.08f else if (isOled) 0.10f else 0.14f)
    val lowWash = lerp(baseBg, tertiary, if (isLight) 0.06f else if (isOled) 0.08f else 0.11f)

    val backdropBrush = remember(topWash, midWash, lowWash, baseBg) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to topWash,
                0.42f to midWash,
                0.82f to lowWash,
                1.0f to baseBg
            )
        )
    }

    if (DevicePerf.isLowEnd) {
        // Single-pass GPU fill (1x fill-rate) — still dynamically reacts to album art colors!
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(backdropBrush)
        )
        return
    }

    val intensity = when {
        isLight -> 0.22f
        isOled -> 0.24f
        else -> 0.34f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backdropBrush)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .nativeHardwareBlur(50.dp)
        ) {
            val w = size.width
            val h = size.height

            val c1 = Offset(w * 0.22f, h * 0.14f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primary.copy(alpha = intensity),
                        primary.copy(alpha = intensity * 0.32f),
                        Color.Transparent
                    ),
                    center = c1,
                    radius = w * 0.90f
                ),
                center = c1,
                radius = w * 0.90f
            )

            val c2 = Offset(w * 0.84f, h * 0.44f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        secondary.copy(alpha = intensity * 0.85f),
                        secondary.copy(alpha = intensity * 0.25f),
                        Color.Transparent
                    ),
                    center = c2,
                    radius = w * 0.84f
                ),
                center = c2,
                radius = w * 0.84f
            )

            val c3 = Offset(w * 0.28f, h * 0.84f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tertiary.copy(alpha = intensity * 0.78f),
                        tertiary.copy(alpha = intensity * 0.20f),
                        Color.Transparent
                    ),
                    center = c3,
                    radius = w * 0.82f
                ),
                center = c3,
                radius = w * 0.82f
            )
        }
    }
}

/**
 * Optical Liquid Glass Modifier (pure @Composable — zero Modifier.composed overhead!):
 * - On low-end devices (DevicePerf.isLowEnd), uses a fast single-pass translucent surface
 *   + single solid specular rim (1x overdraw instead of 4x stacked brushes + RenderNode layers).
 * - On capable GPUs, adds directional refraction gradient, top specular sheen, and bevel rim.
 * - Reverts cleanly to crisp solid surface styling when Liquid Glass is disabled in Settings.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(18.dp),
    level: GlassLevel = GlassLevel.Card,
    tintOverride: Color? = null,
    borderWidth: Dp = 0.85.dp
): Modifier {
    val glassEnabled = LocalGlassEnabled.current
    val isLight = LocalIsLightTheme.current
    val palette = LocalAlbumPalette.current
    val colorScheme = MaterialTheme.colorScheme

    if (!glassEnabled) {
        val solidBg = when (level) {
            GlassLevel.Subtle, GlassLevel.Card -> colorScheme.surfaceContainer
            GlassLevel.Elevated, GlassLevel.Floating, GlassLevel.Modal -> colorScheme.surfaceContainerHigh
        }
        val solidRim = colorScheme.onSurface.copy(alpha = 0.07f)
        return this
            .clip(shape)
            .background(tintOverride ?: solidBg)
            .border(0.75.dp, solidRim, shape)
    }

    val tint = tintOverride ?: palette.primary

    // Fast 1-pass translucent glass for PowerVR GE8100 / low-end GPUs
    if (DevicePerf.isLowEnd) {
        val fillColor = if (isLight) {
            val alpha = when (level) {
                GlassLevel.Subtle -> 0.60f
                GlassLevel.Card -> 0.70f
                GlassLevel.Elevated -> 0.76f
                GlassLevel.Floating -> 0.84f
                GlassLevel.Modal -> 0.92f
            }
            lerp(Color.White, tint, 0.07f).copy(alpha = alpha)
        } else {
            val alpha = when (level) {
                GlassLevel.Subtle -> 0.48f
                GlassLevel.Card -> 0.56f
                GlassLevel.Elevated -> 0.62f
                GlassLevel.Floating -> 0.74f
                GlassLevel.Modal -> 0.88f
            }
            lerp(colorScheme.surfaceContainerHigh, tint, 0.14f).copy(alpha = alpha)
        }
        val rimColor = if (isLight) {
            Color.White.copy(alpha = 0.72f)
        } else {
            Color.White.copy(alpha = if (level == GlassLevel.Floating || level == GlassLevel.Modal) 0.22f else 0.14f)
        }
        return this
            .clip(shape)
            .background(fillColor)
            .border(0.75.dp, rimColor, shape)
    }

    val fillBrush = remember(isLight, level, tint, palette.secondary, colorScheme.surfaceContainer, colorScheme.surfaceContainerHigh) {
        if (isLight) {
            val topAlpha = when (level) {
                GlassLevel.Subtle -> 0.56f
                GlassLevel.Card -> 0.66f
                GlassLevel.Elevated -> 0.72f
                GlassLevel.Floating -> 0.78f
                GlassLevel.Modal -> 0.88f
            }
            val bottomAlpha = (topAlpha - 0.12f).coerceAtLeast(0.42f)
            val tintedTop = lerp(Color.White, tint, 0.06f)
            val tintedBottom = lerp(colorScheme.surfaceContainer, tint, 0.10f)
            Brush.linearGradient(
                colors = listOf(
                    tintedTop.copy(alpha = topAlpha),
                    tintedBottom.copy(alpha = bottomAlpha)
                ),
                start = Offset.Zero,
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        } else {
            val topBase = lerp(colorScheme.surfaceContainerHigh, tint, 0.14f)
            val bottomBase = lerp(colorScheme.surfaceContainer, palette.secondary, 0.08f)
            val topAlpha = when (level) {
                GlassLevel.Subtle -> 0.42f
                GlassLevel.Card -> 0.50f
                GlassLevel.Elevated -> 0.56f
                GlassLevel.Floating -> 0.66f
                GlassLevel.Modal -> 0.84f
            }
            val bottomAlpha = when (level) {
                GlassLevel.Subtle -> 0.28f
                GlassLevel.Card -> 0.36f
                GlassLevel.Elevated -> 0.42f
                GlassLevel.Floating -> 0.54f
                GlassLevel.Modal -> 0.76f
            }
            Brush.linearGradient(
                colors = listOf(
                    topBase.copy(alpha = topAlpha),
                    bottomBase.copy(alpha = bottomAlpha)
                ),
                start = Offset.Zero,
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        }
    }

    val sheenBrush = remember(isLight) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.White.copy(alpha = if (isLight) 0.34f else 0.10f),
                0.35f to Color.White.copy(alpha = if (isLight) 0.09f else 0.03f),
                1.0f to Color.Transparent
            )
        )
    }

    val rimBrush = remember(isLight, level, tint, colorScheme.onSurface) {
        if (isLight) {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.85f),
                    Color.White.copy(alpha = 0.38f),
                    colorScheme.onSurface.copy(alpha = 0.10f)
                ),
                start = Offset.Zero,
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = when (level) {
                        GlassLevel.Modal, GlassLevel.Floating -> 0.30f
                        GlassLevel.Elevated -> 0.24f
                        else -> 0.18f
                    }),
                    Color.White.copy(alpha = 0.07f),
                    lerp(Color.White, tint, 0.45f).copy(alpha = 0.14f)
                ),
                start = Offset.Zero,
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        }
    }

    return this
        .clip(shape)
        .background(fillBrush)
        .background(sheenBrush)
        .border(width = borderWidth, brush = rimBrush, shape = shape)
}

/**
 * Hardware-accelerated Frosted Glass Container for high-elevation floating surfaces
 * (MiniPlayer, Bottom Navigation Bar, Modals, Floating Now-Playing Docks).
 * Skips extra blurred radial Canvas passes on GPUs without native RenderEffect blur.
 */
@Composable
fun FrostedGlassContainer(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    level: GlassLevel = GlassLevel.Floating,
    blurRadius: Dp = 24.dp,
    tintOverride: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val glassEnabled = LocalGlassEnabled.current
    val isLight = LocalIsLightTheme.current
    val palette = LocalAlbumPalette.current
    val accent = tintOverride ?: palette.primary

    Box(
        modifier = modifier
            .liquidGlass(shape = shape, level = level, tintOverride = tintOverride)
    ) {
        if (glassEnabled && DevicePerf.supportsNativeBlur) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .nativeHardwareBlur(blurRadius)
            ) {
                val glowAlpha = if (isLight) 0.16f else 0.24f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = glowAlpha), Color.Transparent),
                        center = Offset(size.width * 0.18f, size.height * 0.12f),
                        radius = size.maxDimension * 0.65f
                    ),
                    center = Offset(size.width * 0.18f, size.height * 0.12f),
                    radius = size.maxDimension * 0.65f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(palette.secondary.copy(alpha = glowAlpha * 0.72f), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.88f),
                        radius = size.maxDimension * 0.58f
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.88f),
                    radius = size.maxDimension * 0.58f
                )
            }
        }
        content()
    }
}

/**
 * Drop-in translucent frosted glass modal dialog.
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null
) {
    val glassEnabled = LocalGlassEnabled.current
    if (!glassEnabled) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = title,
            text = text,
            confirmButton = confirmButton,
            dismissButton = dismissButton
        )
        return
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        FrostedGlassContainer(
            shape = RoundedCornerShape(28.dp),
            level = GlassLevel.Modal,
            blurRadius = 30.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 22.dp)
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface
                ) {
                    ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                        title()
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                        text()
                    }
                    Spacer(modifier = Modifier.height(22.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}

@Composable
fun glassScreenBackground(): Color {
    return if (LocalGlassEnabled.current) Color.Transparent else MaterialTheme.colorScheme.background
}

@Composable
fun glassMenuContainerColor(): Color {
    val glassEnabled = LocalGlassEnabled.current
    val isLight = LocalIsLightTheme.current
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    return if (!glassEnabled) {
        base
    } else if (isLight) {
        lerp(Color.White, LocalAlbumPalette.current.primary, 0.05f).copy(alpha = 0.92f)
    } else {
        lerp(base, LocalAlbumPalette.current.primary, 0.14f).copy(alpha = 0.90f)
    }
}

@Composable
fun glassSheetContainerColor(): Color {
    val glassEnabled = LocalGlassEnabled.current
    val isLight = LocalIsLightTheme.current
    val base = MaterialTheme.colorScheme.surfaceContainer
    return if (!glassEnabled) {
        base
    } else if (isLight) {
        lerp(Color.White, LocalAlbumPalette.current.primary, 0.07f).copy(alpha = 0.90f)
    } else {
        lerp(base, LocalAlbumPalette.current.primary, 0.16f).copy(alpha = 0.86f)
    }
}

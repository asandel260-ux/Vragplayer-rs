package com.example.ui.screens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import com.example.util.DevicePerf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Custom cubic-bezier motion language tuned for flagship music player ergonomics:
 * - Zero robotic linear curves
 * - Immediate touch response (< 16ms start velocity) with silky velvet deceleration
 * - All list & expansion motion runs strictly in the GPU RenderNode (graphicsLayer) phase
 */
object StudioMotion {
    const val MICRO_MS = 140
    const val FAST_MS = 190
    const val STANDARD_MS = 260
    const val SHEET_EXPAND_MS = 340
    const val SHEET_COLLAPSE_MS = 240
    const val COVER_BREATHE_MS = 380

    // Material 3 / Apple Music Emphasized Decelerate: instant lift, velvet settle
    val EMPHASIZED_DECELERATE: Easing = CubicBezierEasing(0.05f, 0.70f, 0.10f, 1.00f)

    // Crisp acceleration out for sheet dismissal & back navigation
    val EMPHASIZED_ACCELERATE: Easing = CubicBezierEasing(0.30f, 0.00f, 0.80f, 0.15f)

    // Balanced shared-axis curve for tab switches & list reordering
    val STANDARD_CUBIC: Easing = CubicBezierEasing(0.20f, 0.00f, 0.00f, 1.00f)

    // Fluid ease-out-expo feel for cover art breathing, track crossfades, and swipe snapbacks
    val FLUID_COVER: Easing = CubicBezierEasing(0.22f, 1.00f, 0.36f, 1.00f)

    // Tactile micro-interaction snap for primary transport & hero controls
    val TACTILE_SNAP: Easing = CubicBezierEasing(0.16f, 1.00f, 0.30f, 1.00f)

    fun <T> emphasizedEnterSpec(durationMs: Int = SHEET_EXPAND_MS) =
        tween<T>(durationMillis = durationMs, easing = EMPHASIZED_DECELERATE)

    fun <T> emphasizedExitSpec(durationMs: Int = SHEET_COLLAPSE_MS) =
        tween<T>(durationMillis = durationMs, easing = EMPHASIZED_ACCELERATE)

    fun <T> standardSpec(durationMs: Int = STANDARD_MS) =
        tween<T>(durationMillis = durationMs, easing = STANDARD_CUBIC)

    fun <T> fluidCoverSpec(durationMs: Int = COVER_BREATHE_MS) =
        tween<T>(durationMillis = durationMs, easing = FLUID_COVER)

    val listPlacementSpec = tween<IntOffset>(
        durationMillis = STANDARD_MS,
        easing = STANDARD_CUBIC
    )
    val listFadeInSpec = tween<Float>(
        durationMillis = FAST_MS,
        easing = EMPHASIZED_DECELERATE
    )
    val listFadeOutSpec = tween<Float>(
        durationMillis = MICRO_MS,
        easing = EMPHASIZED_ACCELERATE
    )
}

/**
 * Hardware-accelerated scroll parallax & depth recession for top scrollable headers.
 * Reads LazyListState ONLY inside the graphicsLayer lambda (Draw phase), causing
 * ZERO recompositions and ZERO relayouts during 60/120fps scrolling.
 */
fun Modifier.gpuScrollHeader(
    listState: LazyListState,
    headerIndex: Int = 0,
    parallaxFactor: Float = 0.28f,
    fadeDistancePx: Float = 360f
): Modifier {
    if (DevicePerf.isLowEnd) return this
    return this.graphicsLayer {
        val firstIdx = listState.firstVisibleItemIndex
        val scrollPx = when {
            firstIdx > headerIndex -> fadeDistancePx
            firstIdx == headerIndex -> listState.firstVisibleItemScrollOffset.toFloat()
            else -> 0f
        }
        val rawProgress = (scrollPx / fadeDistancePx).coerceIn(0f, 1f)
        val eased = StudioMotion.STANDARD_CUBIC.transform(rawProgress)

        translationY = scrollPx * parallaxFactor
        val scale = 1f - (eased * 0.05f)
        scaleX = scale
        scaleY = scale
        alpha = (1f - eased * 0.85f).coerceIn(0.15f, 1f)
        transformOrigin = TransformOrigin(0.5f, 0.35f)
    }
}

/**
 * Hardware-accelerated focal scale for horizontal album shelves (Recently Played).
 * Automatically bypassed on low-end GPUs to eliminate per-card layoutInfo scans during scroll.
 */
fun Modifier.gpuShelfCardMotion(
    listState: LazyListState,
    itemKey: Any
): Modifier {
    if (DevicePerf.isLowEnd) return this
    return this.graphicsLayer {
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == itemKey }
        if (item != null && info.viewportSize.width > 0) {
            val viewportEnd = info.viewportEndOffset.toFloat()
            val itemCenter = item.offset + item.size * 0.5f
            val edgeFraction = ((itemCenter - viewportEnd * 0.78f) / (viewportEnd * 0.35f)).coerceIn(0f, 1f)
            val eased = StudioMotion.STANDARD_CUBIC.transform(edgeFraction)
            val scale = 1f - eased * 0.045f
            scaleX = scale
            scaleY = scale
            alpha = 1f - eased * 0.16f
        }
    }
}

/**
 * Hardware-accelerated GPU press feedback using custom TACTILE_SNAP cubic-bezier.
 * Intended strictly for focal hero controls (Play/Pause, MiniPlayer, Featured Stage);
 * scrolling song rows use lean Modifier.clickable for zero per-item allocation overhead.
 */
@Composable
fun Modifier.heroPressClick(
    enabled: Boolean = true,
    pressedScale: Float = 0.93f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = tween(
            durationMillis = if (isPressed) 90 else StudioMotion.FAST_MS,
            easing = StudioMotion.TACTILE_SNAP
        ),
        label = "hero_press"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            compositingStrategy = CompositingStrategy.Auto
        }
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Minimal 3-bar active indicator for the currently playing track row.
 * Zero-cost static bars when paused; single lightweight Canvas draw when playing.
 */
@Composable
fun ActiveTrackBars(
    isPlaying: Boolean,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    // Avoid continuous 60fps Canvas invalidation inside scrolling lists on low-end GPUs
    val animateBars = isPlaying && !DevicePerf.isLowEnd
    val phase = if (animateBars) {
        val transition = rememberInfiniteTransition(label = "bars")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "bars_phase"
        )
        value
    } else {
        0f
    }

    Canvas(modifier = modifier) {
        val barCount = 3
        val gap = size.width * 0.2f
        val barW = (size.width - gap * (barCount - 1)) / barCount
        val corner = CornerRadius(barW / 2f, barW / 2f)
        for (i in 0 until barCount) {
            val frac = if (animateBars) {
                (0.28f + 0.70f * abs(sin(phase + i * 1.1f))).coerceIn(0.22f, 1f)
            } else if (isPlaying) {
                when (i) {
                    0 -> 0.58f
                    1 -> 0.96f
                    else -> 0.74f
                }
            } else {
                when (i) {
                    0 -> 0.42f
                    1 -> 0.80f
                    else -> 0.56f
                }
            }
            val h = size.height * frac
            val x = i * (barW + gap)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, size.height - h),
                size = Size(barW, h),
                cornerRadius = corner
            )
        }
    }
}

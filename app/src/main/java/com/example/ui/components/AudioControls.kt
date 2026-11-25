package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Precision zero-dot hairline progress track for MiniPlayer & compact playback indicators.
 * Eliminates Material3 LinearProgressIndicator's jarring stop-indicator dot and gap artifacts.
 */
@Composable
fun HairlineProgressTrack(
    progress: Float,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    height: Dp = 2.dp,
    modifier: Modifier = Modifier
) {
    val clamped = progress.coerceIn(0f, 1f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(
            color = trackColor,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = radius
        )
        if (clamped > 0f) {
            drawRoundRect(
                color = activeColor,
                topLeft = Offset.Zero,
                size = Size(size.width * clamped, size.height),
                cornerRadius = radius
            )
        }
    }
}

/**
 * Flagship precision audio scrubber modeled on Apple Music / Tidal / Braun audio ergonomics:
 * - Thin 3.5dp pill track at rest, expands tactilely to 7dp while scrubbing
 * - Subtle glowing thumb grows smoothly on drag
 * - Generous 40dp touch target for effortless Fitts's Law thumb accuracy
 * - Supports both instant tap-to-seek and smooth horizontal dragging
 */
@Composable
fun VragScrubber(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    activeColor: Color = MaterialTheme.colorScheme.onBackground,
    inactiveColor: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.20f),
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }

    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 7.dp else 3.5.dp,
        animationSpec = tween(
            durationMillis = com.example.ui.screens.StudioMotion.MICRO_MS,
            easing = com.example.ui.screens.StudioMotion.TACTILE_SNAP
        ),
        label = "scrubber_track_h"
    )
    val thumbRadius by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 4.5.dp,
        animationSpec = tween(
            durationMillis = com.example.ui.screens.StudioMotion.MICRO_MS,
            easing = com.example.ui.screens.StudioMotion.TACTILE_SNAP
        ),
        label = "scrubber_thumb_r"
    )

    val clamped = value.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / widthPx).coerceIn(0f, 1f)
                    onValueChange(frac)
                    onValueChangeFinished()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        onValueChange((offset.x / widthPx).coerceIn(0f, 1f))
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChangeFinished()
                    },
                    onDragCancel = {
                        isDragging = false
                        onValueChangeFinished()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onValueChange((change.position.x / widthPx).coerceIn(0f, 1f))
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        ) {
            val thPx = trackHeight.toPx()
            val trPx = thumbRadius.toPx()
            val centerY = size.height / 2f
            val usableWidth = (size.width - trPx * 2f).coerceAtLeast(1f)
            val startX = trPx
            val activeEndX = startX + usableWidth * clamped
            val corner = CornerRadius(thPx / 2f, thPx / 2f)

            // Inactive background track
            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(startX, centerY - thPx / 2f),
                size = Size(usableWidth, thPx),
                cornerRadius = corner
            )

            // Active filled track
            val filledW = (activeEndX - startX).coerceAtLeast(0f)
            if (filledW > 0f) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(startX, centerY - thPx / 2f),
                    size = Size(filledW, thPx),
                    cornerRadius = corner
                )
            }

            // Soft halo when dragging
            if (isDragging) {
                drawCircle(
                    color = activeColor.copy(alpha = 0.22f),
                    radius = trPx * 1.9f,
                    center = Offset(activeEndX, centerY)
                )
            }

            // Precision thumb
            drawCircle(
                color = activeColor,
                radius = trPx,
                center = Offset(activeEndX, centerY)
            )
        }
    }
}

/**
 * Precision vertical studio fader for authentic graphic Equalizer console ergonomics.
 * Smooth vertical drag + tap-to-set with 0 dB center detent tick and crisp active fill.
 */
@Composable
fun VerticalEqFader(
    valueDb: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    detentColor: Color = MaterialTheme.colorScheme.outline,
    modifier: Modifier = Modifier
) {
    var heightPx by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }

    val clampedNorm = ((valueDb.coerceIn(-12f, 12f) + 12f) / 24f).coerceIn(0f, 1f)
    val thumbColor = if (enabled) activeColor else activeColor.copy(alpha = 0.35f)
    val fillColor = if (enabled) activeColor else activeColor.copy(alpha = 0.22f)

    Box(
        modifier = modifier
            .width(44.dp)
            .height(176.dp)
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val norm = (1f - offset.y / heightPx).coerceIn(0f, 1f)
                    onValueChange((norm * 24f - 12f).coerceIn(-12f, 12f))
                    onValueChangeFinished()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val norm = (1f - offset.y / heightPx).coerceIn(0f, 1f)
                        onValueChange((norm * 24f - 12f).coerceIn(-12f, 12f))
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChangeFinished()
                    },
                    onDragCancel = {
                        isDragging = false
                        onValueChangeFinished()
                    },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val norm = (1f - change.position.y / heightPx).coerceIn(0f, 1f)
                        onValueChange((norm * 24f - 12f).coerceIn(-12f, 12f))
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .width(36.dp)
                .height(164.dp)
        ) {
            val centerX = size.width / 2f
            val padY = 10.dp.toPx()
            val trackTop = padY
            val trackBottom = size.height - padY
            val trackLen = (trackBottom - trackTop).coerceAtLeast(1f)
            val midY = trackTop + trackLen / 2f
            val trackW = 5.dp.toPx()
            val thumbY = trackBottom - clampedNorm * trackLen

            // Center 0 dB detent tick marks
            drawLine(
                color = detentColor,
                start = Offset(centerX - 11.dp.toPx(), midY),
                end = Offset(centerX + 11.dp.toPx(), midY),
                strokeWidth = 1.dp.toPx()
            )

            // Rail background
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(centerX - trackW / 2f, trackTop),
                size = Size(trackW, trackLen),
                cornerRadius = CornerRadius(trackW / 2f, trackW / 2f)
            )

            // Active gain bar extending from 0 dB center to current value
            val barTop = minOf(midY, thumbY)
            val barH = kotlin.math.abs(midY - thumbY)
            if (barH > 0.5f) {
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(centerX - trackW / 2f, barTop),
                    size = Size(trackW, barH),
                    cornerRadius = CornerRadius(trackW / 2f, trackW / 2f)
                )
            }

            // Hardware fader cap (precision pill knob)
            val capW = if (isDragging) 24.dp.toPx() else 20.dp.toPx()
            val capH = if (isDragging) 14.dp.toPx() else 12.dp.toPx()
            if (isDragging) {
                drawRoundRect(
                    color = thumbColor.copy(alpha = 0.22f),
                    topLeft = Offset(centerX - capW * 0.72f, thumbY - capH * 0.85f),
                    size = Size(capW * 1.44f, capH * 1.7f),
                    cornerRadius = CornerRadius(capH, capH)
                )
            }
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(centerX - capW / 2f, thumbY - capH / 2f),
                size = Size(capW, capH),
                cornerRadius = CornerRadius(capH / 2f, capH / 2f)
            )
            // Tactile center ridge on fader cap
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = Offset(centerX - capW * 0.24f, thumbY),
                end = Offset(centerX + capW * 0.24f, thumbY),
                strokeWidth = 1.5.dp.toPx()
            )
        }
    }
}

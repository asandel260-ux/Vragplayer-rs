package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import com.example.data.Song
import com.example.ui.components.HairlineProgressTrack
import com.example.ui.components.TrackArtwork
import com.example.ui.theme.FrostedGlassContainer
import com.example.ui.theme.GlassLevel
import com.example.ui.theme.LocalAlbumPalette
import com.example.ui.theme.LocalGlassEnabled
import com.example.ui.theme.LocalIsLightTheme
import com.example.ui.theme.nativeHardwareBlur
import com.example.util.DevicePerf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    showCovers: Boolean,
    mediaControllerProvider: () -> MediaController?,
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onPlayerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val pressSource = remember { MutableInteractionSource() }
    val isPressed by pressSource.collectIsPressedAsState()
    val glassEnabled = LocalGlassEnabled.current
    val isLight = LocalIsLightTheme.current

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.975f else 1f,
        animationSpec = tween(
            durationMillis = if (isPressed) 90 else StudioMotion.FAST_MS,
            easing = StudioMotion.TACTILE_SNAP
        ),
        label = "mini_press_scale"
    )

    val extracted = LocalAlbumPalette.current.primary
    val baseSurface = MaterialTheme.colorScheme.surfaceContainerHigh
    val targetTint = if (glassEnabled) extracted else lerp(baseSurface, extracted, 0.24f)
    val animatedTint by animateColorAsState(
        targetValue = targetTint,
        animationSpec = tween(420, easing = StudioMotion.STANDARD_CUBIC),
        label = "mini_tint"
    )

    val shape = RoundedCornerShape(if (glassEnabled) 18.dp else 14.dp)

    val gestureModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp)
        .graphicsLayer {
            val dx = dragOffset.value
            val dragRatio = (abs(dx) / 160f).coerceIn(0f, 1f)
            val combinedScale = pressScale * (1f - dragRatio * 0.025f)
            translationX = dx * 0.48f
            scaleX = combinedScale
            scaleY = combinedScale
            alpha = 1f - dragRatio * 0.22f
        }
        .pointerInput(song.id) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    val dx = dragOffset.value
                    when {
                        dx < -72f -> onNext()
                        dx > 72f -> onPrevious()
                    }
                    scope.launch {
                        dragOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = StudioMotion.STANDARD_MS,
                                easing = StudioMotion.FLUID_COVER
                            )
                        )
                    }
                },
                onDragCancel = {
                    scope.launch {
                        dragOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = StudioMotion.STANDARD_MS,
                                easing = StudioMotion.FLUID_COVER
                            )
                        )
                    }
                },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    scope.launch {
                        dragOffset.snapTo((dragOffset.value + amount).coerceIn(-160f, 160f))
                    }
                }
            )
        }
        .testTag("mini_player_bar")

    val innerContent: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = pressSource,
                        indication = ripple(),
                        onClick = onPlayerClick
                    )
                    .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackArtwork(
                    song = song,
                    showCovers = showCovers,
                    sizePx = 112,
                    iconSize = 18.dp,
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.size(44.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("mini_player_play_pause")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("mini_player_next")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            MiniPlayerProgressBar(
                songId = song.id,
                isPlaying = isPlaying,
                durationMs = song.duration,
                mediaControllerProvider = mediaControllerProvider
            )
        }
    }

    if (glassEnabled) {
        FrostedGlassContainer(
            shape = shape,
            level = GlassLevel.Floating,
            blurRadius = 26.dp,
            tintOverride = animatedTint,
            modifier = gestureModifier
        ) {
            if (showCovers && DevicePerf.supportsNativeBlur && song.albumArtUri != null) {
                TrackArtwork(
                    song = song,
                    showCovers = true,
                    sizePx = 64,
                    iconSize = 0.dp,
                    shape = shape,
                    showRim = false,
                    modifier = Modifier
                        .matchParentSize()
                        .nativeHardwareBlur(32.dp)
                        .alpha(if (isLight) 0.14f else 0.24f)
                )
            }
            innerContent()
        }
    } else {
        Box(
            modifier = gestureModifier
                .clip(shape)
                .background(animatedTint)
                .border(0.75.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f), shape)
        ) {
            innerContent()
        }
    }
}

@Composable
private fun MiniPlayerProgressBar(
    songId: Long,
    isPlaying: Boolean,
    durationMs: Long,
    mediaControllerProvider: () -> MediaController?
) {
    var positionMs by remember(songId) { mutableLongStateOf(0L) }
    var resolvedDuration by remember(songId, durationMs) { mutableLongStateOf(durationMs) }

    LaunchedEffect(songId, isPlaying) {
        val controller = mediaControllerProvider()
        positionMs = try { controller?.currentPosition ?: 0L } catch (_: Throwable) { 0L }
        val ctrlDur = try { controller?.duration ?: 0L } catch (_: Throwable) { 0L }
        if (ctrlDur > 0L) resolvedDuration = ctrlDur

        val pollMs = DevicePerf.miniPlayerPollMs
        while (isPlaying) {
            delay(pollMs)
            val c = mediaControllerProvider()
            positionMs = try { c?.currentPosition ?: 0L } catch (_: Throwable) { 0L }
            val d = try { c?.duration ?: 0L } catch (_: Throwable) { 0L }
            if (d > 0L) resolvedDuration = d
        }
    }

    val progress = if (resolvedDuration > 0L) {
        (positionMs.toFloat() / resolvedDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    HairlineProgressTrack(
        progress = progress,
        activeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.90f),
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
        height = 2.dp
    )
}

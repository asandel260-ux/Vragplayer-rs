package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import com.example.data.Song
import com.example.ui.components.TrackArtwork
import com.example.ui.components.VragScrubber
import com.example.ui.components.formatCountdown
import com.example.ui.components.formatDuration
import com.example.ui.theme.FrostedGlassContainer
import com.example.ui.theme.GlassAlertDialog
import com.example.ui.theme.GlassLevel
import com.example.ui.theme.LocalAlbumPalette
import com.example.ui.theme.LocalGlassEnabled
import com.example.ui.theme.LocalIsLightTheme
import com.example.ui.theme.TabularCaption
import com.example.ui.theme.liquidGlass
import com.example.ui.theme.nativeHardwareBlur
import com.example.util.DevicePerf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerContent(
    song: Song?,
    isPlaying: Boolean,
    queueSource: String,
    isLiked: Boolean,
    repeatMode: Int,
    shuffleModeEnabled: Boolean,
    playbackSpeed: Float,
    showCovers: Boolean,
    sleepTimerRemainingMs: Long,
    sleepAfterCurrentTrack: Boolean,
    mediaControllerProvider: () -> MediaController?,
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onLikeToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onShuffleToggle: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onAddToPlaylist: () -> Unit,
    onShowTrackInfo: () -> Unit,
    onNavigateToQueue: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToSleepTimer: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showSpeedDialog by remember { mutableStateOf(false) }

    // GPU-accelerated sheet expansion choreography (runs purely on RenderNode via graphicsLayer)
    val sheetExpand = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        sheetExpand.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = StudioMotion.SHEET_EXPAND_MS,
                easing = StudioMotion.EMPHASIZED_DECELERATE
            )
        )
    }

    val dragX = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }

    // Breathing cover art driven by custom FLUID_COVER cubic-bezier curve
    val artScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.90f,
        animationSpec = tween(
            durationMillis = StudioMotion.COVER_BREATHE_MS,
            easing = StudioMotion.FLUID_COVER
        ),
        label = "art_scale"
    )

    val glassEnabled = LocalGlassEnabled.current
    val isLight = LocalIsLightTheme.current

    // Reuse shared tri-tone album palette computed once at root (zero duplicate bitmap decode)
    val palette = LocalAlbumPalette.current
    val ambientColor by animateColorAsState(
        targetValue = palette.primary,
        animationSpec = tween(560, easing = StudioMotion.STANDARD_CUBIC),
        label = "player_ambient"
    )
    val secondaryColor by animateColorAsState(
        targetValue = palette.secondary,
        animationSpec = tween(680, easing = StudioMotion.STANDARD_CUBIC),
        label = "player_ambient_sec"
    )
    val tertiaryColor by animateColorAsState(
        targetValue = palette.tertiary,
        animationSpec = tween(760, easing = StudioMotion.STANDARD_CUBIC),
        label = "player_ambient_ter"
    )

    val bgBase = MaterialTheme.colorScheme.background
    val topAmbientStop = remember(bgBase, ambientColor, glassEnabled, isLight) {
        lerp(bgBase, ambientColor, if (glassEnabled) (if (isLight) 0.24f else 0.54f) else 0.44f)
    }
    val midAmbientStop = remember(bgBase, secondaryColor, ambientColor, glassEnabled, isLight) {
        lerp(bgBase, if (glassEnabled) secondaryColor else ambientColor, if (glassEnabled) (if (isLight) 0.14f else 0.28f) else 0.16f)
    }
    val bottomAmbientStop = remember(bgBase, tertiaryColor, glassEnabled, isLight) {
        if (glassEnabled) lerp(bgBase, tertiaryColor, if (isLight) 0.10f else 0.22f) else bgBase
    }

    val sleepActive = sleepTimerRemainingMs > 0L || sleepAfterCurrentTrack

    // Whole Now Playing sheet transforms cohesively on the GPU during expansion & pull-to-dismiss
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val dy = dragY.value.coerceAtLeast(0f)
                val dismissFraction = (dy / 460f).coerceIn(0f, 1f)
                val expand = sheetExpand.value
                val sheetScale = (0.94f + 0.06f * expand) * (1f - dismissFraction * 0.08f)

                translationY = dy * 0.58f + (1f - expand) * 56.dp.toPx()
                scaleX = sheetScale
                scaleY = sheetScale
                alpha = (expand * (1f - dismissFraction * 0.25f)).coerceIn(0f, 1f)
                val cornerPx = (dismissFraction * 28f + (1f - expand) * 24f).dp
                shape = RoundedCornerShape(cornerPx)
                clip = dismissFraction > 0.01f || expand < 0.99f
                transformOrigin = TransformOrigin(0.5f, 0.15f)
            }
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to topAmbientStop,
                        0.46f to midAmbientStop,
                        0.82f to bottomAmbientStop,
                        1.0f to bgBase
                    )
                )
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        val dy = dragY.value
                        if (dy > 160f) {
                            onBack()
                        } else {
                            scope.launch {
                                dragY.animateTo(
                                    0f,
                                    tween(StudioMotion.STANDARD_MS, easing = StudioMotion.EMPHASIZED_DECELERATE)
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            dragY.animateTo(
                                0f,
                                tween(StudioMotion.STANDARD_MS, easing = StudioMotion.EMPHASIZED_DECELERATE)
                            )
                        }
                    },
                    onDrag = { change, amount ->
                        if (amount.y > 0f || dragY.value > 0f) {
                            change.consume()
                            scope.launch {
                                dragY.snapTo((dragY.value + amount.y).coerceIn(0f, 420f))
                            }
                        }
                    }
                )
            }
    ) {
        // Hardware-accelerated frosted album-art atmospheric veil strictly on capable API 31+ GPUs
        if (glassEnabled && DevicePerf.supportsNativeBlur) {
            if (showCovers && song?.albumArtUri != null) {
                TrackArtwork(
                    song = song,
                    showCovers = true,
                    sizePx = 96,
                    iconSize = 0.dp,
                    shape = RoundedCornerShape(0.dp),
                    showRim = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .nativeHardwareBlur(68.dp)
                        .alpha(if (isLight) 0.18f else 0.34f)
                )
            }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .nativeHardwareBlur(48.dp)
            ) {
                val orbAlpha = if (isLight) 0.22f else 0.34f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(ambientColor.copy(alpha = orbAlpha), Color.Transparent),
                        center = Offset(size.width * 0.22f, size.height * 0.20f),
                        radius = size.width * 0.85f
                    ),
                    center = Offset(size.width * 0.22f, size.height * 0.20f),
                    radius = size.width * 0.85f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(secondaryColor.copy(alpha = orbAlpha * 0.85f), Color.Transparent),
                        center = Offset(size.width * 0.84f, size.height * 0.62f),
                        radius = size.width * 0.80f
                    ),
                    center = Offset(size.width * 0.84f, size.height * 0.62f),
                    radius = size.width * 0.80f
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to bgBase.copy(alpha = if (isLight) 0.35f else 0.28f),
                                0.52f to bgBase.copy(alpha = if (isLight) 0.56f else 0.44f),
                                1.0f to bgBase.copy(alpha = if (isLight) 0.82f else 0.72f)
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .align(Alignment.Center)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Subtle sheet grip indicator
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f))
            )

            // Minimal Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("player_back_button")
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimize",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .then(
                            if (glassEnabled) {
                                Modifier
                                    .liquidGlass(shape = CircleShape, level = GlassLevel.Subtle)
                                    .padding(horizontal = 14.dp, vertical = 5.dp)
                            } else {
                                Modifier.padding(horizontal = 12.dp)
                            }
                        )
                ) {
                    Text(
                        text = queueSource,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onNavigateToQueue,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("player_queue_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.55f))

            // Hero Artwork Stage with hardware-accelerated lift, breathing scale, and 3D tilt swipe
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                // Soft chromatic halo directly behind cover (skipped on low-end GPUs to save fill-rate)
                if (!DevicePerf.isLowEnd) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val expand = sheetExpand.value
                                val combined = artScale * (0.88f + 0.12f * expand) * 1.12f
                                scaleX = combined
                                scaleY = combined
                                alpha = (if (isPlaying) 0.52f else 0.28f) * expand
                            }
                    ) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ambientColor.copy(alpha = 0.55f),
                                    ambientColor.copy(alpha = 0.14f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width / 2f, size.height * 0.54f),
                                radius = size.minDimension * 0.56f
                            )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.90f)
                        .aspectRatio(1f)
                        .graphicsLayer {
                            val expand = sheetExpand.value
                            val dx = dragX.value
                            val swipeRatio = (abs(dx) / 240f).coerceIn(0f, 1f)
                            val coverScale = artScale * (0.86f + 0.14f * expand) * (1f - swipeRatio * 0.06f)

                            translationX = dx * 0.48f
                            translationY = (1f - expand) * 26.dp.toPx()
                            rotationZ = (dx / 240f) * 4.5f
                            scaleX = coverScale
                            scaleY = coverScale
                            alpha = (1f - swipeRatio * 0.24f) * expand
                            cameraDistance = 14f * density
                            compositingStrategy = CompositingStrategy.Auto
                        }
                        .pointerInput(song?.id) {
                            detectDragGestures(
                                onDragEnd = {
                                    val dx = dragX.value
                                    val dy = dragY.value
                                    when {
                                        dy > 160f -> onBack()
                                        dx < -100f -> onNext()
                                        dx > 100f -> onPrevious()
                                    }
                                    scope.launch {
                                        launch {
                                            dragX.animateTo(
                                                0f,
                                                tween(StudioMotion.STANDARD_MS, easing = StudioMotion.FLUID_COVER)
                                            )
                                        }
                                        launch {
                                            dragY.animateTo(
                                                0f,
                                                tween(StudioMotion.STANDARD_MS, easing = StudioMotion.EMPHASIZED_DECELERATE)
                                            )
                                        }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch {
                                        launch {
                                            dragX.animateTo(
                                                0f,
                                                tween(StudioMotion.STANDARD_MS, easing = StudioMotion.FLUID_COVER)
                                            )
                                        }
                                        launch {
                                            dragY.animateTo(
                                                0f,
                                                tween(StudioMotion.STANDARD_MS, easing = StudioMotion.EMPHASIZED_DECELERATE)
                                            )
                                        }
                                    }
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    scope.launch {
                                        if (abs(amount.x) >= abs(amount.y) || abs(dragX.value) > 12f) {
                                            dragX.snapTo((dragX.value + amount.x).coerceIn(-240f, 240f))
                                        } else {
                                            dragY.snapTo((dragY.value + amount.y).coerceIn(0f, 360f))
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    AnimatedContent(
                        targetState = song,
                        transitionSpec = {
                            (fadeIn(tween(240, easing = StudioMotion.FLUID_COVER)) +
                                scaleIn(
                                    animationSpec = tween(260, easing = StudioMotion.FLUID_COVER),
                                    initialScale = 0.94f
                                )) togetherWith
                                (fadeOut(tween(180, easing = StudioMotion.EMPHASIZED_ACCELERATE)) +
                                    scaleOut(
                                        animationSpec = tween(180, easing = StudioMotion.EMPHASIZED_ACCELERATE),
                                        targetScale = 0.96f
                                    ))
                        },
                        label = "player_cover_xfade"
                    ) { current ->
                        TrackArtwork(
                            song = current,
                            showCovers = showCovers,
                            sizePx = 600,
                            iconSize = 68.dp,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.55f))

            // Staggered GPU-accelerated controls deck (zero layout invalidation)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val expand = sheetExpand.value
                        val deckProgress = ((expand - 0.12f) / 0.88f).coerceIn(0f, 1f)
                        translationY = (1f - deckProgress) * 28.dp.toPx()
                        alpha = deckProgress
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title, Artist & Favorite
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = song?.title ?: "Not Playing",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, velocity = 36.dp)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = song?.artist ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val likeScale by animateFloatAsState(
                        targetValue = if (isLiked) 1.0f else 0.92f,
                        animationSpec = tween(StudioMotion.FAST_MS, easing = StudioMotion.TACTILE_SNAP),
                        label = "like_scale"
                    )
                    IconButton(
                        onClick = onLikeToggle,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .graphicsLayer {
                                scaleX = likeScale
                                scaleY = likeScale
                            }
                            .testTag("player_like_button")
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isLiked) "Remove favorite" else "Favorite",
                            tint = if (isLiked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Isolated Precision Scrubber
                PlayerSeekSection(
                    song = song,
                    isPlaying = isPlaying,
                    mediaControllerProvider = mediaControllerProvider,
                    onSeek = onSeek
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Flagship Transport Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TransportModeToggle(
                        active = shuffleModeEnabled,
                        icon = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        onClick = onShuffleToggle,
                        testTag = "player_shuffle_button"
                    )

                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("player_previous_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onBackground)
                            .heroPressClick(pressedScale = 0.91f, onClick = onPlayPauseToggle)
                            .testTag("player_play_pause_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.background,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick = onNext,
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("player_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    TransportModeToggle(
                        active = repeatMode != 0,
                        icon = if (repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        onClick = onRepeatToggle,
                        testTag = "player_repeat_button"
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Floating Liquid Glass Utility Dock (collapses cleanly to minimal strip when Glass is off)
                val utilityRow: @Composable () -> Unit = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (glassEnabled) 8.dp else 0.dp, vertical = if (glassEnabled) 2.dp else 0.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (playbackSpeed != 1.0f) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                                    else Color.Transparent
                                )
                                .clickable { showSpeedDialog = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("player_speed_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${if (playbackSpeed % 1f == 0f) playbackSpeed.toInt() else playbackSpeed}x",
                                style = TabularCaption,
                                fontWeight = FontWeight.Bold,
                                color = if (playbackSpeed != 1.0f) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.74f)
                            )
                        }

                        IconButton(
                            onClick = onNavigateToEqualizer,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("player_eq_button")
                        ) {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = "Equalizer",
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.76f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = onNavigateToSleepTimer,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("player_sleep_button")
                        ) {
                            Icon(
                                Icons.Default.Bedtime,
                                contentDescription = "Sleep timer",
                                tint = if (sleepActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.76f),
                                modifier = Modifier.size(21.dp)
                            )
                        }

                        IconButton(
                            onClick = onAddToPlaylist,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("player_add_playlist_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = "Add to playlist",
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.76f),
                                modifier = Modifier.size(23.dp)
                            )
                        }

                        IconButton(
                            onClick = onShowTrackInfo,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("player_info_button")
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Song info",
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.76f),
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }

                if (glassEnabled) {
                    FrostedGlassContainer(
                        shape = CircleShape,
                        level = GlassLevel.Floating,
                        blurRadius = 22.dp,
                        tintOverride = ambientColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        utilityRow()
                    }
                } else {
                    utilityRow()
                }

                if (sleepActive) {
                    Text(
                        text = if (sleepAfterCurrentTrack) "Pausing after this song"
                        else "Sleep in ${formatCountdown(sleepTimerRemainingMs)}",
                        style = TabularCaption,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }

    if (showSpeedDialog) {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        GlassAlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback Speed") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    speeds.forEach { speed ->
                        val selected = speed == playbackSpeed
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .clickable {
                                    onSpeedChange(speed)
                                    showSpeedDialog = false
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${speed}x",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun TransportModeToggle(
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String
) {
    val dotScale by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(StudioMotion.FAST_MS, easing = StudioMotion.TACTILE_SNAP),
        label = "mode_dot"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag(testTag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.56f),
                modifier = Modifier.size(22.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(4.dp)
                .graphicsLayer {
                    scaleX = dotScale
                    scaleY = dotScale
                    alpha = dotScale
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun PlayerSeekSection(
    song: Song?,
    isPlaying: Boolean,
    mediaControllerProvider: () -> MediaController?,
    onSeek: (Long) -> Unit
) {
    var resolvedDuration by remember(song?.id, song?.duration) {
        mutableLongStateOf((song?.duration ?: 0L).coerceAtLeast(1L))
    }
    var positionMs by remember(song?.id) { mutableLongStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(song?.id, isPlaying) {
        val c = mediaControllerProvider()
        positionMs = try { c?.currentPosition ?: 0L } catch (_: Throwable) { 0L }
        val ctrlDur = try { c?.duration ?: 0L } catch (_: Throwable) { 0L }
        if (ctrlDur > 0L) resolvedDuration = ctrlDur

        val pollMs = DevicePerf.scrubberPollMs
        while (isPlaying) {
            delay(pollMs)
            if (!isDragging) {
                val controller = mediaControllerProvider()
                positionMs = try { controller?.currentPosition ?: 0L } catch (_: Throwable) { 0L }
                val d = try { controller?.duration ?: 0L } catch (_: Throwable) { 0L }
                if (d > 0L) resolvedDuration = d
            }
        }
    }

    val safeDuration = resolvedDuration.coerceAtLeast(1L)
    val currentFraction = if (isDragging) {
        dragFraction
    } else {
        (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        VragScrubber(
            value = currentFraction,
            onValueChange = {
                isDragging = true
                dragFraction = it
            },
            onValueChangeFinished = {
                val targetMs = (dragFraction * safeDuration).toLong()
                positionMs = targetMs
                isDragging = false
                onSeek(targetMs)
            },
            activeColor = MaterialTheme.colorScheme.onBackground,
            inactiveColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.20f),
            modifier = Modifier.testTag("player_seek_slider")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayPos = if (isDragging) (dragFraction * safeDuration).toLong() else positionMs
            Text(
                text = formatDuration(displayPos),
                style = TabularCaption,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f)
            )
            Text(
                text = formatDuration(if (resolvedDuration > 1L) resolvedDuration else (song?.duration ?: 0L)),
                style = TabularCaption,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f)
            )
        }
    }
}

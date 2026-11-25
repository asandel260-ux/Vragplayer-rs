package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.Song
import com.example.data.formatDurationFast
import com.example.data.formatFileSize
import com.example.ui.screens.ActiveTrackBars
import com.example.ui.theme.GlassLevel
import com.example.ui.theme.LocalGlassEnabled
import com.example.ui.theme.TabularCaption
import com.example.ui.theme.artworkToneForKey
import com.example.ui.theme.glassMenuContainerColor
import com.example.ui.theme.glassSheetContainerColor
import com.example.ui.theme.liquidGlass
import com.example.util.DevicePerf

/**
 * High-performance album artwork renderer:
 * - Uses zero-subcomposition AsyncImage (eliminates Main-Thread SubcomposeLayout stalls on scroll!).
 * - For list thumbnails (sizePx < 240), uses a single flat tonal surface + vector glyph fallback
 *   with zero per-row Canvas/radialGradient overdraw.
 * - Reserves specular vinyl-sleeve Canvas detail strictly for large hero artwork (sizePx >= 240).
 */
@Composable
fun TrackArtwork(
    song: Song?,
    showCovers: Boolean,
    sizePx: Int = 128,
    iconSize: Dp = 22.dp,
    shape: Shape = RoundedCornerShape(10.dp),
    showRim: Boolean = true,
    modifier: Modifier = Modifier
) {
    val artUri = if (showCovers) song?.albumArtUri else null
    val fallbackKey = song?.artworkKey ?: "vrag"
    val (topTone, bottomTone) = remember(fallbackKey) { artworkToneForKey(fallbackKey) }
    val isHeroSize = sizePx >= 240
    val drawRim = showRim && (isHeroSize || !DevicePerf.isLowEnd)
    val rimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    val bgModifier = if (isHeroSize && !DevicePerf.isLowEnd) {
        val gradient = remember(topTone, bottomTone) {
            Brush.linearGradient(
                colors = listOf(topTone, bottomTone),
                start = Offset.Zero,
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        }
        Modifier.background(gradient)
    } else {
        Modifier.background(topTone)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(bgModifier)
            .then(
                if (drawRim) Modifier.border(0.6.dp, rimColor, shape)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isHeroSize && !DevicePerf.isLowEnd) {
            VinylSleeveFallback(iconSize)
        } else if (iconSize > 0.dp) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.52f),
                modifier = Modifier.size(iconSize)
            )
        }

        if (artUri != null) {
            val context = LocalContext.current
            val request = remember(artUri, sizePx) {
                ImageRequest.Builder(context)
                    .data(artUri)
                    .size(sizePx)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = song?.title ?: "Album cover",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun VinylSleeveFallback(iconSize: Dp) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(size.width * 0.22f, size.height * 0.18f),
                    radius = size.minDimension * 0.85f
                )
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = size.minDimension * 0.34f,
                style = Stroke(width = 1.dp.toPx())
            )
        }
        if (iconSize > 0.dp) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.56f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * 2x2 real album art mosaic for playlists & collections.
 * Shows 4 distinct covers when available, single cover if fewer, or clean tonal fallback if empty.
 */
@Composable
fun ArtworkMosaic(
    songs: List<Song>,
    showCovers: Boolean,
    sizePx: Int = 112,
    iconSize: Dp = 22.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    modifier: Modifier = Modifier
) {
    val distinctSongs = remember(songs) {
        val withArt = songs.distinctBy { if (it.albumId > 0L) it.albumId else it.id }
        withArt.take(4)
    }
    val rimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    if (distinctSongs.size < 4) {
        TrackArtwork(
            song = distinctSongs.firstOrNull(),
            showCovers = showCovers,
            sizePx = sizePx * 2,
            iconSize = iconSize,
            shape = shape,
            modifier = modifier
        )
    } else {
        Column(
            modifier = modifier
                .clip(shape)
                .border(0.6.dp, rimColor, shape)
        ) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                TrackArtwork(
                    song = distinctSongs[0],
                    showCovers = showCovers,
                    sizePx = sizePx,
                    iconSize = iconSize * 0.65f,
                    shape = RoundedCornerShape(0.dp),
                    showRim = false,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                TrackArtwork(
                    song = distinctSongs[1],
                    showCovers = showCovers,
                    sizePx = sizePx,
                    iconSize = iconSize * 0.65f,
                    shape = RoundedCornerShape(0.dp),
                    showRim = false,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                TrackArtwork(
                    song = distinctSongs[2],
                    showCovers = showCovers,
                    sizePx = sizePx,
                    iconSize = iconSize * 0.65f,
                    shape = RoundedCornerShape(0.dp),
                    showRim = false,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                TrackArtwork(
                    song = distinctSongs[3],
                    showCovers = showCovers,
                    sizePx = sizePx,
                    iconSize = iconSize * 0.65f,
                    shape = RoundedCornerShape(0.dp),
                    showRim = false,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
    }
}

/**
 * Refined editorial pill selector (replaces clunky bordered Material FilterChips).
 */
@Composable
fun EditorialPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val glassEnabled = LocalGlassEnabled.current
    val textColor = when {
        selected -> MaterialTheme.colorScheme.background
        enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }

    val pillSurface = if (selected) {
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onBackground)
    } else if (glassEnabled) {
        modifier.liquidGlass(shape = CircleShape, level = GlassLevel.Subtle)
    } else {
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
    }

    Box(
        modifier = pillSurface
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            maxLines = 1
        )
    }
}

/**
 * Editorial track row with crisp 20dp outer alignment, specular cover rim,
 * clear figure-ground contrast, and zero card-soup overhead.
 */
@Composable
fun StudioSongRow(
    song: Song,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    isLiked: Boolean,
    showCovers: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShowInfo: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("song_row_${song.id}")
            .then(
                if (isCurrentSong) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(50.dp)) {
            TrackArtwork(
                song = song,
                showCovers = showCovers,
                sizePx = 112,
                iconSize = 20.dp,
                shape = SongRowArtworkShape,
                modifier = Modifier.fillMaxSize()
            )
            if (isCurrentSong) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(SongRowArtworkShape)
                        .background(Color.Black.copy(alpha = 0.52f)),
                    contentAlignment = Alignment.Center
                ) {
                    ActiveTrackBars(
                        isPlaying = isPlaying,
                        color = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (isLiked) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(11.dp)
                    )
                }
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                )
                Text(
                    text = song.durationFormatted,
                    style = TabularCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }
        }

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("song_menu_${song.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.size(20.dp)
                )
            }

            if (menuExpanded) {
                val context = LocalContext.current
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = glassMenuContainerColor()
                ) {
                    DropdownMenuItem(
                        text = { Text("Play") },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            menuExpanded = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Play next") },
                        leadingIcon = { Icon(Icons.Default.QueuePlayNext, null) },
                        onClick = {
                            menuExpanded = false
                            onPlayNext()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to queue") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                        onClick = {
                            menuExpanded = false
                            onAddToQueue()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isLiked) "Remove favorite" else "Add to favorites") },
                        leadingIcon = {
                            Icon(
                                if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onToggleLike()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to playlist") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                        onClick = {
                            menuExpanded = false
                            onAddToPlaylist()
                        }
                    )
                    if (onRemoveFromPlaylist != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DropdownMenuItem(
                            text = { Text("Remove from playlist", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onRemoveFromPlaylist()
                            }
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DropdownMenuItem(
                        text = { Text("Song info") },
                        leadingIcon = { Icon(Icons.Default.Info, null) },
                        onClick = {
                            menuExpanded = false
                            onShowInfo()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                        onClick = {
                            menuExpanded = false
                            shareTrackIntent(context, song)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackInfoBottomSheet(
    song: Song,
    showCovers: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = glassSheetContainerColor()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TrackArtwork(
                    song = song,
                    showCovers = showCovers,
                    sizePx = 160,
                    iconSize = 26.dp,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(62.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .size(44.dp)
                        .liquidGlass(shape = CircleShape, level = GlassLevel.Elevated)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            InfoMetadataRow("Album", song.album)
            InfoMetadataRow("Duration", formatDuration(song.duration))
            InfoMetadataRow("Format", song.formatLabel)
            InfoMetadataRow("Size", formatFileSize(song.size))
            if (song.genre.isNotBlank() && song.genre != "Unknown Genre") {
                InfoMetadataRow("Genre", song.genre)
            }
            if (song.year > 0) {
                InfoMetadataRow("Year", song.year.toString())
            }
            InfoMetadataRow("Folder", song.folderName)
        }
    }
}

@Composable
private fun InfoMetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun shareTrackIntent(context: Context, song: Song) {
    val shareText = "${song.title} — ${song.artist}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, song.title)
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Share"))
    } catch (_: Exception) {
    }
}

private val SongRowArtworkShape = RoundedCornerShape(9.dp)

fun formatDuration(durationMs: Long): String = formatDurationFast(durationMs)

fun formatCountdown(remainingMs: Long): String {
    val safeSec = if (remainingMs > 0L) remainingMs / 1000L else 0L
    val mins = safeSec / 60L
    val secs = safeSec % 60L
    val sb = StringBuilder(5)
    if (mins < 10L) sb.append('0')
    sb.append(mins).append(':')
    if (secs < 10L) sb.append('0')
    sb.append(secs)
    return sb.toString()
}

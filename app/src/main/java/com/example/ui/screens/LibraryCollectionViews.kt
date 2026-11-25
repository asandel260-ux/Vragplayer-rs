package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Song
import com.example.ui.Playlist
import com.example.ui.components.ArtworkMosaic
import com.example.ui.components.TrackArtwork
import com.example.ui.components.formatDuration
import com.example.ui.theme.AccentCrimson
import com.example.ui.theme.GlassLevel
import com.example.ui.theme.LocalGlassEnabled
import com.example.ui.theme.TabularCaption
import com.example.ui.theme.glassMenuContainerColor
import com.example.ui.theme.liquidGlass
import com.example.ui.theme.rememberSongAmbientColor
import com.example.util.DevicePerf

/**
 * Scrollable Editorial Library Header:
 * Dynamically composes real album covers from the user's library into a rich tinted backdrop
 * that smoothly scrolls out of view as the user scrolls down.
 */
@Composable
internal fun ScrollableLibraryHero(
    title: String,
    songs: List<Song>,
    showCovers: Boolean,
    isLikedSection: Boolean,
    onImportAudio: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coverSongs = remember(songs) {
        songs.distinctBy { if (it.albumId > 0L) it.albumId else it.id }.take(4)
    }
    val shape = RoundedCornerShape(20.dp)
    val baseTint = if (isLikedSection) AccentCrimson else MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .height(164.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        lerp(MaterialTheme.colorScheme.surfaceContainer, baseTint, 0.35f),
                        MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            )
            .border(0.75.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape)
    ) {
        // Real library artwork collage backdrop (zero static JPEG assets!)
        if (coverSongs.isNotEmpty() && showCovers) {
            Row(modifier = Modifier.fillMaxSize()) {
                coverSongs.forEach { sample ->
                    TrackArtwork(
                        song = sample,
                        showCovers = true,
                        sizePx = 192,
                        iconSize = 26.dp,
                        shape = RoundedCornerShape(0.dp),
                        showRim = false,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }

        // Rich cinematic bottom-to-top scrim guaranteeing high-contrast text legibility on any cover
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.28f),
                            0.45f to Color.Black.copy(alpha = 0.56f),
                            1.0f to Color.Black.copy(alpha = 0.88f)
                        )
                    )
                )
        )

        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${songs.size} ${if (songs.size == 1) "song" else "songs"}",
                    style = TabularCaption,
                    color = Color.White.copy(alpha = 0.78f)
                )
            }

            if (!isLikedSection) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .border(0.75.dp, Color.White.copy(alpha = 0.24f), CircleShape)
                        .clickable(onClick = onImportAudio)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("library_import_audio"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.FileOpen,
                        contentDescription = "Import audio",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Import",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Editorial Play + Shuffle full-width pill action bar (high-contrast primary + calm tonal secondary).
 */
@Composable
internal fun PlayAllShuffleRow(
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onPlayAll,
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .testTag("play_all_button"),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background
            )
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Play", fontWeight = FontWeight.Bold)
        }
        val glassEnabled = LocalGlassEnabled.current
        if (glassEnabled) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .liquidGlass(shape = CircleShape, level = GlassLevel.Card)
                    .clickable(onClick = onShuffleAll)
                    .testTag("shuffle_all_button"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Shuffle",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            Button(
                onClick = onShuffleAll,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("shuffle_all_button"),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Shuffle", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * 2-Column Visual Album Grid Card (square cover art + bold title + artist).
 */
@Composable
internal fun AlbumGridPairRow(
    pair: List<Pair<String, List<Song>>>,
    showCovers: Boolean,
    onAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        pair.forEach { (albumName, albumSongs) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onAlbumClick(albumName) }
            ) {
                TrackArtwork(
                    song = albumSongs.firstOrNull(),
                    showCovers = showCovers,
                    sizePx = 280,
                    iconSize = 38.dp,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = albumName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${albumSongs.firstOrNull()?.artist ?: ""} · ${albumSongs.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (pair.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Artist Row with Circular Artist Portrait (matches Apple Music / Spotify editorial convention).
 */
@Composable
internal fun ArtistPortraitRow(
    artist: String,
    songs: List<Song>,
    showCovers: Boolean,
    onClick: () -> Unit
) {
    val albumCount = remember(songs) { songs.map { it.album }.distinct().size }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtwork(
            song = songs.firstOrNull(),
            showCovers = showCovers,
            sizePx = 128,
            iconSize = 22.dp,
            shape = CircleShape,
            modifier = Modifier.size(54.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$albumCount ${if (albumCount == 1) "album" else "albums"} · ${songs.size} ${if (songs.size == 1) "song" else "songs"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Playlist Row with dynamic 2x2 Album Mosaic Cover + full Triple-Dot Menu.
 */
@Composable
internal fun PlaylistListRow(
    playlist: Playlist,
    resolvedSongs: List<Song>,
    showCovers: Boolean,
    onClick: () -> Unit,
    onPlaySequential: () -> Unit,
    onPlayRandom: () -> Unit,
    onAddToQueue: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("playlist_row_${playlist.id}")
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (resolvedSongs.isNotEmpty()) {
            ArtworkMosaic(
                songs = resolvedSongs,
                showCovers = showCovers,
                sizePx = 96,
                iconSize = 22.dp,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(56.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val durationSummary = remember(resolvedSongs) {
                val totalDur = resolvedSongs.sumOf { it.duration }
                "${resolvedSongs.size} ${if (resolvedSongs.size == 1) "song" else "songs"} · ${formatDuration(totalDur)}"
            }
            Text(
                text = durationSummary,
                style = TabularCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("playlist_menu_${playlist.id}")
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Playlist options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (menuExpanded) {
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = glassMenuContainerColor()
                ) {
                    DropdownMenuItem(
                        text = { Text("Play in order") },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            menuExpanded = false
                            onPlaySequential()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Shuffle") },
                        leadingIcon = { Icon(Icons.Default.Shuffle, null) },
                        onClick = {
                            menuExpanded = false
                            onPlayRandom()
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Editorial Collection / Playlist Detail Hero (centered large cover/mosaic, dynamic ambient wash,
 * bold centered title, full-width Play + Shuffle pill buttons, and triple-dot menu).
 */
@Composable
internal fun GroupDetailHeader(
    title: String,
    songs: List<Song>,
    showCovers: Boolean,
    playlist: Playlist?,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onAddSongs: (() -> Unit)?,
    onAddToQueue: () -> Unit,
    onRenamePlaylist: (() -> Unit)?,
    onClearPlaylist: (() -> Unit)?,
    onDeletePlaylist: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val songCount = songs.size
    val totalDurationMs = remember(songs) { songs.sumOf { it.duration } }

    val ambient = rememberSongAmbientColor(
        song = songs.firstOrNull(),
        showCovers = showCovers,
        fallback = MaterialTheme.colorScheme.primary
    )
    val bgBase = MaterialTheme.colorScheme.background
    val glassEnabled = LocalGlassEnabled.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (glassEnabled) ambient.copy(alpha = 0.28f) else lerp(bgBase, ambient, 0.34f),
                        if (glassEnabled) Color.Transparent else bgBase
                    )
                )
            )
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("playlist_detail_menu")
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Collection options",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (menuExpanded) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = glassMenuContainerColor()
                    ) {
                        DropdownMenuItem(
                            text = { Text("Play in order") },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                menuExpanded = false
                                onPlayAll()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Shuffle") },
                            leadingIcon = { Icon(Icons.Default.Shuffle, null) },
                            onClick = {
                                menuExpanded = false
                                onShuffle()
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
                        if (playlist != null) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            onAddSongs?.let { addFn ->
                                DropdownMenuItem(
                                    text = { Text("Add songs") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                                    onClick = {
                                        menuExpanded = false
                                        addFn()
                                    }
                                )
                            }
                            onRenamePlaylist?.let { renameFn ->
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = {
                                        menuExpanded = false
                                        renameFn()
                                    }
                                )
                            }
                            onClearPlaylist?.let { clearFn ->
                                DropdownMenuItem(
                                    text = { Text("Clear songs") },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                                    onClick = {
                                        menuExpanded = false
                                        clearFn()
                                    }
                                )
                            }
                            onDeletePlaylist?.let { deleteFn ->
                                DropdownMenuItem(
                                    text = { Text("Delete playlist", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menuExpanded = false
                                        deleteFn()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Centered Hero Artwork with subtle chromatic aura
        Box(
            modifier = Modifier.size(188.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!DevicePerf.isLowEnd) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(ambient.copy(alpha = 0.42f), Color.Transparent),
                            center = Offset(size.width / 2f, size.height * 0.56f),
                            radius = size.minDimension * 0.55f
                        )
                    )
                }
            }
            if (playlist != null && songs.size >= 4) {
                ArtworkMosaic(
                    songs = songs,
                    showCovers = showCovers,
                    sizePx = 180,
                    iconSize = 36.dp,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(164.dp)
                )
            } else {
                TrackArtwork(
                    song = songs.firstOrNull(),
                    showCovers = showCovers,
                    sizePx = 360,
                    iconSize = 46.dp,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(164.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "$songCount ${if (songCount == 1) "song" else "songs"} · ${formatDuration(totalDurationMs)}",
            style = TabularCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (songCount > 0) {
            PlayAllShuffleRow(onPlayAll = onPlayAll, onShuffleAll = onShuffle)
        }

        if (onAddSongs != null) {
            TextButton(
                onClick = onAddSongs,
                modifier = Modifier.testTag("playlist_add_songs_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Songs", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun GroupCollectionRow(
    title: String,
    subtitle: String,
    count: Int,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .liquidGlass(shape = RoundedCornerShape(12.dp), level = GlassLevel.Subtle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            text = "$count",
            style = TabularCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun LibraryEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .liquidGlass(shape = CircleShape, level = GlassLevel.Card),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onAction,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background
                )
            ) {
                Text(actionLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

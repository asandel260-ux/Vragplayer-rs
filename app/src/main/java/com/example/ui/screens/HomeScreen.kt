package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Song
import com.example.ui.MusicViewModel
import com.example.ui.components.StudioSongRow
import com.example.ui.components.TrackArtwork
import com.example.ui.theme.AccentCrimson
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.FrostedGlassContainer
import com.example.ui.theme.GlassLevel
import com.example.ui.theme.LocalAlbumPalette
import com.example.ui.theme.LocalGlassEnabled
import com.example.ui.theme.glassScreenBackground
import com.example.ui.theme.liquidGlass
import java.util.Calendar

@Composable
fun HomeContent(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    viewModel: MusicViewModel,
    onSongClick: (Song, List<Song>, String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToLiked: () -> Unit
) {
    val stats by viewModel.stats.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val recentSongs by viewModel.recentlyPlayed.collectAsState()
    val showCovers by viewModel.showCovers.collectAsState()

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    val featuredSongs = remember(songs, stats.songPlayCounts) {
        if (stats.songPlayCounts.isEmpty()) songs.take(10)
        else songs.sortedByDescending { stats.songPlayCounts[it.id] ?: 0 }.take(10)
    }

    val recentlyAddedSongs = remember(songs) {
        songs.sortedByDescending { it.dateAdded }
    }

    val heroSong = currentSong ?: recentSongs.firstOrNull() ?: songs.firstOrNull()
    val rawHeroAmbient = LocalAlbumPalette.current.primary
    val heroAmbient by animateColorAsState(
        targetValue = rawHeroAmbient,
        animationSpec = StudioMotion.fluidCoverSpec(480),
        label = "home_hero_ambient"
    )

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val listState = rememberLazyListState()
    val shelfState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(glassScreenBackground()),
        contentPadding = PaddingValues(top = statusTop + 10.dp, bottom = 28.dp)
    ) {
        // 1. Editorial Greeting Top Bar
        item(key = "home_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .gpuScrollHeader(
                        listState = listState,
                        headerIndex = 0,
                        parallaxFactor = 0.20f,
                        fadeDistancePx = 220f
                    )
                    .padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onNavigateToEqualizer,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_equalizer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Equalizer",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // 2. Proven 2x2 Quick-Access Compact Grid (fast one-handed thumb access)
        item(key = "home_quick_grid") {
            val firstPlaylist = playlists.firstOrNull()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickGridTile(
                        title = "Favorites",
                        subtitle = if (likedSongs.isNotEmpty()) "${likedSongs.size} songs" else null,
                        icon = Icons.Default.Favorite,
                        tint = AccentCrimson,
                        onClick = onNavigateToLiked,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_liked_tile")
                    )
                    QuickGridTile(
                        title = "Shuffle All",
                        subtitle = if (songs.isNotEmpty()) "${songs.size} songs" else null,
                        icon = Icons.Default.Shuffle,
                        tint = AccentEmerald,
                        onClick = {
                            if (songs.isNotEmpty()) {
                                viewModel.playAllShuffled(songs, "Shuffled")
                                onNavigateToPlayer()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_shuffle_all")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickGridTile(
                        title = "Playlists",
                        subtitle = if (playlists.isNotEmpty()) "${playlists.size} saved" else "Create new",
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        tint = AccentViolet,
                        onClick = onNavigateToPlaylists,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_playlists_tile")
                    )
                    if (firstPlaylist != null) {
                        val plCount = firstPlaylist.songIds.size
                        QuickGridTile(
                            title = firstPlaylist.name,
                            subtitle = "$plCount ${if (plCount == 1) "song" else "songs"}",
                            icon = Icons.Default.PlayArrow,
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = {
                                if (plCount > 0) {
                                    viewModel.playPlaylistSequential(firstPlaylist.id)
                                    onNavigateToPlayer()
                                } else {
                                    onNavigateToPlaylists()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        QuickGridTile(
                            title = "Recently Added",
                            subtitle = if (recentlyAddedSongs.isNotEmpty()) "${recentlyAddedSongs.size} tracks" else null,
                            icon = Icons.Default.Schedule,
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = {
                                if (recentlyAddedSongs.isNotEmpty()) {
                                    onSongClick(recentlyAddedSongs.first(), recentlyAddedSongs, "Recently Added")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // 3. Chromatic Featured Stage (Album-Art-Tinted Liquid Glass Continue Listening / Now Playing)
        if (heroSong != null) {
            item(key = "home_hero") {
                val glassEnabled = LocalGlassEnabled.current
                val baseSurface = MaterialTheme.colorScheme.surfaceContainer
                val startTint = lerp(baseSurface, heroAmbient, 0.34f)
                val endTint = lerp(baseSurface, heroAmbient, 0.10f)
                val heroShape = RoundedCornerShape(20.dp)

                val heroRowContent: @Composable () -> Unit = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (currentSong != null) {
                                    onNavigateToPlayer()
                                } else {
                                    onSongClick(heroSong, songs, "Library")
                                }
                            }
                            .padding(14.dp)
                            .testTag("home_now_playing_card"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrackArtwork(
                            song = heroSong,
                            showCovers = showCovers,
                            sizePx = 192,
                            iconSize = 28.dp,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(66.dp)
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (currentSong != null && isPlaying) "Now Playing"
                                else if (currentSong != null) "Paused"
                                else "Jump Back In",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = heroSong.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = heroSong.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onBackground)
                                .heroPressClick {
                                    if (currentSong == null) {
                                        viewModel.playFromList(heroSong, songs, "Library")
                                    } else {
                                        viewModel.togglePlayback()
                                    }
                                }
                                .testTag("home_card_play_pause"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.background,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                if (glassEnabled) {
                    FrostedGlassContainer(
                        shape = heroShape,
                        level = GlassLevel.Elevated,
                        blurRadius = 24.dp,
                        tintOverride = heroAmbient,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        heroRowContent()
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(heroShape)
                            .background(Brush.horizontalGradient(listOf(startTint, endTint)))
                            .border(0.75.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), heroShape)
                    ) {
                        heroRowContent()
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // 4. Recently Played Borderless Album Shelf
        if (recentSongs.isNotEmpty()) {
            item(key = "recent_header") {
                SectionHeading(
                    title = "Recently Played",
                    actionText = "See all",
                    onAction = onNavigateToLibrary
                )
            }

            item(key = "recent_carousel") {
                LazyRow(
                    state = shelfState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(recentSongs.take(10), key = { "recent_${it.id}" }) { song ->
                        AlbumShelfCard(
                            song = song,
                            isCurrent = currentSong?.id == song.id,
                            showCovers = showCovers,
                            onClick = { onSongClick(song, recentSongs, "Recently Played") },
                            modifier = Modifier.gpuShelfCardMotion(shelfState, "recent_${song.id}")
                        )
                    }
                }
                Spacer(modifier = Modifier.height(22.dp))
            }
        }

        // 5. Most Played / Songs Feed
        if (featuredSongs.isNotEmpty()) {
            item(key = "tracks_header") {
                SectionHeading(
                    title = if (stats.songPlayCounts.isNotEmpty()) "Your Rotation" else "Songs",
                    actionText = "Library",
                    onAction = onNavigateToLibrary
                )
            }

            items(
                items = featuredSongs,
                key = { "home_song_${it.id}" },
                contentType = { "song_row" }
            ) { song ->
                val isCurrent = currentSong?.id == song.id
                StudioSongRow(
                    song = song,
                    isCurrentSong = isCurrent,
                    isPlaying = isCurrent && isPlaying,
                    isLiked = song.id in likedSongs,
                    showCovers = showCovers,
                    onClick = { onSongClick(song, featuredSongs, "Library") },
                    onPlayNext = { viewModel.playNextInQueue(song) },
                    onAddToQueue = { viewModel.addToQueue(song) },
                    onToggleLike = { viewModel.toggleLike(song.id) },
                    onAddToPlaylist = { viewModel.requestAddToPlaylist(song) },
                    onShowInfo = { viewModel.showSongInfo(song) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 84.dp, end = 20.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

@Composable
private fun QuickGridTile(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .height(56.dp)
            .liquidGlass(shape = shape, level = GlassLevel.Card)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    Brush.linearGradient(
                        listOf(tint.copy(alpha = 0.85f), tint.copy(alpha = 0.45f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AlbumShelfCard(
    song: Song,
    isCurrent: Boolean,
    showCovers: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(144.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        TrackArtwork(
            song = song,
            showCovers = showCovers,
            sizePx = 256,
            iconSize = 36.dp,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(144.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SectionHeading(
    title: String,
    actionText: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        TextButton(onClick = onAction) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

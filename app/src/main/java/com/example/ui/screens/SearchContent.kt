package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Song
import com.example.ui.MusicViewModel
import com.example.ui.components.EditorialPill
import com.example.ui.components.StudioSongRow
import com.example.ui.theme.GlassLevel
import com.example.ui.theme.artworkToneForKey
import com.example.ui.theme.glassScreenBackground
import com.example.ui.theme.liquidGlass

@Composable
fun SearchContent(
    songs: List<Song>,
    viewModel: MusicViewModel,
    onSongClick: (Song, List<Song>, String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filterScope by rememberSaveable { mutableStateOf("All") }

    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val showCovers by viewModel.showCovers.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val searchPlayedIds by viewModel.searchPlayedIds.collectAsState()

    val scopes = remember { listOf("All", "Songs", "Artists", "Albums") }

    val filteredSongs by remember(songs, query, filterScope) {
        derivedStateOf {
            val q = query.trim()
            if (q.isEmpty()) emptyList()
            else songs.filter { song ->
                when (filterScope) {
                    "Songs" -> song.title.contains(q, ignoreCase = true)
                    "Artists" -> song.artist.contains(q, ignoreCase = true)
                    "Albums" -> song.album.contains(q, ignoreCase = true)
                    else -> song.title.contains(q, ignoreCase = true) ||
                        song.artist.contains(q, ignoreCase = true) ||
                        song.album.contains(q, ignoreCase = true) ||
                        song.genre.contains(q, ignoreCase = true)
                }
            }
        }
    }

    val searchPlayedSongs by remember(songs, searchPlayedIds) {
        derivedStateOf {
            val map = songs.associateBy { it.id }
            searchPlayedIds.mapNotNull { map[it] }
        }
    }

    // Real browse categories derived strictly from the user's actual local library
    val browseCategories = remember(songs) {
        val byGenre = songs
            .filter { it.genre.isNotBlank() && it.genre != "Unknown Genre" && it.genre != "Other" }
            .groupingBy { it.genre }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(6)
            .map { it.key to "${it.value} ${if (it.value == 1) "song" else "songs"}" }

        if (byGenre.size >= 2) byGenre
        else songs
            .filter { it.artist.isNotBlank() && it.artist != "Unknown Artist" }
            .groupingBy { it.artist }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(6)
            .map { it.key to "${it.value} ${if (it.value == 1) "song" else "songs"}" }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val fieldShape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(glassScreenBackground())
            .padding(top = statusTop)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Search",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .liquidGlass(shape = fieldShape, level = GlassLevel.Card)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_input_field"),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                text = "Songs, artists, albums",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        inner()
                    }
                )
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { query = "" },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("search_clear_button")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (query.isNotBlank()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(scopes) { scope ->
                        EditorialPill(
                            label = scope,
                            selected = filterScope == scope,
                            onClick = { filterScope = scope }
                        )
                    }
                }
            }
        }

        if (query.isNotBlank() && filteredSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(34.dp)
                    )
                    Text(
                        text = "No results for \"$query\"",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                if (query.isBlank()) {
                    if (recentSearches.isNotEmpty()) {
                        item(key = "recent_hdr") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Searches",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(onClick = { viewModel.clearRecentSearches() }) {
                                    Text("Clear", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        items(recentSearches, key = { "term_$it" }) { term ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { query = term }
                                    .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = term,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.removeRecentSearch(term) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (browseCategories.isNotEmpty()) {
                        item(key = "browse_hdr") {
                            Text(
                                text = "Browse Library",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 10.dp)
                            )
                        }

                        val pairs = browseCategories.chunked(2)
                        items(pairs, key = { "cat_${it.first().first}" }) { pair ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                pair.forEach { (label, countText) ->
                                    val (topTone, bottomTone) = remember(label) { artworkToneForKey(label) }
                                    val cardShape = RoundedCornerShape(14.dp)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(82.dp)
                                            .clip(cardShape)
                                            .background(Brush.linearGradient(listOf(topTone, bottomTone)))
                                            .border(0.6.dp, Color.White.copy(alpha = 0.08f), cardShape)
                                            .clickable {
                                                filterScope = "All"
                                                query = label
                                            }
                                            .padding(14.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.align(Alignment.BottomStart),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = countText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.72f)
                                            )
                                        }
                                    }
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    if (searchPlayedSongs.isNotEmpty()) {
                        item(key = "found_hdr") {
                            Text(
                                text = "Recently Found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp)
                            )
                        }
                        items(
                            items = searchPlayedSongs,
                            key = { "found_${it.id}" },
                            contentType = { "song_row" }
                        ) { song ->
                            val isCurrent = currentSong?.id == song.id
                            StudioSongRow(
                                song = song,
                                isCurrentSong = isCurrent,
                                isPlaying = isCurrent && isPlaying,
                                isLiked = song.id in likedSongs,
                                showCovers = showCovers,
                                onClick = {
                                    viewModel.addRecentSearch(song.title)
                                    viewModel.registerSearchPlayedSong(song.id)
                                    onSongClick(song, searchPlayedSongs, "Search")
                                },
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
                } else {
                    items(
                        items = filteredSongs,
                        key = { "res_${it.id}" },
                        contentType = { "song_row" }
                    ) { song ->
                        val isCurrent = currentSong?.id == song.id
                        StudioSongRow(
                            song = song,
                            isCurrentSong = isCurrent,
                            isPlaying = isCurrent && isPlaying,
                            isLiked = song.id in likedSongs,
                            showCovers = showCovers,
                            onClick = {
                                viewModel.addRecentSearch(query)
                                viewModel.registerSearchPlayedSong(song.id)
                                onSongClick(song, filteredSongs, "Search")
                            },
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
    }
}

package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Song
import com.example.ui.MusicViewModel
import com.example.ui.Playlist
import com.example.ui.components.EditorialPill
import com.example.ui.components.StudioSongRow
import com.example.ui.theme.GlassAlertDialog
import com.example.ui.theme.glassMenuContainerColor
import com.example.ui.theme.glassScreenBackground

@Composable
fun LibraryContent(
    songs: List<Song>,
    title: String,
    viewModel: MusicViewModel,
    onPlayFromList: (Song, List<Song>, String, String?) -> Unit,
    onImportAudio: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    initialTabIndex: Int = 0,
    onInitialTabConsumed: () -> Unit = {},
    isLikedSection: Boolean = false,
    onBack: (() -> Unit)? = null
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val showCovers by viewModel.showCovers.collectAsState()
    val sortType by viewModel.sortType.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(initialTabIndex) }
    var selectedGroupKey by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(initialTabIndex) {
        if (initialTabIndex > 0) {
            selectedTabIndex = initialTabIndex
            selectedGroupKey = null
            onInitialTabConsumed()
        }
    }

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    var playlistForAddingSongs by remember { mutableStateOf<Playlist?>(null) }

    BackHandler(enabled = !isLikedSection && selectedGroupKey != null) {
        selectedGroupKey = null
    }

    val tabs = remember {
        listOf("Songs", "Playlists", "Albums", "Artists", "Favorites", "Folders", "Genres", "Years")
    }

    val albums by remember(songs) { derivedStateOf { songs.groupBy { it.album } } }
    val albumPairs by remember(songs) {
        derivedStateOf { albums.entries.map { it.key to it.value }.chunked(2) }
    }
    val artists by remember(songs) { derivedStateOf { songs.groupBy { it.artist } } }
    val folders by remember(songs) { derivedStateOf { songs.groupBy { it.folderName } } }
    val genres by remember(songs) { derivedStateOf { songs.groupBy { it.genre } } }
    val years by remember(songs) {
        derivedStateOf { songs.groupBy { it.year.toString() }.filterKeys { it != "0" } }
    }
    val favSongs by remember(songs, likedSongs) {
        derivedStateOf { songs.filter { it.id in likedSongs } }
    }

    val activePlaylist = if (!isLikedSection && selectedTabIndex == 1 && selectedGroupKey != null) {
        playlists.find { it.id == selectedGroupKey }
    } else null

    LaunchedEffect(playlists, selectedTabIndex, selectedGroupKey) {
        if (!isLikedSection && selectedTabIndex == 1 && selectedGroupKey != null && activePlaylist == null) {
            selectedGroupKey = null
        }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val listState = rememberLazyListState()

    // Single LazyColumn so the top artwork collage hero glides away on the GPU with zero recompositions
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(glassScreenBackground()),
        contentPadding = PaddingValues(top = statusTop + 6.dp, bottom = 28.dp)
    ) {
        if (selectedGroupKey == null) {
            item(key = "library_hero") {
                ScrollableLibraryHero(
                    title = title,
                    songs = songs,
                    showCovers = showCovers,
                    isLikedSection = isLikedSection,
                    onImportAudio = onImportAudio,
                    onBack = onBack,
                    modifier = Modifier.gpuScrollHeader(
                        listState = listState,
                        headerIndex = 0,
                        parallaxFactor = 0.30f,
                        fadeDistancePx = 380f
                    )
                )
            }
        }

        if (isLikedSection) {
            if (songs.isNotEmpty()) {
                item(key = "liked_actions") {
                    PlayAllShuffleRow(
                        onPlayAll = { onPlayFromList(songs.first(), songs, "Favorites", null) },
                        onShuffleAll = {
                            viewModel.playAllShuffled(songs, "Favorites")
                            onNavigateToPlayer()
                        }
                    )
                }
                items(
                    items = songs,
                    key = { "liked_${it.id}" },
                    contentType = { "song_row" }
                ) { song ->
                    val isCurrent = currentSong?.id == song.id
                    StudioSongRow(
                        song = song,
                        isCurrentSong = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        isLiked = true,
                        showCovers = showCovers,
                        onClick = { onPlayFromList(song, songs, "Favorites", null) },
                        onPlayNext = { viewModel.playNextInQueue(song) },
                        onAddToQueue = { viewModel.addToQueue(song) },
                        onToggleLike = { viewModel.toggleLike(song.id) },
                        onAddToPlaylist = { viewModel.requestAddToPlaylist(song) },
                        onShowInfo = { viewModel.showSongInfo(song) }
                    )
                    RowDivider()
                }
            } else {
                item(key = "liked_empty") {
                    LibraryEmptyState(
                        icon = Icons.Default.Favorite,
                        title = "No Favorites Yet",
                        subtitle = "Tap the heart on any song to save it here."
                    )
                }
            }
        } else if (selectedGroupKey != null) {
            val groupSongs = when (selectedTabIndex) {
                1 -> activePlaylist?.let { viewModel.resolvePlaylistSongs(it) } ?: emptyList()
                2 -> albums[selectedGroupKey] ?: emptyList()
                3 -> artists[selectedGroupKey] ?: emptyList()
                5 -> folders[selectedGroupKey] ?: emptyList()
                6 -> genres[selectedGroupKey] ?: emptyList()
                7 -> years[selectedGroupKey] ?: emptyList()
                else -> emptyList()
            }
            val groupDisplayName = activePlaylist?.name ?: selectedGroupKey.orEmpty()

            item(key = "group_detail_header") {
                GroupDetailHeader(
                    title = groupDisplayName,
                    songs = groupSongs,
                    showCovers = showCovers,
                    playlist = activePlaylist,
                    onBack = { selectedGroupKey = null },
                    onPlayAll = {
                        if (activePlaylist != null) {
                            viewModel.playPlaylistSequential(activePlaylist.id)
                            if (groupSongs.isNotEmpty()) onNavigateToPlayer()
                        } else if (groupSongs.isNotEmpty()) {
                            onPlayFromList(groupSongs.first(), groupSongs, groupDisplayName, null)
                        }
                    },
                    onShuffle = {
                        if (activePlaylist != null) {
                            viewModel.playPlaylistRandom(activePlaylist.id)
                            if (groupSongs.isNotEmpty()) onNavigateToPlayer()
                        } else if (groupSongs.isNotEmpty()) {
                            viewModel.playAllShuffled(groupSongs, groupDisplayName)
                            onNavigateToPlayer()
                        }
                    },
                    onAddSongs = activePlaylist?.let { pl -> { playlistForAddingSongs = pl } },
                    onAddToQueue = { viewModel.addListToQueue(groupSongs, groupDisplayName) },
                    onRenamePlaylist = activePlaylist?.let { pl -> { playlistToRename = pl } },
                    onClearPlaylist = activePlaylist?.let { pl -> { viewModel.clearPlaylist(pl.id) } },
                    onDeletePlaylist = activePlaylist?.let { pl -> { playlistToDelete = pl } },
                    modifier = Modifier.gpuScrollHeader(
                        listState = listState,
                        headerIndex = 0,
                        parallaxFactor = 0.24f,
                        fadeDistancePx = 420f
                    )
                )
            }

            if (groupSongs.isEmpty()) {
                item(key = "group_empty") {
                    LibraryEmptyState(
                        icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                        title = "No Songs Yet",
                        subtitle = if (activePlaylist != null) "Add songs to start listening." else "No songs in this collection.",
                        actionLabel = if (activePlaylist != null) "Add Songs" else null,
                        onAction = activePlaylist?.let { pl -> { playlistForAddingSongs = pl } }
                    )
                }
            } else {
                items(
                    items = groupSongs,
                    key = { "group_${selectedGroupKey}_${it.id}" },
                    contentType = { "song_row" }
                ) { song ->
                    val isCurrent = currentSong?.id == song.id
                    StudioSongRow(
                        song = song,
                        isCurrentSong = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        isLiked = song.id in likedSongs,
                        showCovers = showCovers,
                        onClick = { onPlayFromList(song, groupSongs, groupDisplayName, activePlaylist?.id) },
                        onPlayNext = { viewModel.playNextInQueue(song) },
                        onAddToQueue = { viewModel.addToQueue(song) },
                        onToggleLike = { viewModel.toggleLike(song.id) },
                        onAddToPlaylist = { viewModel.requestAddToPlaylist(song) },
                        onShowInfo = { viewModel.showSongInfo(song) },
                        onRemoveFromPlaylist = activePlaylist?.let { pl ->
                            { viewModel.removeSongFromPlaylist(pl.id, song.id) }
                        }
                    )
                    RowDivider()
                }
            }
        } else {
            item(key = "library_tabs") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(tabs) { idx, label ->
                        EditorialPill(
                            label = label,
                            selected = selectedTabIndex == idx,
                            onClick = {
                                selectedTabIndex = idx
                                selectedGroupKey = null
                            },
                            modifier = Modifier.testTag("library_tab_${label.lowercase()}")
                        )
                    }
                }
            }

            when (selectedTabIndex) {
                0 -> { // Songs
                    item(key = "tracks_toolbar") {
                        SortAndPlayToolbar(
                            sortType = sortType,
                            sortAscending = sortAscending,
                            trackCount = songs.size,
                            onSortSelected = { type -> viewModel.setSorting(type, sortAscending) },
                            onToggleDirection = { viewModel.setSorting(sortType, !sortAscending) },
                            onPlayAll = {
                                if (songs.isNotEmpty()) onPlayFromList(songs.first(), songs, "Library", null)
                            },
                            onShuffleAll = {
                                viewModel.playAllShuffled(songs, "Shuffled")
                                onNavigateToPlayer()
                            }
                        )
                    }
                    items(
                        items = songs,
                        key = { "all_${it.id}" },
                        contentType = { "song_row" }
                    ) { song ->
                        val isCurrent = currentSong?.id == song.id
                        StudioSongRow(
                            song = song,
                            isCurrentSong = isCurrent,
                            isPlaying = isCurrent && isPlaying,
                            isLiked = song.id in likedSongs,
                            showCovers = showCovers,
                            onClick = { onPlayFromList(song, songs, "Library", null) },
                            onPlayNext = { viewModel.playNextInQueue(song) },
                            onAddToQueue = { viewModel.addToQueue(song) },
                            onToggleLike = { viewModel.toggleLike(song.id) },
                            onAddToPlaylist = { viewModel.requestAddToPlaylist(song) },
                            onShowInfo = { viewModel.showSongInfo(song) }
                        )
                        RowDivider()
                    }
                }

                1 -> { // Playlists
                    item(key = "playlists_bar") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${playlists.size} ${if (playlists.size == 1) "Playlist" else "Playlists"}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Button(
                                onClick = { showCreatePlaylistDialog = true },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onBackground,
                                    contentColor = MaterialTheme.colorScheme.background
                                ),
                                modifier = Modifier.testTag("create_playlist_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("New Playlist", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    if (playlists.isEmpty()) {
                        item(key = "playlists_empty") {
                            LibraryEmptyState(
                                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                                title = "No Playlists",
                                subtitle = "Create a playlist to curate your favorite tracks.",
                                actionLabel = "Create Playlist",
                                onAction = { showCreatePlaylistDialog = true }
                            )
                        }
                    } else {
                        items(
                            items = playlists,
                            key = { "playlist_${it.id}" },
                            contentType = { "playlist_row" }
                        ) { playlist ->
                            val resolvedSongs = remember(playlist.songIds, songs) {
                                viewModel.resolvePlaylistSongs(playlist)
                            }
                            PlaylistListRow(
                                playlist = playlist,
                                resolvedSongs = resolvedSongs,
                                showCovers = showCovers,
                                onClick = { selectedGroupKey = playlist.id },
                                onPlaySequential = {
                                    viewModel.playPlaylistSequential(playlist.id)
                                    if (resolvedSongs.isNotEmpty()) onNavigateToPlayer()
                                },
                                onPlayRandom = {
                                    viewModel.playPlaylistRandom(playlist.id)
                                    if (resolvedSongs.isNotEmpty()) onNavigateToPlayer()
                                },
                                onAddToQueue = { viewModel.addListToQueue(resolvedSongs, playlist.name) },
                                onRename = { playlistToRename = playlist },
                                onDelete = { playlistToDelete = playlist }
                            )
                            RowDivider()
                        }
                    }
                }

                2 -> { // Albums — True 2-Column Visual Album Grid!
                    items(
                        items = albumPairs,
                        key = { pair -> "album_pair_${pair.first().first}" },
                        contentType = { "album_pair" }
                    ) { pair ->
                        AlbumGridPairRow(
                            pair = pair,
                            showCovers = showCovers,
                            onAlbumClick = { selectedGroupKey = it }
                        )
                    }
                }

                3 -> { // Artists — Circular Editorial Artist Portraits!
                    items(artists.entries.toList(), key = { "artist_${it.key}" }) { (artist, list) ->
                        ArtistPortraitRow(
                            artist = artist,
                            songs = list,
                            showCovers = showCovers,
                            onClick = { selectedGroupKey = artist }
                        )
                        RowDivider()
                    }
                }

                4 -> { // Favorites
                    if (favSongs.isEmpty()) {
                        item(key = "favs_empty") {
                            LibraryEmptyState(
                                icon = Icons.Default.Favorite,
                                title = "No Favorites",
                                subtitle = "Tap the heart on any song to save it here."
                            )
                        }
                    } else {
                        item(key = "favs_actions") {
                            PlayAllShuffleRow(
                                onPlayAll = { onPlayFromList(favSongs.first(), favSongs, "Favorites", null) },
                                onShuffleAll = {
                                    viewModel.playAllShuffled(favSongs, "Favorites")
                                    onNavigateToPlayer()
                                }
                            )
                        }
                        items(
                            items = favSongs,
                            key = { "fav_${it.id}" },
                            contentType = { "song_row" }
                        ) { song ->
                            val isCurrent = currentSong?.id == song.id
                            StudioSongRow(
                                song = song,
                                isCurrentSong = isCurrent,
                                isPlaying = isCurrent && isPlaying,
                                isLiked = true,
                                showCovers = showCovers,
                                onClick = { onPlayFromList(song, favSongs, "Favorites", null) },
                                onPlayNext = { viewModel.playNextInQueue(song) },
                                onAddToQueue = { viewModel.addToQueue(song) },
                                onToggleLike = { viewModel.toggleLike(song.id) },
                                onAddToPlaylist = { viewModel.requestAddToPlaylist(song) },
                                onShowInfo = { viewModel.showSongInfo(song) }
                            )
                            RowDivider()
                        }
                    }
                }

                5 -> { // Folders
                    items(folders.entries.toList(), key = { "folder_${it.key}" }) { (folder, list) ->
                        GroupCollectionRow(
                            title = folder,
                            subtitle = "${list.size} ${if (list.size == 1) "song" else "songs"}",
                            count = list.size,
                            icon = Icons.Default.Folder,
                            onClick = { selectedGroupKey = folder }
                        )
                        RowDivider()
                    }
                }

                6 -> { // Genres
                    items(genres.entries.toList(), key = { "genre_${it.key}" }) { (genre, list) ->
                        GroupCollectionRow(
                            title = genre,
                            subtitle = "${list.size} ${if (list.size == 1) "song" else "songs"}",
                            count = list.size,
                            icon = Icons.Default.Category,
                            onClick = { selectedGroupKey = genre }
                        )
                        RowDivider()
                    }
                }

                7 -> { // Years
                    val sortedYears = years.entries.sortedByDescending { it.key }
                    if (sortedYears.isEmpty()) {
                        item(key = "years_empty") {
                            LibraryEmptyState(
                                icon = Icons.Default.CalendarMonth,
                                title = "No Years Found",
                                subtitle = "Songs with year metadata appear here."
                            )
                        }
                    } else {
                        items(sortedYears, key = { "year_${it.key}" }) { (year, list) ->
                            GroupCollectionRow(
                                title = year,
                                subtitle = "${list.size} ${if (list.size == 1) "song" else "songs"}",
                                count = list.size,
                                icon = Icons.Default.CalendarMonth,
                                onClick = { selectedGroupKey = year }
                            )
                            RowDivider()
                        }
                    }
                }
            }
        }
    }

    // --- Playlist Dialogs ---
    if (showCreatePlaylistDialog) {
        var nameInput by remember { mutableStateOf("") }
        GlassAlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_playlist_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            viewModel.createPlaylist(nameInput)
                            showCreatePlaylistDialog = false
                        }
                    },
                    enabled = nameInput.isNotBlank(),
                    modifier = Modifier.testTag("confirm_create_playlist")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancel") }
            }
        )
    }

    playlistToRename?.let { target ->
        var renameInput by remember(target.id) { mutableStateOf(target.name) }
        GlassAlertDialog(
            onDismissRequest = { playlistToRename = null },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            viewModel.renamePlaylist(target.id, renameInput)
                            playlistToRename = null
                        }
                    },
                    enabled = renameInput.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRename = null }) { Text("Cancel") }
            }
        )
    }

    playlistToDelete?.let { target ->
        GlassAlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete \"${target.name}\"?") },
            text = { Text("Your audio files will remain on your device.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePlaylist(target.id)
                        playlistToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) { Text("Cancel") }
            }
        )
    }

    playlistForAddingSongs?.let { target ->
        val selectedIds = remember(target.id) { mutableStateListOf<Long>() }
        val availableSongs = remember(songs, target.songIds) {
            songs.filter { it.id !in target.songIds }
        }
        GlassAlertDialog(
            onDismissRequest = { playlistForAddingSongs = null },
            title = { Text("Add to ${target.name}") },
            text = {
                if (availableSongs.isEmpty()) {
                    Text(
                        "All songs are already in this playlist.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        items(availableSongs, key = { "pick_${it.id}" }) { song ->
                            val checked = song.id in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (checked) selectedIds.remove(song.id) else selectedIds.add(song.id)
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        if (it) selectedIds.add(song.id) else selectedIds.remove(song.id)
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addSongsToPlaylist(target.id, selectedIds.toList())
                        playlistForAddingSongs = null
                    },
                    enabled = selectedIds.isNotEmpty()
                ) {
                    Text("Add (${selectedIds.size})")
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistForAddingSongs = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 84.dp, end = 20.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun SortAndPlayToolbar(
    sortType: String,
    sortAscending: Boolean,
    trackCount: Int,
    onSortSelected: (String) -> Unit,
    onToggleDirection: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Column {
        if (trackCount > 0) {
            PlayAllShuffleRow(onPlayAll = onPlayAll, onShuffleAll = onShuffleAll)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                TextButton(onClick = { showSortMenu = true }) {
                    Text(
                        text = "Sorted by $sortType",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showSortMenu) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = { showSortMenu = false },
                        containerColor = glassMenuContainerColor()
                    ) {
                        listOf("Name", "Artist", "Duration", "Recent").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    onSortSelected(option)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onToggleDirection,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = "Sort order",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

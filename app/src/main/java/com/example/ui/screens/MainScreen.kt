package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.Song
import com.example.ui.MusicViewModel
import com.example.ui.components.TrackArtwork
import com.example.ui.components.TrackInfoBottomSheet
import com.example.ui.components.shareTrackIntent
import com.example.ui.theme.DynamicGlassBackdrop
import com.example.ui.theme.FrostedGlassContainer
import com.example.ui.theme.GlassAlertDialog
import com.example.ui.theme.GlassLevel
import com.example.ui.theme.LocalGlassEnabled
import com.example.ui.theme.VragTheme
import com.example.ui.theme.glassScreenBackground
import com.example.ui.theme.liquidGlass
import com.example.ui.theme.rememberSongAlbumPalette

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object Library : Screen("library")
    data object Profile : Screen("profile")
    data object Settings : Screen("settings")
    data object Player : Screen("player")
    data object Liked : Screen("liked")
    data object Equalizer : Screen("equalizer")
    data object SleepTimer : Screen("sleeptimer")
    data object Queue : Screen("queue")
}

private data class TopNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun MainScreen(
    viewModel: MusicViewModel,
    isInPipMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val isLoadingLibrary by viewModel.isLoadingLibrary.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val showCovers by viewModel.showCovers.collectAsState()
    val glassmorphismEnabled by viewModel.glassmorphismEnabled.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val albumPalette = rememberSongAlbumPalette(
        song = currentSong ?: songs.firstOrNull(),
        showCovers = showCovers,
        fallback = accentColor
    )

    val snackbarHostState = remember { SnackbarHostState() }
    var pendingLibraryTab by remember { mutableIntStateOf(0) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.loadMedia(includeBuiltInDemoIfEmpty = true)
    }

    val importAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importAudioFiles(uris)
        }
    }

    val launchAudioImport = {
        importAudioLauncher.launch(arrayOf("audio/*"))
    }

    LaunchedEffect(Unit) {
        viewModel.initializeController(context)
        val neededPermissions = buildList {
            val audioPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            if (ContextCompat.checkSelfPermission(context, audioPerm) != PackageManager.PERMISSION_GRANTED) {
                add(audioPerm)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (neededPermissions.isNotEmpty()) {
            permissionsLauncher.launch(neededPermissions.toTypedArray())
        }
        viewModel.loadMedia(includeBuiltInDemoIfEmpty = true)
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeStatusMessage()
        }
    }

    VragTheme(
        themeMode = themeMode,
        accentColor = accentColor,
        glassmorphismEnabled = glassmorphismEnabled,
        albumPalette = albumPalette
    ) {
        if (isInPipMode) {
            PipCompactPlayer(
                song = currentSong,
                isPlaying = isPlaying,
                showCovers = showCovers,
                onPrevious = { viewModel.playPrevious() },
                onPlayPause = { viewModel.togglePlayback() },
                onNext = { viewModel.playNext() }
            )
            return@VragTheme
        }

        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val isPlayerScreen = currentRoute == Screen.Player.route

        val songToAddToPlaylist by viewModel.songToAddToPlaylist.collectAsState()
        val songInfoTarget by viewModel.songInfoTarget.collectAsState()
        val playlists by viewModel.playlists.collectAsState()

        val topNavItems = remember {
            listOf(
                TopNavItem(Screen.Home.route, "Listen", Icons.Filled.Home, Icons.Outlined.Home),
                TopNavItem(Screen.Search.route, "Search", Icons.Filled.Search, Icons.Outlined.Search),
                TopNavItem(Screen.Library.route, "Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
                TopNavItem(Screen.Profile.route, "Stats", Icons.Filled.BarChart, Icons.Outlined.BarChart)
            )
        }

        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp && !isPlayerScreen

            // Reactive, hardware-accelerated dynamic album-art mesh backdrop behind the entire app
            DynamicGlassBackdrop(
                palette = albumPalette,
                isPlaying = isPlaying,
                themeMode = themeMode,
                modifier = Modifier.fillMaxSize()
            )

            Row(modifier = Modifier.fillMaxSize()) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = if (glassmorphismEnabled) {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.52f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        topNavItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTopTab(navController, item.route) },
                                icon = {
                                    Icon(
                                        if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = glassScreenBackground(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        if (!isPlayerScreen) {
                            Column(
                                modifier = Modifier.background(
                                    if (glassmorphismEnabled) Color.Transparent
                                    else MaterialTheme.colorScheme.background
                                )
                            ) {
                                AnimatedVisibility(
                                    visible = currentSong != null,
                                    enter = slideInVertically(
                                        StudioMotion.emphasizedEnterSpec(StudioMotion.STANDARD_MS)
                                    ) { it } + fadeIn(
                                        StudioMotion.emphasizedEnterSpec(StudioMotion.FAST_MS)
                                    ),
                                    exit = slideOutVertically(
                                        StudioMotion.emphasizedExitSpec(StudioMotion.FAST_MS)
                                    ) { it } + fadeOut(
                                        StudioMotion.emphasizedExitSpec(StudioMotion.MICRO_MS)
                                    )
                                ) {
                                    currentSong?.let { song ->
                                        MiniPlayer(
                                            song = song,
                                            isPlaying = isPlaying,
                                            showCovers = showCovers,
                                            mediaControllerProvider = { viewModel.mediaController },
                                            onPlayPauseToggle = { viewModel.togglePlayback() },
                                            onNext = { viewModel.playNext() },
                                            onPrevious = { viewModel.playPrevious() },
                                            onPlayerClick = { navController.navigate(Screen.Player.route) }
                                        )
                                    }
                                }

                                if (!useNavRail) {
                                    VragBottomBar(
                                        items = topNavItems,
                                        currentRoute = currentRoute,
                                        onSelect = { route -> navigateToTopTab(navController, route) }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    if (isLoadingLibrary && songs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Home.route,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            enterTransition = {
                                fadeIn(StudioMotion.standardSpec(StudioMotion.FAST_MS)) +
                                    scaleIn(StudioMotion.standardSpec(StudioMotion.FAST_MS), initialScale = 0.985f)
                            },
                            exitTransition = {
                                fadeOut(StudioMotion.standardSpec(StudioMotion.MICRO_MS))
                            },
                            popEnterTransition = {
                                fadeIn(StudioMotion.standardSpec(StudioMotion.FAST_MS)) +
                                    scaleIn(StudioMotion.standardSpec(StudioMotion.FAST_MS), initialScale = 0.985f)
                            },
                            popExitTransition = {
                                fadeOut(StudioMotion.standardSpec(StudioMotion.MICRO_MS))
                            }
                        ) {
                            composable(Screen.Home.route) {
                                HomeContent(
                                    songs = songs,
                                    currentSong = currentSong,
                                    isPlaying = isPlaying,
                                    viewModel = viewModel,
                                    onSongClick = { song, list, source ->
                                        viewModel.playFromList(song, list, source)
                                        navController.navigate(Screen.Player.route)
                                    },
                                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                                    onNavigateToEqualizer = { navController.navigate(Screen.Equalizer.route) },
                                    onNavigateToLibrary = { navigateToTopTab(navController, Screen.Library.route) },
                                    onNavigateToPlaylists = {
                                        pendingLibraryTab = 1
                                        navigateToTopTab(navController, Screen.Library.route)
                                    },
                                    onNavigateToPlayer = { navController.navigate(Screen.Player.route) },
                                    onNavigateToLiked = { navController.navigate(Screen.Liked.route) }
                                )
                            }

                            composable(Screen.Search.route) {
                                SearchContent(
                                    songs = songs,
                                    viewModel = viewModel,
                                    onSongClick = { song, list, source ->
                                        viewModel.playFromList(song, list, source)
                                        navController.navigate(Screen.Player.route)
                                    }
                                )
                            }

                            composable(Screen.Library.route) {
                                LibraryContent(
                                    songs = songs,
                                    title = "Library",
                                    viewModel = viewModel,
                                    initialTabIndex = pendingLibraryTab,
                                    onInitialTabConsumed = { pendingLibraryTab = 0 },
                                    onPlayFromList = { song, list, source, playlistId ->
                                        viewModel.playFromList(song, list, source, playlistId)
                                        navController.navigate(Screen.Player.route)
                                    },
                                    onImportAudio = launchAudioImport,
                                    onNavigateToPlayer = { navController.navigate(Screen.Player.route) }
                                )
                            }

                            composable(
                                route = Screen.Liked.route,
                                enterTransition = {
                                    slideInHorizontally(StudioMotion.emphasizedEnterSpec(StudioMotion.STANDARD_MS)) { it / 5 } +
                                        fadeIn(StudioMotion.emphasizedEnterSpec(StudioMotion.FAST_MS))
                                },
                                exitTransition = { fadeOut(StudioMotion.standardSpec(StudioMotion.MICRO_MS)) },
                                popEnterTransition = { fadeIn(StudioMotion.standardSpec(StudioMotion.FAST_MS)) },
                                popExitTransition = {
                                    slideOutHorizontally(StudioMotion.emphasizedExitSpec(StudioMotion.FAST_MS)) { it / 5 } +
                                        fadeOut(StudioMotion.emphasizedExitSpec(StudioMotion.MICRO_MS))
                                }
                            ) {
                                val likedIds by viewModel.likedSongs.collectAsState()
                                val favSongs = remember(songs, likedIds) { songs.filter { it.id in likedIds } }
                                LibraryContent(
                                    songs = favSongs,
                                    title = "Favorites",
                                    viewModel = viewModel,
                                    isLikedSection = true,
                                    onBack = { navController.popBackStack() },
                                    onPlayFromList = { song, list, source, playlistId ->
                                        viewModel.playFromList(song, list, source, playlistId)
                                        navController.navigate(Screen.Player.route)
                                    },
                                    onImportAudio = launchAudioImport,
                                    onNavigateToPlayer = { navController.navigate(Screen.Player.route) }
                                )
                            }

                            composable(Screen.Profile.route) {
                                StatsContent(
                                    songs = songs,
                                    viewModel = viewModel,
                                    onSongClick = { song, list, source ->
                                        viewModel.playFromList(song, list, source)
                                        navController.navigate(Screen.Player.route)
                                    }
                                )
                            }

                            composable(
                                route = Screen.Settings.route,
                                enterTransition = {
                                    slideInHorizontally(StudioMotion.emphasizedEnterSpec(StudioMotion.STANDARD_MS)) { it / 5 } +
                                        fadeIn(StudioMotion.emphasizedEnterSpec(StudioMotion.FAST_MS))
                                },
                                exitTransition = { fadeOut(StudioMotion.standardSpec(StudioMotion.MICRO_MS)) },
                                popEnterTransition = { fadeIn(StudioMotion.standardSpec(StudioMotion.FAST_MS)) },
                                popExitTransition = {
                                    slideOutHorizontally(StudioMotion.emphasizedExitSpec(StudioMotion.FAST_MS)) { it / 5 } +
                                        fadeOut(StudioMotion.emphasizedExitSpec(StudioMotion.MICRO_MS))
                                }
                            ) {
                                SettingsContent(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() },
                                    onNavigateToEqualizer = { navController.navigate(Screen.Equalizer.route) },
                                    onNavigateToSleepTimer = { navController.navigate(Screen.SleepTimer.route) },
                                    onImportAudio = launchAudioImport
                                )
                            }

                            composable(
                                route = Screen.Equalizer.route,
                                enterTransition = {
                                    slideInHorizontally(StudioMotion.emphasizedEnterSpec(StudioMotion.STANDARD_MS)) { it / 5 } +
                                        fadeIn(StudioMotion.emphasizedEnterSpec(StudioMotion.FAST_MS))
                                },
                                exitTransition = { fadeOut(StudioMotion.standardSpec(StudioMotion.MICRO_MS)) },
                                popEnterTransition = { fadeIn(StudioMotion.standardSpec(StudioMotion.FAST_MS)) },
                                popExitTransition = {
                                    slideOutHorizontally(StudioMotion.emphasizedExitSpec(StudioMotion.FAST_MS)) { it / 5 } +
                                        fadeOut(StudioMotion.emphasizedExitSpec(StudioMotion.MICRO_MS))
                                }
                            ) {
                                EqualizerContent(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(
                                route = Screen.SleepTimer.route,
                                enterTransition = {
                                    slideInHorizontally(StudioMotion.emphasizedEnterSpec(StudioMotion.STANDARD_MS)) { it / 5 } +
                                        fadeIn(StudioMotion.emphasizedEnterSpec(StudioMotion.FAST_MS))
                                },
                                exitTransition = { fadeOut(StudioMotion.standardSpec(StudioMotion.MICRO_MS)) },
                                popEnterTransition = { fadeIn(StudioMotion.standardSpec(StudioMotion.FAST_MS)) },
                                popExitTransition = {
                                    slideOutHorizontally(StudioMotion.emphasizedExitSpec(StudioMotion.FAST_MS)) { it / 5 } +
                                        fadeOut(StudioMotion.emphasizedExitSpec(StudioMotion.MICRO_MS))
                                }
                            ) {
                                SleepTimerContent(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(
                                route = Screen.Player.route,
                                enterTransition = {
                                    slideInVertically(StudioMotion.emphasizedEnterSpec()) { (it * 0.24f).toInt() } +
                                        scaleIn(StudioMotion.emphasizedEnterSpec(), initialScale = 0.93f) +
                                        fadeIn(StudioMotion.emphasizedEnterSpec(240))
                                },
                                exitTransition = {
                                    slideOutVertically(StudioMotion.emphasizedExitSpec()) { (it * 0.24f).toInt() } +
                                        scaleOut(StudioMotion.emphasizedExitSpec(), targetScale = 0.94f) +
                                        fadeOut(StudioMotion.emphasizedExitSpec(180))
                                },
                                popEnterTransition = {
                                    fadeIn(StudioMotion.standardSpec(StudioMotion.FAST_MS))
                                },
                                popExitTransition = {
                                    slideOutVertically(StudioMotion.emphasizedExitSpec()) { (it * 0.24f).toInt() } +
                                        scaleOut(StudioMotion.emphasizedExitSpec(), targetScale = 0.94f) +
                                        fadeOut(StudioMotion.emphasizedExitSpec(180))
                                }
                            ) {
                                val likedSongs by viewModel.likedSongs.collectAsState()
                                val repeatMode by viewModel.repeatMode.collectAsState()
                                val shuffleModeEnabled by viewModel.shuffleModeEnabled.collectAsState()
                                val playbackSpeed by viewModel.playbackSpeed.collectAsState()
                                val queueSource by viewModel.activeQueueSource.collectAsState()
                                val sleepRemaining by viewModel.sleepTimerRemainingMs.collectAsState()
                                val sleepAfterTrack by viewModel.sleepAfterCurrentTrack.collectAsState()

                                PlayerContent(
                                    song = currentSong,
                                    isPlaying = isPlaying,
                                    queueSource = queueSource,
                                    isLiked = currentSong?.let { it.id in likedSongs } == true,
                                    repeatMode = repeatMode,
                                    shuffleModeEnabled = shuffleModeEnabled,
                                    playbackSpeed = playbackSpeed,
                                    showCovers = showCovers,
                                    sleepTimerRemainingMs = sleepRemaining,
                                    sleepAfterCurrentTrack = sleepAfterTrack,
                                    mediaControllerProvider = { viewModel.mediaController },
                                    onPlayPauseToggle = { viewModel.togglePlayback() },
                                    onNext = { viewModel.playNext() },
                                    onPrevious = { viewModel.playPrevious() },
                                    onSeek = { viewModel.seekTo(it) },
                                    onLikeToggle = { currentSong?.let { viewModel.toggleLike(it.id) } },
                                    onRepeatToggle = { viewModel.toggleRepeatMode() },
                                    onShuffleToggle = { viewModel.toggleShuffleMode() },
                                    onSpeedChange = { viewModel.setPlaybackSpeed(it) },
                                    onAddToPlaylist = { currentSong?.let { viewModel.requestAddToPlaylist(it) } },
                                    onShowTrackInfo = { currentSong?.let { viewModel.showSongInfo(it) } },
                                    onNavigateToQueue = { navController.navigate(Screen.Queue.route) },
                                    onNavigateToEqualizer = { navController.navigate(Screen.Equalizer.route) },
                                    onNavigateToSleepTimer = { navController.navigate(Screen.SleepTimer.route) },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(
                                route = Screen.Queue.route,
                                enterTransition = {
                                    slideInHorizontally(StudioMotion.emphasizedEnterSpec(StudioMotion.STANDARD_MS)) { it / 5 } +
                                        fadeIn(StudioMotion.emphasizedEnterSpec(StudioMotion.FAST_MS))
                                },
                                exitTransition = { fadeOut(StudioMotion.standardSpec(StudioMotion.MICRO_MS)) },
                                popEnterTransition = { fadeIn(StudioMotion.standardSpec(StudioMotion.FAST_MS)) },
                                popExitTransition = {
                                    slideOutHorizontally(StudioMotion.emphasizedExitSpec(StudioMotion.FAST_MS)) { it / 5 } +
                                        fadeOut(StudioMotion.emphasizedExitSpec(StudioMotion.MICRO_MS))
                                }
                            ) {
                                val queueSongs by viewModel.queue.collectAsState()
                                val queueSource by viewModel.activeQueueSource.collectAsState()
                                QueueContent(
                                    songs = queueSongs,
                                    currentSong = currentSong,
                                    isPlaying = isPlaying,
                                    queueSource = queueSource,
                                    showCovers = showCovers,
                                    onSongClick = { index, _ -> viewModel.playSongAtIndex(index) },
                                    onMoveQueueItem = { from, to -> viewModel.moveQueueItem(from, to) },
                                    onRemoveQueueItem = { idx -> viewModel.removeQueueItem(idx) },
                                    onClearUpcoming = { viewModel.clearUpcomingQueue() },
                                    onSaveAsPlaylist = { name -> viewModel.saveQueueAsPlaylist(name) },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Global Add-to-Playlist Dialog
        songToAddToPlaylist?.let { targetSong ->
            var showNewInput by remember { mutableStateOf(false) }
            var newName by remember { mutableStateOf("") }

            GlassAlertDialog(
                onDismissRequest = { viewModel.requestAddToPlaylist(null) },
                title = { Text("Add to Playlist") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = targetSong.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (playlists.isEmpty() && !showNewInput) {
                            Text(
                                text = "Create a playlist below to add this song.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (playlists.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(playlists, key = { it.id }) { playlist ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .liquidGlass(
                                                shape = RoundedCornerShape(12.dp),
                                                level = GlassLevel.Subtle
                                            )
                                            .clickable {
                                                viewModel.addSongToPlaylist(playlist.id, targetSong.id)
                                                viewModel.requestAddToPlaylist(null)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = playlist.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "${playlist.songIds.size}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (showNewInput) {
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                label = { Text("Playlist name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    if (showNewInput) {
                        Button(
                            onClick = {
                                val created = viewModel.createPlaylist(newName, listOf(targetSong.id))
                                if (created != null) {
                                    viewModel.requestAddToPlaylist(null)
                                }
                            },
                            enabled = newName.isNotBlank()
                        ) {
                            Text("Create & Add")
                        }
                    } else {
                        Button(onClick = { showNewInput = true }) {
                            Text("New Playlist")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            if (showNewInput) showNewInput = false
                            else viewModel.requestAddToPlaylist(null)
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Global Song Info Sheet
        songInfoTarget?.let { infoSong ->
            TrackInfoBottomSheet(
                song = infoSong,
                showCovers = showCovers,
                onDismiss = { viewModel.showSongInfo(null) },
                onShare = { shareTrackIntent(context, infoSong) }
            )
        }
    }
}

/**
 * Slim, flagship-grade Bottom Navigation Bar:
 * - No bulky 80dp slab or clunky Material3 oval indicator pills
 * - Crisp 0.5dp top hairline separator + safe navigationBarsPadding()
 * - Generous 48dp+ touch targets with filled/outlined iconography and clean label contrast
 */
@Composable
private fun VragBottomBar(
    items: List<TopNavItem>,
    currentRoute: String?,
    onSelect: (String) -> Unit
) {
    val glassEnabled = LocalGlassEnabled.current

    val navRowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                val tint = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                val interactionSource = remember { MutableInteractionSource() }
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.08f else 1f,
                    animationSpec = tween(StudioMotion.FAST_MS, easing = StudioMotion.TACTILE_SNAP),
                    label = "nav_icon_scale_${item.route}"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(bounded = false, radius = 32.dp),
                            onClick = { onSelect(item.route) }
                        )
                        .testTag("nav_${item.route}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        tint = if (selected) MaterialTheme.colorScheme.primary else tint,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            }
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else tint,
                        maxLines = 1
                    )
                }
            }
        }
    }

    if (glassEnabled) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 8.dp)
        ) {
            FrostedGlassContainer(
                shape = RoundedCornerShape(24.dp),
                level = GlassLevel.Floating,
                blurRadius = 28.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                navRowContent()
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
        ) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            navRowContent()
        }
    }
}

private fun navigateToTopTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(Screen.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun PipCompactPlayer(
    song: Song?,
    isPlaying: Boolean,
    showCovers: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TrackArtwork(
                song = song,
                showCovers = showCovers,
                sizePx = 112,
                iconSize = 20.dp,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxHeight(0.85f)
                    .size(52.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song?.title ?: "VragPlayer",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song?.artist ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onBackground)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play or pause",
                            tint = MaterialTheme.colorScheme.background,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

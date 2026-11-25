package com.example.ui

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.imageLoader
import com.example.data.ListeningStats
import com.example.data.MediaRepository
import com.example.data.Song
import com.example.data.StatsRepository
import com.example.service.PlaybackService
import com.example.ui.theme.AccentCrimson
import com.example.ui.theme.ThemeMode
import com.example.util.DevicePerf
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Immutable
data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val songIds: List<Long> = emptyList()
)

enum class EqualizerPreset(val label: String, val bands: List<Float>) {
    FLAT("Flat", listOf(0f, 0f, 0f, 0f, 0f)),
    BASS_BOOST("Bass", listOf(8f, 5f, 1f, 0f, -1f)),
    TREBLE_BOOST("Treble", listOf(-1f, 0f, 2f, 6f, 9f)),
    VOCAL("Vocal", listOf(-2f, 1f, 7f, 4f, 1f)),
    ACOUSTIC("Acoustic", listOf(3f, 2f, 3f, 4f, 2f)),
    ELECTRONIC("Electronic", listOf(6f, 3f, -1f, 4f, 7f)),
    LATE_NIGHT("Late Night", listOf(2f, 1f, -1f, -2f, -3f));

    companion object {
        fun fromLabel(label: String): EqualizerPreset? = entries.find { it.label == label }
    }
}

private data class PendingPlayRequest(
    val song: Song,
    val sourceList: List<Song>,
    val sourceLabel: String,
    val playlistId: String?,
    val shuffle: Boolean
)

@Stable
class MusicViewModel(
    private val mediaRepository: MediaRepository,
    private val statsRepository: StatsRepository
) : ViewModel() {

    // Library & Queue
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _isLoadingLibrary = MutableStateFlow(true)
    val isLoadingLibrary: StateFlow<Boolean> = _isLoadingLibrary.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _activeQueueSource = MutableStateFlow("Library")
    val activeQueueSource: StateFlow<String> = _activeQueueSource.asStateFlow()

    private val _activePlaylistId = MutableStateFlow<String?>(null)
    val activePlaylistId: StateFlow<String?> = _activePlaylistId.asStateFlow()

    // Active Playback State
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // 0: Off, 1: All, 2: One
    private val _repeatMode = MutableStateFlow(0)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // User Collections
    private val _likedSongs = MutableStateFlow<Set<Long>>(emptySet())
    val likedSongs: StateFlow<Set<Long>> = _likedSongs.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _recentlyPlayed = MutableStateFlow<List<Song>>(emptyList())
    val recentlyPlayed: StateFlow<List<Song>> = _recentlyPlayed.asStateFlow()

    private val _songToAddToPlaylist = MutableStateFlow<Song?>(null)
    val songToAddToPlaylist: StateFlow<Song?> = _songToAddToPlaylist.asStateFlow()

    private val _songInfoTarget = MutableStateFlow<Song?>(null)
    val songInfoTarget: StateFlow<Song?> = _songInfoTarget.asStateFlow()

    // Search
    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _searchPlayedIds = MutableStateFlow<List<Long>>(emptyList())
    val searchPlayedIds: StateFlow<List<Long>> = _searchPlayedIds.asStateFlow()

    // Sorting
    private val _sortType = MutableStateFlow("Name")
    val sortType: StateFlow<String> = _sortType.asStateFlow()

    private val _sortAscending = MutableStateFlow(true)
    val sortAscending: StateFlow<Boolean> = _sortAscending.asStateFlow()

    // Audio DSP & Sleep Timer
    private val _eqEnabled = MutableStateFlow(false)
    val eqEnabled: StateFlow<Boolean> = _eqEnabled.asStateFlow()

    private val _equalizerPreset = MutableStateFlow("Flat")
    val equalizerPreset: StateFlow<String> = _equalizerPreset.asStateFlow()

    private val _equalizerBands = MutableStateFlow(listOf(0f, 0f, 0f, 0f, 0f))
    val equalizerBands: StateFlow<List<Float>> = _equalizerBands.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(0)
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(0)
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    private val _skipSilence = MutableStateFlow(false)
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()

    private val _sleepTimerRemainingMs = MutableStateFlow(0L)
    val sleepTimerRemainingMs: StateFlow<Long> = _sleepTimerRemainingMs.asStateFlow()

    private val _sleepAfterCurrentTrack = MutableStateFlow(false)
    val sleepAfterCurrentTrack: StateFlow<Boolean> = _sleepAfterCurrentTrack.asStateFlow()

    // Appearance & Preferences
    private val _themeMode = MutableStateFlow(ThemeMode.DARK_STUDIO)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _accentColor = MutableStateFlow(AccentCrimson)
    val accentColor: StateFlow<Color> = _accentColor.asStateFlow()

    private val _showCovers = MutableStateFlow(true)
    val showCovers: StateFlow<Boolean> = _showCovers.asStateFlow()

    private val _glassmorphismEnabled = MutableStateFlow(!DevicePerf.isLowEnd)
    val glassmorphismEnabled: StateFlow<Boolean> = _glassmorphismEnabled.asStateFlow()

    private val _pipEnabled = MutableStateFlow(false)
    val pipEnabled: StateFlow<Boolean> = _pipEnabled.asStateFlow()

    private val _username = MutableStateFlow("My Music")
    val username: StateFlow<String> = _username.asStateFlow()

    val stats: StateFlow<ListeningStats> = statsRepository.stats

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    private var sharedPrefs: SharedPreferences? = null
    private var sleepTimerJob: Job? = null
    private var queueSyncJob: Job? = null
    private var pendingPlayRequest: PendingPlayRequest? = null

    init {
        // Lightweight play-time ticker — StatsRepository buffers seconds in memory so zero 1Hz UI recomposition
        viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                if (_isPlaying.value) {
                    statsRepository.addPlaySecond()
                }
            }
        }
    }

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }

    fun showMessage(msg: String) {
        _statusMessage.value = msg
    }

    fun initializeController(context: Context) {
        val appContext = context.applicationContext
        if (sharedPrefs == null) {
            val prefs = appContext.getSharedPreferences("vrag_prefs", Context.MODE_PRIVATE)
            sharedPrefs = prefs
            loadPreferences(prefs)
        }

        if (mediaControllerFuture != null) return

        try {
            val sessionToken = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
            mediaControllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()
            mediaControllerFuture?.addListener({
                try {
                    val controller = mediaControllerFuture?.get() ?: return@addListener
                    mediaController = controller
                    attachPlayerListener(controller)
                    val pending = pendingPlayRequest
                    if (pending != null) {
                        pendingPlayRequest = null
                        playFromList(
                            song = pending.song,
                            sourceList = pending.sourceList,
                            sourceLabel = pending.sourceLabel,
                            playlistId = pending.playlistId,
                            shuffle = pending.shuffle
                        )
                    } else {
                        syncFromExistingController(controller)
                    }
                } catch (_: Throwable) {
                    mediaControllerFuture = null
                }
            }, MoreExecutors.directExecutor())
        } catch (_: Throwable) {
            mediaControllerFuture = null
        }
    }

    private fun loadPreferences(prefs: SharedPreferences) {
        val likes = prefs.getStringSet("liked_songs", emptySet()) ?: emptySet()
        _likedSongs.value = likes.mapNotNull { it.toLongOrNull() }.toSet()

        _username.value = prefs.getString("profile_username", "My Music") ?: "My Music"
        _showCovers.value = prefs.getBoolean("settings_show_covers", true)
        _glassmorphismEnabled.value = prefs.getBoolean("settings_glassmorphism", !DevicePerf.isLowEnd)
        _pipEnabled.value = prefs.getBoolean("settings_pip", false)
        _themeMode.value = ThemeMode.fromKey(prefs.getString("theme_mode", ThemeMode.DARK_STUDIO.key))

        val savedAccentHex = prefs.getString("theme_accent_hex", "#FA2D48") ?: "#FA2D48"
        try {
            _accentColor.value = Color(android.graphics.Color.parseColor(savedAccentHex))
        } catch (_: Exception) {
            _accentColor.value = AccentCrimson
        }

        _eqEnabled.value = prefs.getBoolean("eq_enabled", false)
        _equalizerPreset.value = prefs.getString("equalizer_preset", "Flat") ?: "Flat"
        val savedBands = prefs.getString("equalizer_bands", "0,0,0,0,0") ?: "0,0,0,0,0"
        val parsedBands = savedBands.split(",").map { it.toFloatOrNull() ?: 0f }
        _equalizerBands.value = if (parsedBands.size == 5) parsedBands else listOf(0f, 0f, 0f, 0f, 0f)
        _bassBoostStrength.value = prefs.getInt("bass_boost_strength", 0).coerceIn(0, 1000)
        _crossfadeSeconds.value = prefs.getInt("crossfade_seconds", 0).coerceIn(0, 8)
        _skipSilence.value = prefs.getBoolean("skip_silence", false)

        val searches = prefs.getString("recent_searches", "") ?: ""
        if (searches.isNotEmpty()) {
            _recentSearches.value = searches.split("||").filter { it.isNotBlank() }
        }

        val searchPlayed = prefs.getString("search_played_song_ids", "") ?: ""
        if (searchPlayed.isNotEmpty()) {
            _searchPlayedIds.value = searchPlayed.split(",").mapNotNull { it.toLongOrNull() }
        }

        loadPlaylists()
    }

    private fun attachPlayerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (!playing) {
                    statsRepository.flushPlayTime()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    _isPlaying.value = false
                    statsRepository.flushPlayTime()
                    if (_sleepAfterCurrentTrack.value) {
                        _sleepAfterCurrentTrack.value = false
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                showMessage("Unable to play this audio file")
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = when (repeatMode) {
                    Player.REPEAT_MODE_ALL -> 1
                    Player.REPEAT_MODE_ONE -> 2
                    else -> 0
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffleModeEnabled.value = shuffleModeEnabled
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (_sleepAfterCurrentTrack.value && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    _sleepAfterCurrentTrack.value = false
                    safeController { it.pause() }
                    _isPlaying.value = false
                    showMessage("Paused at end of track")
                }

                val songId = mediaItem?.mediaId?.toLongOrNull() ?: return
                val matchedSong = _queue.value.find { it.id == songId }
                    ?: _songs.value.find { it.id == songId }
                if (matchedSong != null && _currentSong.value?.id != matchedSong.id) {
                    recordCurrentSongChange(matchedSong)
                }
            }
        })
    }

    private fun syncFromExistingController(controller: MediaController) {
        try {
            _isPlaying.value = controller.isPlaying
            _shuffleModeEnabled.value = controller.shuffleModeEnabled
            _repeatMode.value = when (controller.repeatMode) {
                Player.REPEAT_MODE_ALL -> 1
                Player.REPEAT_MODE_ONE -> 2
                else -> 0
            }
            _playbackSpeed.value = controller.playbackParameters.speed

            val activeId = controller.currentMediaItem?.mediaId?.toLongOrNull()
            if (activeId != null) {
                _songs.value.find { it.id == activeId }?.let { _currentSong.value = it }
            }
            val count = controller.mediaItemCount
            if (count > 0 && _songs.value.isNotEmpty()) {
                val songMap = _songs.value.associateBy { it.id }
                val restoredQueue = (0 until count).mapNotNull { idx ->
                    val id = controller.getMediaItemAt(idx).mediaId.toLongOrNull()
                    if (id != null) songMap[id] else null
                }
                if (restoredQueue.isNotEmpty()) {
                    _queue.value = restoredQueue
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun loadMedia(includeBuiltInDemoIfEmpty: Boolean = true) {
        viewModelScope.launch {
            _isLoadingLibrary.value = true
            val audioFiles = mediaRepository.getAudioFiles(includeBuiltInDemoIfEmpty)
            val sorted = withContext(Dispatchers.Default) {
                sortSongsList(audioFiles, _sortType.value, _sortAscending.value)
            }
            _songs.value = sorted

            if (_queue.value.isEmpty() && sorted.isNotEmpty()) {
                _queue.value = sorted
                _activeQueueSource.value = "Library"
            }

            try {
                mediaController?.currentMediaItem?.mediaId?.toLongOrNull()?.let { activeId ->
                    sorted.find { it.id == activeId }?.let { _currentSong.value = it }
                }
            } catch (_: Throwable) {
            }

            val savedRecents = sharedPrefs?.getString("settings_recently_played", "") ?: ""
            if (savedRecents.isNotEmpty()) {
                val songMap = sorted.associateBy { it.id }
                val recentsList = savedRecents.split(",")
                    .mapNotNull { it.toLongOrNull() }
                    .mapNotNull { songMap[it] }
                _recentlyPlayed.value = if (recentsList.isNotEmpty()) recentsList else sorted.take(6)
            } else {
                _recentlyPlayed.value = sorted.take(6)
            }
            _isLoadingLibrary.value = false
        }
    }

    fun importAudioFiles(uris: List<Uri>) {
        viewModelScope.launch {
            val added = mediaRepository.importAudioUris(uris)
            if (added > 0) {
                loadMedia(includeBuiltInDemoIfEmpty = true)
                showMessage("Imported $added ${if (added == 1) "song" else "songs"}")
            } else {
                showMessage("Already in your library")
            }
        }
    }

    /**
     * Ultra-lightweight MediaItem builder — NEVER attaches raw artwork ByteArrays so
     * Binder IPC stays tiny and NEVER throws TransactionTooLargeException.
     */
    private fun Song.toMediaItem(): MediaItem {
        val metaBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
        albumArtUri?.let { metaBuilder.setArtworkUri(it) }

        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(id.toString())
            .setMediaMetadata(metaBuilder.build())
            .build()
    }

    private inline fun safeController(block: (MediaController) -> Unit) {
        val controller = mediaController ?: return
        try {
            block(controller)
        } catch (_: Throwable) {
        }
    }

    /**
     * Plays the tapped song IMMEDIATELY (single tiny Binder call -> instant start)
     * and then syncs surrounding queue items in safe 80-item chunks so even 5,000-song
     * libraries NEVER lag or throw TransactionTooLargeException.
     */
    private fun pushQueueToControllerSafe(
        controller: MediaController,
        finalQueue: List<Song>,
        startIndex: Int
    ) {
        queueSyncJob?.cancel()
        val safeStart = startIndex.coerceIn(0, finalQueue.lastIndex)
        val startSong = finalQueue[safeStart]

        try {
            controller.shuffleModeEnabled = false
            _shuffleModeEnabled.value = false

            if (finalQueue.size <= BINDER_SAFE_CHUNK) {
                controller.setMediaItems(finalQueue.map { it.toMediaItem() }, safeStart, 0L)
                controller.prepare()
                controller.play()
                return
            }

            // Instant playback start for the selected track
            controller.setMediaItem(startSong.toMediaItem(), 0L)
            controller.prepare()
            controller.play()

            // Asynchronously populate the rest of the queue in tiny Binder-safe chunks
            val beforeSongs = finalQueue.subList(0, safeStart)
            val afterSongs = if (safeStart + 1 < finalQueue.size) {
                finalQueue.subList(safeStart + 1, finalQueue.size)
            } else emptyList()

            queueSyncJob = viewModelScope.launch(Dispatchers.Main) {
                var insertedBefore = 0
                for (chunk in beforeSongs.chunked(BINDER_SAFE_CHUNK)) {
                    if (!isActive) return@launch
                    val items = withContext(Dispatchers.Default) { chunk.map { it.toMediaItem() } }
                    try {
                        controller.addMediaItems(insertedBefore, items)
                        insertedBefore += items.size
                    } catch (_: Throwable) {
                        return@launch
                    }
                }
                for (chunk in afterSongs.chunked(BINDER_SAFE_CHUNK)) {
                    if (!isActive) return@launch
                    val items = withContext(Dispatchers.Default) { chunk.map { it.toMediaItem() } }
                    try {
                        controller.addMediaItems(items)
                    } catch (_: Throwable) {
                        return@launch
                    }
                }
            }
        } catch (_: Throwable) {
            showMessage("Could not start playback")
        }
    }

    // --- Single-Pass Playback & Strict Playlist Queue Isolation ---

    fun playFromList(
        song: Song,
        sourceList: List<Song>,
        sourceLabel: String = "Library",
        playlistId: String? = null,
        shuffle: Boolean = false
    ) {
        val cleanList = sourceList.ifEmpty { listOf(song) }
        val finalQueue = if (shuffle && cleanList.size > 1) {
            val rest = cleanList.filter { it.id != song.id }.shuffled()
            listOf(song) + rest
        } else {
            cleanList
        }
        val startIndex = finalQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

        _queue.value = finalQueue
        _activeQueueSource.value = sourceLabel
        _activePlaylistId.value = playlistId

        val controller = mediaController
        if (controller != null) {
            pushQueueToControllerSafe(controller, finalQueue, startIndex)
        } else {
            // Controller still binding — auto-start the instant it connects!
            pendingPlayRequest = PendingPlayRequest(song, finalQueue, sourceLabel, playlistId, false)
        }
        recordCurrentSongChange(song)
        _isPlaying.value = true
    }

    fun playPlaylistSequential(playlistId: String) {
        val playlist = _playlists.value.find { it.id == playlistId } ?: return
        val playlistSongs = resolvePlaylistSongs(playlist)
        if (playlistSongs.isEmpty()) {
            showMessage("\"${playlist.name}\" has no songs yet")
            return
        }
        playFromList(
            song = playlistSongs.first(),
            sourceList = playlistSongs,
            sourceLabel = playlist.name,
            playlistId = playlist.id,
            shuffle = false
        )
    }

    fun playPlaylistRandom(playlistId: String) {
        val playlist = _playlists.value.find { it.id == playlistId } ?: return
        val playlistSongs = resolvePlaylistSongs(playlist)
        if (playlistSongs.isEmpty()) {
            showMessage("\"${playlist.name}\" has no songs yet")
            return
        }
        val shuffled = playlistSongs.shuffled()
        playFromList(
            song = shuffled.first(),
            sourceList = shuffled,
            sourceLabel = playlist.name,
            playlistId = playlist.id,
            shuffle = false
        )
    }

    fun playAllShuffled(sourceList: List<Song> = _songs.value, sourceLabel: String = "Shuffled") {
        if (sourceList.isEmpty()) return
        val shuffled = sourceList.shuffled()
        playFromList(
            song = shuffled.first(),
            sourceList = shuffled,
            sourceLabel = sourceLabel,
            playlistId = null,
            shuffle = false
        )
    }

    fun playSongAtIndex(index: Int) {
        val currentQueue = _queue.value
        if (index !in currentQueue.indices) return
        val song = currentQueue[index]
        val controller = mediaController
        if (controller != null) {
            try {
                if (index < controller.mediaItemCount) {
                    controller.seekTo(index, 0L)
                    controller.prepare()
                    controller.play()
                } else {
                    pushQueueToControllerSafe(controller, currentQueue, index)
                }
            } catch (_: Throwable) {
            }
        }
        recordCurrentSongChange(song)
        _isPlaying.value = true
    }

    fun playNextInQueue(song: Song) {
        val currentQueue = _queue.value.toMutableList()
        val controller = mediaController
        val currentIndex = try {
            controller?.currentMediaItemIndex?.takeIf { it in currentQueue.indices }
        } catch (_: Throwable) { null }
            ?: currentQueue.indexOfFirst { it.id == _currentSong.value?.id }.coerceAtLeast(-1)

        val insertIndex = (currentIndex + 1).coerceIn(0, currentQueue.size)
        currentQueue.add(insertIndex, song)
        _queue.value = currentQueue
        safeController {
            val safePos = insertIndex.coerceIn(0, it.mediaItemCount)
            it.addMediaItem(safePos, song.toMediaItem())
        }
        showMessage("Playing \"${song.title}\" next")
    }

    fun addToQueue(song: Song) {
        _queue.value = _queue.value + song
        safeController { it.addMediaItem(song.toMediaItem()) }
        showMessage("Added to queue")
    }

    fun addListToQueue(list: List<Song>, label: String) {
        if (list.isEmpty()) return
        _queue.value = _queue.value + list
        safeController { controller ->
            list.chunked(BINDER_SAFE_CHUNK).forEach { chunk ->
                controller.addMediaItems(chunk.map { it.toMediaItem() })
            }
        }
        showMessage("Added $label to queue")
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val list = _queue.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _queue.value = list
        safeController {
            if (fromIndex < it.mediaItemCount && toIndex < it.mediaItemCount) {
                it.moveMediaItem(fromIndex, toIndex)
            }
        }
    }

    fun removeQueueItem(index: Int) {
        val list = _queue.value.toMutableList()
        if (index !in list.indices) return
        val removed = list.removeAt(index)
        _queue.value = list
        safeController {
            if (index < it.mediaItemCount) it.removeMediaItem(index)
        }
        if (list.isEmpty()) {
            _currentSong.value = null
            _isPlaying.value = false
        } else if (removed.id == _currentSong.value?.id) {
            val nextIdx = index.coerceAtMost(list.lastIndex)
            _currentSong.value = list[nextIdx]
        }
    }

    fun clearUpcomingQueue() {
        val current = _currentSong.value ?: return
        _queue.value = listOf(current)
        safeController { controller ->
            val currentIdx = controller.currentMediaItemIndex
            if (currentIdx >= 0 && controller.mediaItemCount > 1) {
                if (currentIdx + 1 < controller.mediaItemCount) {
                    controller.removeMediaItems(currentIdx + 1, controller.mediaItemCount)
                }
                if (currentIdx > 0) {
                    controller.removeMediaItems(0, currentIdx)
                }
            }
        }
        showMessage("Cleared upcoming songs")
    }

    fun saveQueueAsPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || _queue.value.isEmpty()) return
        val newPlaylist = Playlist(
            name = trimmed,
            songIds = _queue.value.map { it.id }.distinct()
        )
        _playlists.value = _playlists.value + newPlaylist
        savePlaylists()
        showMessage("Saved as \"$trimmed\"")
    }

    fun togglePlayback() {
        val controller = mediaController
        if (_currentSong.value == null) {
            val seed = _queue.value.ifEmpty { _songs.value }
            if (seed.isNotEmpty()) {
                playAllShuffled(seed, "Shuffled")
            }
            return
        }
        if (controller == null) {
            _isPlaying.value = !_isPlaying.value
            return
        }
        try {
            if (controller.isPlaying) {
                controller.pause()
                _isPlaying.value = false
            } else {
                if (controller.mediaItemCount == 0 && _queue.value.isNotEmpty()) {
                    val idx = _queue.value.indexOfFirst { it.id == _currentSong.value?.id }.coerceAtLeast(0)
                    pushQueueToControllerSafe(controller, _queue.value, idx)
                    return
                } else if (controller.playbackState == Player.STATE_ENDED) {
                    controller.seekTo(0, 0L)
                }
                controller.play()
                _isPlaying.value = true
            }
        } catch (_: Throwable) {
        }
    }

    fun playNext() {
        val controller = mediaController
        val currentQueue = _queue.value
        if (controller != null) {
            try {
                if (controller.hasNextMediaItem()) {
                    controller.seekToNextMediaItem()
                    if (!controller.isPlaying) {
                        controller.prepare()
                        controller.play()
                    }
                } else if (currentQueue.isNotEmpty()) {
                    playSongAtIndex(0)
                }
            } catch (_: Throwable) {
            }
        } else if (currentQueue.isNotEmpty()) {
            val currentIdx = currentQueue.indexOfFirst { it.id == _currentSong.value?.id }
            val nextIdx = if (currentIdx >= 0) (currentIdx + 1) % currentQueue.size else 0
            playSongAtIndex(nextIdx)
        }
    }

    fun playPrevious() {
        val controller = mediaController
        val currentQueue = _queue.value
        if (controller != null) {
            try {
                if (controller.currentPosition > 3000L) {
                    controller.seekTo(0L)
                } else if (controller.hasPreviousMediaItem()) {
                    controller.seekToPreviousMediaItem()
                } else if (currentQueue.isNotEmpty()) {
                    playSongAtIndex(currentQueue.lastIndex)
                }
            } catch (_: Throwable) {
            }
        } else if (currentQueue.isNotEmpty()) {
            val currentIdx = currentQueue.indexOfFirst { it.id == _currentSong.value?.id }
            val prevIdx = if (currentIdx > 0) currentIdx - 1 else currentQueue.lastIndex
            playSongAtIndex(prevIdx)
        }
    }

    fun stopPlayback() {
        safeController { controller ->
            controller.pause()
            controller.seekTo(0L)
        }
        _isPlaying.value = false
        statsRepository.flushPlayTime()
    }

    fun seekTo(positionMs: Long) {
        safeController { it.seekTo(positionMs.coerceAtLeast(0L)) }
    }

    fun toggleRepeatMode() {
        val nextMode = (_repeatMode.value + 1) % 3
        _repeatMode.value = nextMode
        safeController {
            it.repeatMode = when (nextMode) {
                1 -> Player.REPEAT_MODE_ALL
                2 -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun toggleShuffleMode() {
        val nextMode = !_shuffleModeEnabled.value
        _shuffleModeEnabled.value = nextMode
        safeController { it.shuffleModeEnabled = nextMode }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        safeController { it.playbackParameters = PlaybackParameters(clamped) }
        _playbackSpeed.value = clamped
    }

    private fun recordCurrentSongChange(song: Song) {
        if (_currentSong.value?.id == song.id) return
        _currentSong.value = song
        statsRepository.recordTrackPlay(song.id)

        val currentRecents = _recentlyPlayed.value.toMutableList()
        currentRecents.removeAll { it.id == song.id }
        currentRecents.add(0, song)
        val updatedRecents = currentRecents.take(12)
        _recentlyPlayed.value = updatedRecents
        sharedPrefs?.edit()
            ?.putString("settings_recently_played", updatedRecents.joinToString(",") { it.id.toString() })
            ?.apply()
    }

    // --- Likes & Playlists ---

    fun toggleLike(songId: Long) {
        val current = _likedSongs.value.toMutableSet()
        val added = if (current.contains(songId)) {
            current.remove(songId)
            false
        } else {
            current.add(songId)
            true
        }
        _likedSongs.value = current
        sharedPrefs?.edit()
            ?.putStringSet("liked_songs", current.map { it.toString() }.toSet())
            ?.apply()
        showMessage(if (added) "Added to Favorites" else "Removed from Favorites")
    }

    fun resolvePlaylistSongs(playlist: Playlist): List<Song> {
        val songMap = _songs.value.associateBy { it.id }
        return playlist.songIds.mapNotNull { songMap[it] }
    }

    fun createPlaylist(name: String, initialSongIds: List<Long> = emptyList()): Playlist? {
        val sanitized = sanitizePlaylistName(name)
        if (sanitized.isEmpty()) return null
        val created = Playlist(name = sanitized, songIds = initialSongIds.distinct())
        _playlists.value = _playlists.value + created
        savePlaylists()
        showMessage("Created \"$sanitized\"")
        return created
    }

    fun renamePlaylist(id: String, newName: String) {
        val sanitized = sanitizePlaylistName(newName)
        if (sanitized.isEmpty()) return
        _playlists.value = _playlists.value.map {
            if (it.id == id) it.copy(name = sanitized) else it
        }
        if (_activePlaylistId.value == id) {
            _activeQueueSource.value = sanitized
        }
        savePlaylists()
        showMessage("Renamed to \"$sanitized\"")
    }

    fun deletePlaylist(id: String) {
        val target = _playlists.value.find { it.id == id }
        _playlists.value = _playlists.value.filterNot { it.id == id }
        if (_activePlaylistId.value == id) {
            _activePlaylistId.value = null
        }
        savePlaylists()
        if (target != null) {
            showMessage("Deleted \"${target.name}\"")
        }
    }

    fun addSongToPlaylist(playlistId: String, songId: Long) {
        var added = false
        var playlistName = ""
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                playlistName = pl.name
                if (songId !in pl.songIds) {
                    added = true
                    pl.copy(songIds = pl.songIds + songId)
                } else pl
            } else pl
        }
        savePlaylists()
        if (added && _activePlaylistId.value == playlistId) {
            _songs.value.find { it.id == songId }?.let { song ->
                _queue.value = _queue.value + song
                safeController { it.addMediaItem(song.toMediaItem()) }
            }
        }
        if (playlistName.isNotEmpty()) {
            showMessage(if (added) "Added to \"$playlistName\"" else "Already in \"$playlistName\"")
        }
    }

    fun addSongsToPlaylist(playlistId: String, songIds: List<Long>) {
        if (songIds.isEmpty()) return
        var addedIds: List<Long> = emptyList()
        var playlistName = ""
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                playlistName = pl.name
                val existing = pl.songIds.toSet()
                val newOnes = songIds.filter { it !in existing }.distinct()
                addedIds = newOnes
                pl.copy(songIds = pl.songIds + newOnes)
            } else pl
        }
        savePlaylists()
        if (addedIds.isNotEmpty() && _activePlaylistId.value == playlistId) {
            val songMap = _songs.value.associateBy { it.id }
            val appended = addedIds.mapNotNull { songMap[it] }
            if (appended.isNotEmpty()) {
                _queue.value = _queue.value + appended
                safeController { controller ->
                    appended.chunked(BINDER_SAFE_CHUNK).forEach { chunk ->
                        controller.addMediaItems(chunk.map { it.toMediaItem() })
                    }
                }
            }
        }
        if (addedIds.isNotEmpty()) {
            val count = addedIds.size
            showMessage("Added $count ${if (count == 1) "song" else "songs"} to \"$playlistName\"")
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songId: Long) {
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == playlistId) pl.copy(songIds = pl.songIds.filterNot { it == songId }) else pl
        }
        savePlaylists()
        if (_activePlaylistId.value == playlistId) {
            val idx = _queue.value.indexOfFirst { it.id == songId }
            if (idx != -1) {
                removeQueueItem(idx)
            }
        }
        showMessage("Removed from playlist")
    }

    fun clearPlaylist(playlistId: String) {
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == playlistId) pl.copy(songIds = emptyList()) else pl
        }
        savePlaylists()
        if (_activePlaylistId.value == playlistId) {
            stopPlayback()
            _queue.value = emptyList()
            _currentSong.value = null
            safeController { it.clearMediaItems() }
        }
        showMessage("Playlist cleared")
    }

    fun requestAddToPlaylist(song: Song?) {
        _songToAddToPlaylist.value = song
    }

    fun showSongInfo(song: Song?) {
        _songInfoTarget.value = song
    }

    private fun sanitizePlaylistName(raw: String): String =
        raw.trim().replace(";;", " ").replace("##", " ")

    private fun loadPlaylists() {
        val raw = sharedPrefs?.getString("playlists_data", "") ?: ""
        if (raw.isEmpty()) {
            _playlists.value = emptyList()
            return
        }
        val list = mutableListOf<Playlist>()
        for (part in raw.split(";;")) {
            if (part.isBlank()) continue
            val fields = part.split("##")
            if (fields.size >= 2) {
                val id = fields[0]
                val name = fields[1]
                val idsStr = fields.getOrNull(2).orEmpty()
                val songIds = if (idsStr.isEmpty()) emptyList()
                else idsStr.split(",").mapNotNull { it.toLongOrNull() }
                list.add(Playlist(id, name, songIds))
            }
        }
        _playlists.value = list
    }

    private fun savePlaylists() {
        val serialized = _playlists.value.joinToString(";;") { playlist ->
            "${playlist.id}##${playlist.name}##${playlist.songIds.joinToString(",")}"
        }
        sharedPrefs?.edit()?.putString("playlists_data", serialized)?.apply()
    }

    // --- Sorting & Search ---

    fun setSorting(type: String, ascending: Boolean) {
        _sortType.value = type
        _sortAscending.value = ascending
        viewModelScope.launch {
            val sorted = withContext(Dispatchers.Default) {
                sortSongsList(_songs.value, type, ascending)
            }
            _songs.value = sorted
        }
    }

    internal fun sortSongsList(list: List<Song>, type: String, ascending: Boolean): List<Song> {
        return when (type) {
            "Artist" -> if (ascending) list.sortedBy { it.artist.lowercase() } else list.sortedByDescending { it.artist.lowercase() }
            "Duration" -> if (ascending) list.sortedBy { it.duration } else list.sortedByDescending { it.duration }
            "Recent" -> if (ascending) list.sortedBy { it.dateAdded } else list.sortedByDescending { it.dateAdded }
            else -> if (ascending) list.sortedBy { it.title.lowercase() } else list.sortedByDescending { it.title.lowercase() }
        }
    }

    fun addRecentSearch(query: String) {
        val trimmed = query.trim().replace("||", " ")
        if (trimmed.isEmpty()) return
        val current = _recentSearches.value.toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)
        val capped = current.take(8)
        _recentSearches.value = capped
        sharedPrefs?.edit()?.putString("recent_searches", capped.joinToString("||"))?.apply()
    }

    fun removeRecentSearch(query: String) {
        val updated = _recentSearches.value.filterNot { it == query }
        _recentSearches.value = updated
        sharedPrefs?.edit()?.putString("recent_searches", updated.joinToString("||"))?.apply()
    }

    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
        sharedPrefs?.edit()?.putString("recent_searches", "")?.apply()
    }

    fun registerSearchPlayedSong(songId: Long) {
        val current = _searchPlayedIds.value.toMutableList()
        current.remove(songId)
        current.add(0, songId)
        val capped = current.take(15)
        _searchPlayedIds.value = capped
        sharedPrefs?.edit()?.putString("search_played_song_ids", capped.joinToString(","))?.apply()
    }

    // --- Audio DSP & Settings ---

    fun setEqEnabled(enabled: Boolean) {
        _eqEnabled.value = enabled
        sharedPrefs?.edit()?.putBoolean("eq_enabled", enabled)?.apply()
    }

    fun setEqualizerPreset(presetName: String) {
        _equalizerPreset.value = presetName
        sharedPrefs?.edit()?.putString("equalizer_preset", presetName)?.apply()
        EqualizerPreset.fromLabel(presetName)?.let { preset ->
            setEqualizerBands(preset.bands)
        }
    }

    fun setEqualizerBands(bands: List<Float>) {
        val clamped = bands.map { it.coerceIn(-12f, 12f) }
        _equalizerBands.value = clamped
        sharedPrefs?.edit()?.putString("equalizer_bands", clamped.joinToString(","))?.apply()
    }

    fun setBassBoostStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _bassBoostStrength.value = clamped
        sharedPrefs?.edit()?.putInt("bass_boost_strength", clamped)?.apply()
    }

    fun setCrossfadeSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(0, 8)
        _crossfadeSeconds.value = clamped
        sharedPrefs?.edit()?.putInt("crossfade_seconds", clamped)?.apply()
    }

    fun setSkipSilence(enabled: Boolean) {
        _skipSilence.value = enabled
        sharedPrefs?.edit()?.putBoolean("skip_silence", enabled)?.apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        sharedPrefs?.edit()?.putString("theme_mode", mode.key)?.apply()
    }

    fun setAccentColor(color: Color) {
        _accentColor.value = color
        val r = (color.red * 255).toInt().coerceIn(0, 255)
        val g = (color.green * 255).toInt().coerceIn(0, 255)
        val b = (color.blue * 255).toInt().coerceIn(0, 255)
        val hex = String.format("#%02X%02X%02X", r, g, b)
        sharedPrefs?.edit()?.putString("theme_accent_hex", hex)?.apply()
    }

    fun setShowCovers(show: Boolean) {
        _showCovers.value = show
        sharedPrefs?.edit()?.putBoolean("settings_show_covers", show)?.apply()
    }

    fun setGlassmorphismEnabled(enabled: Boolean) {
        _glassmorphismEnabled.value = enabled
        sharedPrefs?.edit()?.putBoolean("settings_glassmorphism", enabled)?.apply()
    }

    fun setPipEnabled(enabled: Boolean) {
        _pipEnabled.value = enabled
        sharedPrefs?.edit()?.putBoolean("settings_pip", enabled)?.apply()
    }

    fun setUsername(name: String) {
        val sanitized = name.trim().ifEmpty { "My Music" }
        _username.value = sanitized
        sharedPrefs?.edit()?.putString("profile_username", sanitized)?.apply()
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearImageCache(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val loader = context.applicationContext.imageLoader
            val diskSizeBytes = loader.diskCache?.size ?: 0L
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
            withContext(Dispatchers.Main) {
                val freedLabel = if (diskSizeBytes > 1024) {
                    "Cleared ${String.format(java.util.Locale.US, "%.1f MB", diskSizeBytes / (1024.0 * 1024.0))} cache"
                } else {
                    "Artwork cache cleared"
                }
                showMessage(freedLabel)
            }
        }
    }

    // --- Sleep Timer ---

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepAfterCurrentTrack.value = false
        if (minutes <= 0) {
            _sleepTimerRemainingMs.value = 0L
            return
        }
        val targetEndMs = System.currentTimeMillis() + minutes * 60_000L
        _sleepTimerRemainingMs.value = minutes * 60_000L
        sleepTimerJob = viewModelScope.launch {
            while (isActive) {
                val remaining = (targetEndMs - System.currentTimeMillis()).coerceAtLeast(0L)
                _sleepTimerRemainingMs.value = remaining
                if (remaining == 0L) {
                    stopPlayback()
                    showMessage("Sleep timer paused playback")
                    break
                }
                delay(1000L)
            }
        }
        showMessage("Sleep timer set for $minutes min")
    }

    fun setSleepAfterCurrentTrack(enabled: Boolean) {
        sleepTimerJob?.cancel()
        _sleepTimerRemainingMs.value = 0L
        _sleepAfterCurrentTrack.value = enabled
        if (enabled) {
            showMessage("Will pause after this song")
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingMs.value = 0L
        _sleepAfterCurrentTrack.value = false
        showMessage("Sleep timer canceled")
    }

    override fun onCleared() {
        super.onCleared()
        queueSyncJob?.cancel()
        statsRepository.flushPlayTime()
        sleepTimerJob?.cancel()
        try {
            mediaControllerFuture?.let { MediaController.releaseFuture(it) }
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val BINDER_SAFE_CHUNK = 80
    }

    class Factory(
        private val repository: MediaRepository,
        private val statsRepository: StatsRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MusicViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MusicViewModel(repository, statsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

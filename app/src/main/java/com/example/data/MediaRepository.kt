package com.example.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val ALBUM_ART_BASE_URI: Uri by lazy(LazyThreadSafetyMode.PUBLICATION) {
    Uri.parse("content://media/external/audio/albumart")
}

@Immutable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val albumId: Long,
    val year: Int = 0,
    val composer: String = "Unknown Composer",
    val path: String = "",
    val dateAdded: Long = 0L,
    val size: Long = 0L,
    val mimeType: String = "",
    val trackNumber: Int = 0,
    val genre: String = "Unknown Genre",
    val isBuiltInDemo: Boolean = false
) {
    // Pre-computed & cached once per Song — zero Uri.parse / File / String.format during 60fps scroll!
    val folderName: String by lazy(LazyThreadSafetyMode.NONE) {
        if (isBuiltInDemo) "Built-in Studio"
        else if (path.isEmpty()) "Imported Audio"
        else File(path).parentFile?.name ?: "Local Storage"
    }

    val albumArtUri: Uri? by lazy(LazyThreadSafetyMode.NONE) {
        if (albumId > 0L) ContentUris.withAppendedId(ALBUM_ART_BASE_URI, albumId) else null
    }

    val formatLabel: String by lazy(LazyThreadSafetyMode.NONE) {
        when {
            mimeType.contains("flac", ignoreCase = true) -> "FLAC"
            mimeType.contains("wav", ignoreCase = true) -> "WAV"
            mimeType.contains("ogg", ignoreCase = true) -> "OGG"
            mimeType.contains("mp4", ignoreCase = true) || mimeType.contains("m4a", ignoreCase = true) -> "AAC / M4A"
            mimeType.contains("opus", ignoreCase = true) -> "OPUS"
            mimeType.contains("mpeg", ignoreCase = true) || mimeType.contains("mp3", ignoreCase = true) -> "MP3"
            else -> mimeType.substringAfter("/", "AUDIO").uppercase()
        }
    }

    val durationFormatted: String = formatDurationFast(duration)

    val artworkKey: String = if (album.isNotBlank()) album else title
}

/**
 * Zero-regex, zero-Formatter duration formatter (~30x faster than String.format on Cortex-A53).
 */
fun formatDurationFast(durationMs: Long): String {
    val safeMs = if (durationMs > 0L) durationMs else 0L
    val totalSeconds = safeMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    val sb = StringBuilder(8)
    if (hours > 0L) {
        sb.append(hours).append(':')
        if (minutes < 10L) sb.append('0')
        sb.append(minutes).append(':')
    } else {
        sb.append(minutes).append(':')
    }
    if (seconds < 10L) sb.append('0')
    sb.append(seconds)
    return sb.toString()
}

class MediaRepository(private val context: Context) {

    private val prefs by lazy {
        context.getSharedPreferences("vrag_prefs", Context.MODE_PRIVATE)
    }

    suspend fun getAudioFiles(includeBuiltInDemoIfEmpty: Boolean = true): List<Song> =
        withContext(Dispatchers.IO) {
            val songs = mutableListOf<Song>()
            val seenKeys = HashSet<String>()

            // 1. Query MediaStore for local device music
            scanMediaStore(songs, seenKeys)

            // 2. Load any SAF-imported audio files persisted by the user
            loadImportedAudio(songs, seenKeys)

            // 3. Offline studio demo tracks fallback when device has 0 audio files
            if (songs.isEmpty() && includeBuiltInDemoIfEmpty) {
                songs.addAll(OfflineDemoAudioGenerator.ensureDemoSongs(context))
            }

            songs
        }

    suspend fun importAudioUris(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext 0
        val existing = prefs.getStringSet("imported_audio_uris", emptySet())?.toMutableSet()
            ?: mutableSetOf()
        var addedCount = 0
        for (uri in uris) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers don't support persistable grants; still keep URI for current session
            }
            if (existing.add(uri.toString())) {
                addedCount++
            }
        }
        prefs.edit().putStringSet("imported_audio_uris", existing).apply()
        addedCount
    }

    private fun scanMediaStore(songs: MutableList<Song>, seenKeys: MutableSet<String>) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projectionList = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.COMPOSER,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.TRACK
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projectionList.add(MediaStore.Audio.Media.GENRE)
        }

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 1000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            context.contentResolver.query(
                collection,
                projectionList.toTypedArray(),
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
                val compCol = cursor.getColumnIndex(MediaStore.Audio.Media.COMPOSER)
                val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
                val genreCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    var title = if (titleCol != -1) cursor.getString(titleCol) else null
                    var artist = if (artistCol != -1) cursor.getString(artistCol) else null
                    var album = if (albumCol != -1) cursor.getString(albumCol) else null

                    if (title.isNullOrBlank() || title == "<unknown>") {
                        title = File(path).nameWithoutExtension.takeIf { it.isNotBlank() } ?: "Unknown Track"
                    }
                    if (artist.isNullOrBlank() || artist == "<unknown>") {
                        artist = "Unknown Artist"
                    } else if (artist.contains(";")) {
                        artist = artist.split(";")[0].trim()
                    }
                    if (album.isNullOrBlank() || album == "<unknown>") {
                        album = "Single"
                    }

                    val duration = if (durationCol != -1) cursor.getLong(durationCol) else 0L
                    val albumId = if (albumIdCol != -1) cursor.getLong(albumIdCol) else 0L
                    val year = if (yearCol != -1) cursor.getInt(yearCol) else 0
                    val composer = if (compCol != -1) cursor.getString(compCol)?.takeIf { it.isNotBlank() } ?: "Unknown Composer" else "Unknown Composer"
                    val dateAdded = if (dateCol != -1) cursor.getLong(dateCol) else 0L
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val mimeType = if (mimeCol != -1) cursor.getString(mimeCol) ?: "audio/mpeg" else "audio/mpeg"
                    val track = if (trackCol != -1) cursor.getInt(trackCol) else 0
                    val genre = if (genreCol != -1) cursor.getString(genreCol)?.takeIf { it.isNotBlank() } ?: "Other" else "Other"

                    val dedupKey = "${title.lowercase()}|$size|$duration"
                    if (!seenKeys.add(dedupKey)) continue

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    songs.add(
                        Song(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                            uri = contentUri,
                            albumId = albumId,
                            year = year,
                            composer = composer,
                            path = path,
                            dateAdded = dateAdded,
                            size = size,
                            mimeType = mimeType,
                            trackNumber = track,
                            genre = genre
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission not granted yet; gracefully continue to SAF / offline tracks
        } catch (_: Throwable) {
            // Protect against OEM MediaStore quirks
        }
    }

    private fun loadImportedAudio(songs: MutableList<Song>, seenKeys: MutableSet<String>) {
        val uriStrings = prefs.getStringSet("imported_audio_uris", emptySet()) ?: return
        if (uriStrings.isEmpty()) return

        for (uriStr in uriStrings) {
            val uri = Uri.parse(uriStr)
            try {
                var displayName = "Imported Track"
                var fileSize = 0L
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (c.moveToFirst()) {
                        if (nameIdx != -1) displayName = c.getString(nameIdx) ?: displayName
                        if (sizeIdx != -1) fileSize = c.getLong(sizeIdx)
                    }
                }

                val retriever = MediaMetadataRetriever()
                var title = displayName.substringBeforeLast(".")
                var artist = "Imported Audio"
                var album = "Imported Library"
                var duration = 0L
                var year = 0
                var genre = "Imported"
                var mime = context.contentResolver.getType(uri) ?: "audio/mpeg"

                try {
                    retriever.setDataSource(context, uri)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?.takeIf { it.isNotBlank() }?.let { title = it }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?.takeIf { it.isNotBlank() }?.let { artist = it }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                        ?.takeIf { it.isNotBlank() }?.let { album = it }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()?.let { duration = it }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                        ?.toIntOrNull()?.let { year = it }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                        ?.takeIf { it.isNotBlank() }?.let { genre = it }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                        ?.takeIf { it.isNotBlank() }?.let { mime = it }
                } catch (_: Throwable) {
                    // Metadata extraction failed; continue with display name fallback
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Throwable) {
                    }
                }

                val stableId = 500_000_000L + (uriStr.hashCode().toLong() and 0x7FFFFFFFL)
                val dedupKey = "${title.lowercase()}|$fileSize|$duration"
                if (!seenKeys.add(dedupKey)) continue

                songs.add(
                    Song(
                        id = stableId,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration.coerceAtLeast(1000L),
                        uri = uri,
                        albumId = 0L,
                        year = year,
                        path = "",
                        dateAdded = System.currentTimeMillis() / 1000L,
                        size = fileSize,
                        mimeType = mime,
                        genre = genre
                    )
                )
            } catch (_: Throwable) {
                // Skip unreadable or revoked SAF URI
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "—"
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024L
    if (kb < 1024L) return "$kb KB"
    val tenthsMb = ((bytes * 10L) + (1024L * 1024L / 2L)) / (1024L * 1024L)
    return "${tenthsMb / 10L}.${tenthsMb % 10L} MB"
}

package com.example.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var attachedSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var crossfadeMonitorJob: Job? = null
    private var fadeInJob: Job? = null
    private var consecutiveErrorCount = 0

    private lateinit var sharedPrefs: SharedPreferences

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "equalizer_bands", "eq_enabled", "bass_boost_strength" -> syncAudioEffects()
            "skip_silence" -> applySkipSilence()
            "crossfade_seconds" -> restartCrossfadeMonitor()
        }
    }

    override fun onCreate() {
        super.onCreate()
        sharedPrefs = getSharedPreferences("vrag_prefs", Context.MODE_PRIVATE)
        sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener)

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setChannelName(R.string.notification_channel_name)
            .setNotificationId(NOTIFICATION_ID)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_stat_music)
        setMediaNotificationProvider(notificationProvider)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // IMPORTANT: Do NOT set WAKE_MODE_LOCAL without WAKE_LOCK permission — foreground
        // MediaSessionService keeps audio playing reliably across all Android versions without crash risk.
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        applySkipSilence()

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId > 0) {
                    attachedSessionId = audioSessionId
                    syncAudioEffects(forceReattach = true)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    consecutiveErrorCount = 0
                    restartCrossfadeMonitor()
                } else {
                    crossfadeMonitorJob?.cancel()
                    fadeInJob?.cancel()
                    player.volume = 1f
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val crossfadeSec = sharedPrefs.getInt("crossfade_seconds", 0)
                if (crossfadeSec > 0 && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && player.isPlaying) {
                    runFadeIn(crossfadeSec)
                } else {
                    fadeInJob?.cancel()
                    player.volume = 1f
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Gracefully skip corrupt/unreadable tracks instead of crashing or hanging
                consecutiveErrorCount++
                if (consecutiveErrorCount <= 3 && player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                } else {
                    player.pause()
                }
            }
        })

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        if (player.audioSessionId > 0) {
            attachedSessionId = player.audioSessionId
            syncAudioEffects(forceReattach = true)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = mediaSession?.player
        if (currentPlayer == null || !currentPlayer.playWhenReady || currentPlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    /**
     * Lazily attaches Equalizer / BassBoost only when non-flat DSP is actually requested
     * and audioSessionId > 0 (never session 0 global mix, which crashes Android 14+ OEM HALs).
     */
    private fun syncAudioEffects(forceReattach: Boolean = false) {
        val sessionId = attachedSessionId
        if (sessionId <= 0) return

        val eqEnabled = sharedPrefs.getBoolean("eq_enabled", false)
        val bassStrength = sharedPrefs.getInt("bass_boost_strength", 0).coerceIn(0, 1000)
        val bandsStr = sharedPrefs.getString("equalizer_bands", "0,0,0,0,0") ?: "0,0,0,0,0"
        val bandLevels = bandsStr.split(",").map { it.toFloatOrNull() ?: 0f }
        val needsEq = eqEnabled && bandLevels.any { abs(it) > 0.1f }
        val needsBass = eqEnabled && bassStrength > 0

        if (!needsEq && !needsBass) {
            releaseAudioEffects()
            return
        }

        if (forceReattach) {
            releaseAudioEffects()
        }

        if (needsEq) {
            try {
                if (equalizer == null) {
                    equalizer = Equalizer(0, sessionId)
                }
                equalizer?.let { eq ->
                    eq.enabled = true
                    val numBands = eq.numberOfBands.toInt()
                    val levelRange = eq.bandLevelRange
                    val minLevel = levelRange[0].toInt()
                    val maxLevel = levelRange[1].toInt()
                    for (i in 0 until minOf(numBands, bandLevels.size)) {
                        val millibels = (bandLevels[i] * 100f).toInt().coerceIn(minLevel, maxLevel)
                        eq.setBandLevel(i.toShort(), millibels.toShort())
                    }
                }
            } catch (_: Throwable) {
                try { equalizer?.release() } catch (_: Throwable) {}
                equalizer = null
            }
        } else {
            try { equalizer?.enabled = false } catch (_: Throwable) {}
        }

        if (needsBass) {
            try {
                if (bassBoost == null) {
                    bassBoost = BassBoost(0, sessionId)
                }
                bassBoost?.let { bb ->
                    bb.enabled = true
                    if (bb.strengthSupported) {
                        bb.setStrength(bassStrength.toShort())
                    }
                }
            } catch (_: Throwable) {
                try { bassBoost?.release() } catch (_: Throwable) {}
                bassBoost = null
            }
        } else {
            try { bassBoost?.enabled = false } catch (_: Throwable) {}
        }
    }

    private fun applySkipSilence() {
        try {
            player.skipSilenceEnabled = sharedPrefs.getBoolean("skip_silence", false)
        } catch (_: Throwable) {
        }
    }

    private fun restartCrossfadeMonitor() {
        crossfadeMonitorJob?.cancel()
        val crossfadeSec = sharedPrefs.getInt("crossfade_seconds", 0)
        if (crossfadeSec <= 0 || !player.isPlaying) {
            player.volume = 1f
            return
        }
        val fadeMs = crossfadeSec * 1000L
        crossfadeMonitorJob = serviceScope.launch {
            while (isActive && player.isPlaying) {
                val duration = player.duration
                val position = player.currentPosition
                if (duration > fadeMs * 2 && position >= 0) {
                    val remaining = duration - position
                    if (remaining in 1..fadeMs && (player.hasNextMediaItem() || player.repeatMode != Player.REPEAT_MODE_OFF)) {
                        val fraction = (remaining.toFloat() / fadeMs.toFloat()).coerceIn(0.05f, 1f)
                        player.volume = fraction
                        delay(100L)
                        continue
                    } else if (fadeInJob?.isActive != true && player.volume < 0.99f) {
                        player.volume = 1f
                    }
                }
                delay(350L)
            }
        }
    }

    private fun runFadeIn(crossfadeSec: Int) {
        fadeInJob?.cancel()
        fadeInJob = serviceScope.launch {
            val steps = 16
            val stepDelay = ((crossfadeSec * 1000L) / steps).coerceAtLeast(40L)
            for (s in 1..steps) {
                if (!player.isPlaying) break
                player.volume = (s.toFloat() / steps.toFloat()).coerceIn(0.05f, 1f)
                delay(stepDelay)
            }
            player.volume = 1f
        }
    }

    private fun releaseAudioEffects() {
        try { equalizer?.release() } catch (_: Throwable) {}
        equalizer = null
        try { bassBoost?.release() } catch (_: Throwable) {}
        bassBoost = null
    }

    override fun onDestroy() {
        serviceScope.cancel()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        releaseAudioEffects()

        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "vrag_playback_channel"
        private const val NOTIFICATION_ID = 1001
    }
}

package com.example

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import com.example.data.OfflineDemoAudioGenerator
import com.example.data.StatsRepository
import com.example.data.formatFileSize
import com.example.ui.EqualizerPreset
import com.example.ui.components.formatCountdown
import com.example.ui.components.formatDuration
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.contrastingOnColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Calendar
import kotlin.math.abs

class VragPlayerUnitTest {

    @Test
    fun offlineDemoAudioGenerator_synthesizesValidNonSilentRiffWavFile() {
        val tempDir = Files.createTempDirectory("vrag_wav_test").toFile()
        try {
            val spec = OfflineDemoAudioGenerator.demoSpecs.first()
            val outFile = File(tempDir, spec.fileName)
            OfflineDemoAudioGenerator.writeSynthesizedWav(outFile, spec)

            assertTrue("WAV file should exist", outFile.exists())
            // 16000 Hz * 12 sec * 2 bytes/sample + 44 byte header
            val expectedBytes = OfflineDemoAudioGenerator.SAMPLE_RATE * OfflineDemoAudioGenerator.DURATION_SECONDS * 2 + 44L
            assertEquals(expectedBytes, outFile.length())

            val bytes = outFile.readBytes()
            assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
            assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
            assertEquals("fmt ", String(bytes, 12, 4, Charsets.US_ASCII))
            assertEquals("data", String(bytes, 36, 4, Charsets.US_ASCII))

            // Verify actual audio energy in the middle of the track (not silence!)
            var maxAbsSample = 0
            val startOffset = 44 + OfflineDemoAudioGenerator.SAMPLE_RATE * 2 * 2 // 2 seconds in
            for (i in startOffset until startOffset + 4096 step 2) {
                val lo = bytes[i].toInt() and 0xFF
                val hi = bytes[i + 1].toInt() shl 8
                val sample = (hi or lo).toShort().toInt()
                maxAbsSample = maxOf(maxAbsSample, abs(sample))
            }
            assertTrue("Synthesized WAV should contain audible musical waveform", maxAbsSample > 2000)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun statsRepository_persistsRealStreaksHourlyBucketsAndPlayTime() {
        val prefs = InMemorySharedPreferences()
        val repo = StatsRepository(prefs)

        val day1 = Calendar.getInstance().apply { set(2026, Calendar.MAY, 10, 14, 30, 0) }.timeInMillis
        val day1Later = Calendar.getInstance().apply { set(2026, Calendar.MAY, 10, 20, 0, 0) }.timeInMillis
        val day2 = Calendar.getInstance().apply { set(2026, Calendar.MAY, 11, 14, 45, 0) }.timeInMillis
        val day5 = Calendar.getInstance().apply { set(2026, Calendar.MAY, 14, 8, 15, 0) }.timeInMillis

        repo.recordTrackPlay(90001L, day1)
        assertEquals(1, repo.stats.value.lifetimePlays)
        assertEquals(1, repo.stats.value.currentStreakDays)
        assertEquals(1, repo.stats.value.hourlyActivity[14])
        assertEquals(1, repo.stats.value.songPlayCounts[90001L])

        // Same calendar day keeps streak at 1
        repo.recordTrackPlay(90001L, day1Later)
        assertEquals(2, repo.stats.value.lifetimePlays)
        assertEquals(1, repo.stats.value.currentStreakDays)
        assertEquals(1, repo.stats.value.hourlyActivity[20])
        assertEquals(2, repo.stats.value.songPlayCounts[90001L])

        // Consecutive calendar day increments streak to 2
        repo.recordTrackPlay(90002L, day2)
        assertEquals(2, repo.stats.value.currentStreakDays)
        assertEquals(2, repo.stats.value.hourlyActivity[14])
        assertEquals(1, repo.stats.value.songPlayCounts[90002L])

        // Multi-day break resets streak cleanly to 1
        repo.recordTrackPlay(90003L, day5)
        assertEquals(1, repo.stats.value.currentStreakDays)

        // Buffering avoids 1Hz StateFlow emissions: 10s shouldn't emit yet
        repeat(10) { repo.addPlaySecond(day5) }
        assertEquals(0.0f, repo.stats.value.totalPlayTimeMinutes, 0.001f)

        // Reaching 60s total (50 more seconds) + flush = 1.0 minute
        repeat(50) { repo.addPlaySecond(day5) }
        repo.flushPlayTime()
        assertEquals(1.0f, repo.stats.value.totalPlayTimeMinutes, 0.02f)

        // Verify persistence when a fresh StatsRepository reads the same SharedPreferences
        val reloaded = StatsRepository(prefs)
        assertEquals(4, reloaded.stats.value.lifetimePlays)
        assertEquals(2, reloaded.stats.value.songPlayCounts[90001L])
        assertEquals(2, reloaded.stats.value.hourlyActivity[14])
        assertEquals(1.0f, reloaded.stats.value.totalPlayTimeMinutes, 0.02f)
    }

    @Test
    fun equalizerPresetsAndThemeModes_roundTripAccurately() {
        EqualizerPreset.entries.forEach { preset ->
            assertEquals(5, preset.bands.size)
            assertEquals(preset, EqualizerPreset.fromLabel(preset.label))
        }
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromKey(mode.key))
        }
        assertEquals(ThemeMode.DARK_STUDIO, ThemeMode.fromKey("unknown_key"))

        // Contrast helper chooses dark ink on bright accents and white ink on dark accents
        assertEquals(Color.White, Color(0xFF1A1A22).contrastingOnColor())
        assertEquals(Color(0xFF111116), Color(0xFFF5F3EF).contrastingOnColor())
        assertNotEquals(StudioCoral, StudioCoral.contrastingOnColor())
    }

    @Test
    fun formattingHelpers_handleBoundaryValues() {
        assertEquals("0:00", formatDuration(-50L))
        assertEquals("0:00", formatDuration(0L))
        assertEquals("0:24", formatDuration(24_000L))
        assertEquals("3:05", formatDuration(185_000L))
        assertEquals("1:01:05", formatDuration(3_665_000L))

        assertEquals("00:00", formatCountdown(-10L))
        assertEquals("15:00", formatCountdown(900_000L))
        assertEquals("01:05", formatCountdown(65_000L))

        assertEquals("512 B", formatFileSize(512L))
        assertEquals("10 KB", formatFileSize(10 * 1024L))
        assertEquals("4.5 MB", formatFileSize((4.5 * 1024 * 1024).toLong()))
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? =
            (map[key] as? String) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (map[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int =
            (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long =
            (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float =
            (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = InMemoryEditor()
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private inner class InMemoryEditor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) pending[key] = values?.toSet() }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?) = apply { if (key != null) pending[key] = null }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) map.clear()
                pending.forEach { (k, v) ->
                    if (v == null) map.remove(k) else map[k] = v
                }
            }
        }
    }
}

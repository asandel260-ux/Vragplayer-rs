package com.example.data

import android.content.Context
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates 3 lightweight offline sample tracks in app cache ONLY if the device
 * has zero local audio files so playback can always be verified immediately.
 */
object OfflineDemoAudioGenerator {

    data class DemoSpec(
        val id: Long,
        val fileName: String,
        val title: String,
        val artist: String,
        val album: String,
        val genre: String,
        val year: Int,
        val trackNumber: Int,
        val chordsHz: List<DoubleArray>,
        val arpPattern: IntArray,
        val tempoBpm: Double
    )

    internal val demoSpecs = listOf(
        DemoSpec(
            id = 90001L,
            fileName = "amber_horizon.wav",
            title = "Amber Horizon",
            artist = "Kora & Loom",
            album = "Evening Sketches",
            genre = "Ambient",
            year = 2025,
            trackNumber = 1,
            chordsHz = listOf(
                doubleArrayOf(220.0, 261.63, 329.63, 392.00),
                doubleArrayOf(174.61, 220.0, 261.63, 329.63),
                doubleArrayOf(196.00, 246.94, 293.66, 392.00),
                doubleArrayOf(164.81, 196.00, 246.94, 329.63)
            ),
            arpPattern = intArrayOf(0, 1, 2, 3, 2, 1),
            tempoBpm = 78.0
        ),
        DemoSpec(
            id = 90002L,
            fileName = "midnight_velvet.wav",
            title = "Midnight Velvet",
            artist = "Nocturne",
            album = "After Hours",
            genre = "Lo-Fi",
            year = 2025,
            trackNumber = 2,
            chordsHz = listOf(
                doubleArrayOf(146.83, 220.00, 261.63, 329.63),
                doubleArrayOf(196.00, 246.94, 329.63, 349.23),
                doubleArrayOf(130.81, 196.00, 246.94, 329.63),
                doubleArrayOf(110.00, 196.00, 261.63, 329.63)
            ),
            arpPattern = intArrayOf(0, 2, 1, 3, 1, 2),
            tempoBpm = 86.0
        ),
        DemoSpec(
            id = 90003L,
            fileName = "glass_rain.wav",
            title = "Glass Rain",
            artist = "Maren Solberg",
            album = "Quiet Rooms",
            genre = "Acoustic",
            year = 2024,
            trackNumber = 3,
            chordsHz = listOf(
                doubleArrayOf(174.61, 220.00, 261.63, 349.23),
                doubleArrayOf(130.81, 164.81, 196.00, 261.63),
                doubleArrayOf(146.83, 174.61, 220.00, 293.66),
                doubleArrayOf(116.54, 174.61, 233.08, 293.66)
            ),
            arpPattern = intArrayOf(0, 3, 2, 1, 2, 3),
            tempoBpm = 74.0
        )
    )

    internal const val SAMPLE_RATE = 16000
    internal const val DURATION_SECONDS = 12

    fun ensureDemoSongs(context: Context): List<Song> {
        val demoDir = File(context.cacheDir, "vrag_samples")
        if (!demoDir.exists()) demoDir.mkdirs()

        val nowSec = System.currentTimeMillis() / 1000L
        return demoSpecs.mapIndexed { idx, spec ->
            val file = File(demoDir, spec.fileName)
            if (!file.exists() || file.length() < 1024L) {
                writeSynthesizedWav(file, spec)
            }
            Song(
                id = spec.id,
                title = spec.title,
                artist = spec.artist,
                album = spec.album,
                duration = DURATION_SECONDS * 1000L,
                uri = Uri.fromFile(file),
                albumId = 0L,
                year = spec.year,
                composer = spec.artist,
                path = file.absolutePath,
                dateAdded = nowSec - (idx * 3600L),
                size = file.length(),
                mimeType = "audio/wav",
                trackNumber = spec.trackNumber,
                genre = spec.genre,
                isBuiltInDemo = true
            )
        }
    }

    internal fun writeSynthesizedWav(file: File, spec: DemoSpec) {
        val totalSamples = SAMPLE_RATE * DURATION_SECONDS
        val dataBytes = totalSamples * 2
        val byteRate = SAMPLE_RATE * 2
        val header = buildWavHeader(dataBytes, SAMPLE_RATE, 1, 16, byteRate)

        val chordDurationSamples = totalSamples / spec.chordsHz.size
        val stepDurationSec = 60.0 / (spec.tempoBpm * 2.0)
        val stepSamples = (stepDurationSec * SAMPLE_RATE).toInt().coerceAtLeast(1)

        BufferedOutputStream(FileOutputStream(file), 16384).use { out ->
            out.write(header)
            val buffer = ByteArray(4096)
            var bufIdx = 0

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val chordIdx = (i / chordDurationSamples).coerceAtMost(spec.chordsHz.size - 1)
                val chord = spec.chordsHz[chordIdx]
                val posInChord = (i % chordDurationSamples).toDouble() / chordDurationSamples

                val chordEnv = when {
                    posInChord < 0.08 -> posInChord / 0.08
                    posInChord > 0.92 -> (1.0 - posInChord) / 0.08
                    else -> 1.0
                }

                var pad = 0.0
                for (n in chord.indices) {
                    pad += sin(2.0 * PI * chord[n] * t) * 0.15
                }
                val bass = sin(2.0 * PI * (chord[0] * 0.5) * t) * 0.22

                val stepIndex = (i / stepSamples) % spec.arpPattern.size
                val noteIdx = spec.arpPattern[stepIndex] % chord.size
                val stepProgress = (i % stepSamples).toDouble() / stepSamples
                val pluckEnv = if (stepProgress < 0.03) {
                    stepProgress / 0.03
                } else {
                    kotlin.math.exp(-4.5 * (stepProgress - 0.03))
                }
                val arpFreq = chord[noteIdx] * 2.0
                val pluck = sin(2.0 * PI * arpFreq * t) * 0.18 * pluckEnv

                val globalEnv = when {
                    i < SAMPLE_RATE / 2 -> i.toDouble() / (SAMPLE_RATE / 2)
                    i > totalSamples - SAMPLE_RATE / 2 -> (totalSamples - i).toDouble() / (SAMPLE_RATE / 2)
                    else -> 1.0
                }

                val sampleDouble = ((pad + bass) * chordEnv + pluck) * globalEnv * 0.85
                val pcm = (sampleDouble.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt()

                buffer[bufIdx++] = (pcm and 0xFF).toByte()
                buffer[bufIdx++] = ((pcm shr 8) and 0xFF).toByte()
                if (bufIdx >= buffer.size) {
                    out.write(buffer, 0, bufIdx)
                    bufIdx = 0
                }
            }
            if (bufIdx > 0) {
                out.write(buffer, 0, bufIdx)
            }
        }
    }

    internal fun buildWavHeader(
        dataSize: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        byteRate: Int
    ): ByteArray {
        val totalSize = dataSize + 36
        val blockAlign = channels * (bitsPerSample / 8)
        return byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            (totalSize and 0xFF).toByte(), ((totalSize shr 8) and 0xFF).toByte(),
            ((totalSize shr 16) and 0xFF).toByte(), ((totalSize shr 24) and 0xFF).toByte(),
            'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(),
            'f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte(),
            16, 0, 0, 0,
            1, 0,
            channels.toByte(), 0,
            (sampleRate and 0xFF).toByte(), ((sampleRate shr 8) and 0xFF).toByte(),
            ((sampleRate shr 16) and 0xFF).toByte(), ((sampleRate shr 24) and 0xFF).toByte(),
            (byteRate and 0xFF).toByte(), ((byteRate shr 8) and 0xFF).toByte(),
            ((byteRate shr 16) and 0xFF).toByte(), ((byteRate shr 24) and 0xFF).toByte(),
            blockAlign.toByte(), 0,
            bitsPerSample.toByte(), 0,
            'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(),
            (dataSize and 0xFF).toByte(), ((dataSize shr 8) and 0xFF).toByte(),
            ((dataSize shr 16) and 0xFF).toByte(), ((dataSize shr 24) and 0xFF).toByte()
        )
    }
}

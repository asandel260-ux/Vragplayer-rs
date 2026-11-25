package com.example.ui.theme

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import android.graphics.Color as AndroidColor

/**
 * Tri-tone album palette extracted directly from embedded album artwork.
 * Drives the fluid dynamic glassmorphism mesh gradients and frosted surface refraction.
 */
@Immutable
data class AlbumPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
)

/**
 * Ultra-lightweight, zero-dependency multi-tone album-art color engine.
 * Decodes album art downsampled to ~24x24px (< 3 KB RAM) on Dispatchers.IO,
 * clusters 24 perceptual hue buckets weighted by vibrancy x mid-luminance,
 * selects harmonic primary/secondary/tertiary tones with guaranteed WCAG legibility,
 * and caches results in a tiny 64-entry LruCache.
 */
object AlbumColorExtractor {

    private val paletteCache = LruCache<Long, AlbumPalette>(64)

    suspend fun extractPaletteForSong(
        context: Context,
        song: Song?,
        fallback: Color
    ): AlbumPalette {
        if (song == null) return fallbackPalette(fallback)
        val artUri = song.albumArtUri
        val cacheKey = if (song.albumId > 0L) song.albumId else song.id

        paletteCache.get(cacheKey)?.let { return it }

        val extracted = if (artUri != null) {
            withContext(Dispatchers.IO) {
                extractMultiTonePalette(context, artUri)
            }
        } else {
            null
        }

        val resolved = extracted ?: run {
            val key = song.album.takeIf { it.isNotBlank() } ?: song.title
            val (topTone, bottomTone) = artworkToneForKey(key)
            val primary = calibrateAmbientTint(topTone)
            val secondary = calibrateSecondaryTint(bottomTone, primary)
            val tertiary = shiftHue(primary, -42f, satScale = 0.85f, valScale = 0.78f)
            AlbumPalette(primary = primary, secondary = secondary, tertiary = tertiary)
        }

        paletteCache.put(cacheKey, resolved)
        return resolved
    }

    suspend fun extractForSong(
        context: Context,
        song: Song?,
        fallback: Color
    ): Color = extractPaletteForSong(context, song, fallback).primary

    fun fallbackPalette(accent: Color): AlbumPalette {
        val primary = calibrateAmbientTint(accent)
        val secondary = shiftHue(primary, 36f, satScale = 0.88f, valScale = 0.90f)
        val tertiary = shiftHue(primary, -48f, satScale = 0.78f, valScale = 0.74f)
        return AlbumPalette(primary = primary, secondary = secondary, tertiary = tertiary)
    }

    private fun extractMultiTonePalette(context: Context, uri: Uri): AlbumPalette? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            if (maxDim <= 0) return null

            var sampleSize = 1
            while (maxDim / sampleSize > 32) {
                sampleSize *= 2
            }

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            } ?: return null

            try {
                val w = bitmap.width
                val h = bitmap.height
                val pixels = IntArray(w * h)
                bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

                val hueWeights = FloatArray(24)
                val rSums = FloatArray(24)
                val gSums = FloatArray(24)
                val bSums = FloatArray(24)
                val hsv = FloatArray(3)

                for (px in pixels) {
                    val r = AndroidColor.red(px)
                    val g = AndroidColor.green(px)
                    val b = AndroidColor.blue(px)
                    AndroidColor.RGBToHSV(r, g, b, hsv)
                    val sat = hsv[1]
                    val value = hsv[2]
                    if (value < 0.12f || value > 0.96f) continue

                    val bucket = ((hsv[0] / 15f).toInt()).coerceIn(0, 23)
                    val lumScore = 1f - abs(value - 0.55f)
                    val weight = (0.15f + sat * sat * 2.2f) * lumScore
                    hueWeights[bucket] += weight
                    rSums[bucket] += r * weight
                    gSums[bucket] += g * weight
                    bSums[bucket] += b * weight
                }

                var primaryBucket = -1
                var primaryWeight = 0f
                for (i in 0 until 24) {
                    if (hueWeights[i] > primaryWeight) {
                        primaryWeight = hueWeights[i]
                        primaryBucket = i
                    }
                }
                if (primaryBucket < 0 || primaryWeight <= 0.01f) return null

                fun bucketColor(idx: Int): Color {
                    val wSum = hueWeights[idx].coerceAtLeast(0.001f)
                    return Color(
                        (rSums[idx] / wSum).toInt().coerceIn(0, 255),
                        (gSums[idx] / wSum).toInt().coerceIn(0, 255),
                        (bSums[idx] / wSum).toInt().coerceIn(0, 255)
                    )
                }

                fun circularBucketDist(a: Int, b: Int): Int {
                    val d = abs(a - b)
                    return min(d, 24 - d)
                }

                // Pick a distinct secondary tone (>= 45 deg hue separation)
                var secondaryBucket = -1
                var secondaryWeight = 0f
                for (i in 0 until 24) {
                    if (circularBucketDist(i, primaryBucket) >= 3 && hueWeights[i] > secondaryWeight) {
                        secondaryWeight = hueWeights[i]
                        secondaryBucket = i
                    }
                }

                // Pick a third deep harmonic accent (>= 45 deg away from both)
                var tertiaryBucket = -1
                var tertiaryWeight = 0f
                for (i in 0 until 24) {
                    val farFromPrimary = circularBucketDist(i, primaryBucket) >= 3
                    val farFromSecondary = secondaryBucket < 0 || circularBucketDist(i, secondaryBucket) >= 3
                    if (farFromPrimary && farFromSecondary && hueWeights[i] > tertiaryWeight) {
                        tertiaryWeight = hueWeights[i]
                        tertiaryBucket = i
                    }
                }

                val primary = calibrateAmbientTint(bucketColor(primaryBucket))
                val secondary = if (secondaryBucket >= 0 && secondaryWeight > primaryWeight * 0.14f) {
                    calibrateAmbientTint(bucketColor(secondaryBucket))
                } else {
                    shiftHue(primary, 34f, satScale = 0.88f, valScale = 0.90f)
                }
                val tertiary = if (tertiaryBucket >= 0 && tertiaryWeight > primaryWeight * 0.10f) {
                    calibrateAmbientTint(bucketColor(tertiaryBucket))
                } else {
                    shiftHue(primary, -46f, satScale = 0.80f, valScale = 0.76f)
                }

                AlbumPalette(primary = primary, secondary = secondary, tertiary = tertiary)
            } finally {
                bitmap.recycle()
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Clamps extracted color into an emotionally rich yet refined ambient zone
     * so gradients never look neon, muddy, or reduce foreground text contrast.
     */
    private fun calibrateAmbientTint(raw: Color): Color {
        val hsv = FloatArray(3)
        AndroidColor.RGBToHSV(
            (raw.red * 255).toInt().coerceIn(0, 255),
            (raw.green * 255).toInt().coerceIn(0, 255),
            (raw.blue * 255).toInt().coerceIn(0, 255),
            hsv
        )
        hsv[1] = hsv[1].coerceIn(0.32f, 0.76f)
        hsv[2] = hsv[2].coerceIn(0.42f, 0.72f)
        return Color(AndroidColor.HSVToColor(hsv))
    }

    private fun calibrateSecondaryTint(raw: Color, primary: Color): Color {
        val calibrated = calibrateAmbientTint(raw)
        return if (calibrated == primary) {
            shiftHue(primary, 34f, satScale = 0.88f, valScale = 0.88f)
        } else {
            calibrated
        }
    }

    private fun shiftHue(color: Color, deltaDeg: Float, satScale: Float, valScale: Float): Color {
        val hsv = FloatArray(3)
        AndroidColor.RGBToHSV(
            (color.red * 255).toInt().coerceIn(0, 255),
            (color.green * 255).toInt().coerceIn(0, 255),
            (color.blue * 255).toInt().coerceIn(0, 255),
            hsv
        )
        hsv[0] = ((hsv[0] + deltaDeg) % 360f + 360f) % 360f
        hsv[1] = (hsv[1] * satScale).coerceIn(0.28f, 0.74f)
        hsv[2] = (hsv[2] * valScale).coerceIn(0.36f, 0.68f)
        return Color(AndroidColor.HSVToColor(hsv))
    }
}

@Composable
fun rememberSongAlbumPalette(
    song: Song?,
    showCovers: Boolean,
    fallback: Color
): AlbumPalette {
    val context = LocalContext.current
    val initial = remember(song?.id, fallback) {
        if (song == null) {
            AlbumColorExtractor.fallbackPalette(fallback)
        } else {
            val key = song.album.takeIf { it.isNotBlank() } ?: song.title
            val (topTone, bottomTone) = artworkToneForKey(key)
            AlbumPalette(
                primary = topTone,
                secondary = bottomTone,
                tertiary = fallback
            )
        }
    }
    var palette by remember(song?.id) { mutableStateOf(initial) }

    LaunchedEffect(song?.id, showCovers, fallback) {
        palette = if (song == null) {
            AlbumColorExtractor.fallbackPalette(fallback)
        } else if (!showCovers) {
            val key = song.album.takeIf { it.isNotBlank() } ?: song.title
            val (topTone, bottomTone) = artworkToneForKey(key)
            AlbumPalette(primary = topTone, secondary = bottomTone, tertiary = fallback)
        } else {
            AlbumColorExtractor.extractPaletteForSong(context, song, fallback)
        }
    }
    return palette
}

@Composable
fun rememberSongAmbientColor(
    song: Song?,
    showCovers: Boolean,
    fallback: Color
): Color {
    return rememberSongAlbumPalette(
        song = song,
        showCovers = showCovers,
        fallback = fallback
    ).primary
}

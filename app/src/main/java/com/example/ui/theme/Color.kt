package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Perceptually calibrated Obsidian Dark (prevents white-on-black halation while keeping deep luxury contrast)
val DarkBg = Color(0xFF0B0B0F)
val DarkSurface = Color(0xFF131319)
val DarkSurfaceHigh = Color(0xFF1B1B24)
val DarkSurfaceHighest = Color(0xFF252531)
val DarkOutline = Color(0xFF2E2E3B)
val DarkOutlineSubtle = Color(0xFF1D1D26)
val DarkTextPrimary = Color(0xFFF2F1F6)
val DarkTextSecondary = Color(0xFF9898A8)

// True AMOLED Black (zero-emission pure black for OLED displays)
val OledBg = Color(0xFF000000)
val OledSurface = Color(0xFF0B0B0F)
val OledSurfaceHigh = Color(0xFF14141B)
val OledSurfaceHighest = Color(0xFF1E1E28)

// Editorial Paper Light (warm off-white ground with crisp ink hierarchy)
val LightBg = Color(0xFFF6F6F9)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceHigh = Color(0xFFEDEDF3)
val LightSurfaceHighest = Color(0xFFE2E2EB)
val LightOutline = Color(0xFFD5D5E0)
val LightOutlineSubtle = Color(0xFFE9E9F0)
val LightTextPrimary = Color(0xFF111116)
val LightTextSecondary = Color(0xFF636374)

// Curated Flagship Accents (calibrated chroma — timeless, never garish neon)
val AccentCrimson = Color(0xFFFA2D48)
val AccentEmerald = Color(0xFF1ED760)
val AccentAmber = Color(0xFFF59E0B)
val AccentCobalt = Color(0xFF3B82F6)
val AccentViolet = Color(0xFF8B5CF6)
val AccentSilver = Color(0xFFE4E4EA)

// Backwards-compatible default alias
val StudioCoral = AccentCrimson

data class AccentPreset(
    val id: String,
    val name: String,
    val primary: Color
)

val CuratedAccentPresets = listOf(
    AccentPreset("crimson", "Crimson", AccentCrimson),
    AccentPreset("emerald", "Emerald", AccentEmerald),
    AccentPreset("amber", "Amber", AccentAmber),
    AccentPreset("cobalt", "Cobalt", AccentCobalt),
    AccentPreset("violet", "Violet", AccentViolet),
    AccentPreset("silver", "Monochrome", AccentSilver)
)

/**
 * Curated deep vinyl-sleeve tones — rich, muted editorial pairs for tracks without embedded artwork.
 * Zero bitmap allocations, instant GPU render.
 */
val ArtworkFallbackTones: List<Pair<Color, Color>> = listOf(
    Color(0xFF432331) to Color(0xFF1A1016), // Burgundy Velvet
    Color(0xFF1E3246) to Color(0xFF0E1621), // Midnight Indigo
    Color(0xFF3E301F) to Color(0xFF19130D), // Warm Amber Wood
    Color(0xFF1C3A31) to Color(0xFF0D1A17), // Deep Forest Pine
    Color(0xFF312445) to Color(0xFF15101F), // Nocturne Plum
    Color(0xFF422723) to Color(0xFF1B1110), // Terracotta Dusk
    Color(0xFF23363E) to Color(0xFF0F181C), // Nordic Slate
    Color(0xFF382A36) to Color(0xFF171118)  // Smoked Rose
)

fun artworkToneForKey(key: String): Pair<Color, Color> {
    val idx = (key.hashCode() and 0x7FFFFFFF) % ArtworkFallbackTones.size
    return ArtworkFallbackTones[idx]
}

fun Color.relativeLuminanceFast(): Float = relativeLuminanceAccurate()

fun Color.relativeLuminanceAccurate(): Float {
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}

fun Color.contrastingOnColor(): Color {
    return if (relativeLuminanceAccurate() > 0.52f) Color(0xFF111116) else Color(0xFFFFFFFF)
}

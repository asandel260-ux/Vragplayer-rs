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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.MusicViewModel
import com.example.ui.components.EditorialPill
import com.example.ui.theme.CuratedAccentPresets
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkSurfaceHigh
import com.example.ui.theme.GlassAlertDialog
import com.example.ui.theme.GlassLevel
import com.example.ui.theme.LightBg
import com.example.ui.theme.LightSurface
import com.example.ui.theme.OledBg
import com.example.ui.theme.OledSurfaceHigh
import com.example.ui.theme.TabularCaption
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.contrastingOnColor
import com.example.ui.theme.glassScreenBackground
import com.example.ui.theme.liquidGlass
import com.example.util.DevicePerf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToSleepTimer: () -> Unit,
    onImportAudio: () -> Unit
) {
    val context = LocalContext.current

    val themeMode by viewModel.themeMode.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val showCovers by viewModel.showCovers.collectAsState()
    val glassmorphismEnabled by viewModel.glassmorphismEnabled.collectAsState()
    val pipEnabled by viewModel.pipEnabled.collectAsState()
    val crossfadeSeconds by viewModel.crossfadeSeconds.collectAsState()
    val skipSilence by viewModel.skipSilence.collectAsState()

    var showCustomHexDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(glassScreenBackground())
    ) {
        CenterAlignedTopAppBar(
            title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = glassScreenBackground()
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(key = "sec_appearance") {
                SettingsGroup(title = "Appearance") {
                    // Miniature Live Theme Swatch Cards
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Theme", style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                ThemePreviewSwatch(
                                    mode = mode,
                                    accent = accentColor,
                                    selected = themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    SettingsDivider()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Accent", style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { showCustomHexDialog = true }) {
                                Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Custom")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            CuratedAccentPresets.forEach { preset ->
                                val isSelected = accentColor.toHexUpper() == preset.primary.toHexUpper()
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(preset.primary)
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        )
                                        .clickable { viewModel.setAccentColor(preset.primary) }
                                        .testTag("accent_preset_${preset.id}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = preset.name,
                                            tint = preset.primary.contrastingOnColor(),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SettingsDivider()
                    SettingsToggleRow(
                        title = "Liquid Glass",
                        subtitle = if (DevicePerf.isLowEnd) {
                            "Light translucent tint & album gradients (tuned for smooth speed)"
                        } else {
                            "Frosted blur surfaces & dynamic album gradients"
                        },
                        checked = glassmorphismEnabled,
                        onCheckedChange = { viewModel.setGlassmorphismEnabled(it) },
                        testTag = "settings_glassmorphism_toggle"
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "Show Album Covers",
                        checked = showCovers,
                        onCheckedChange = { viewModel.setShowCovers(it) }
                    )
                }
            }

            item(key = "sec_playback") {
                SettingsGroup(title = "Playback") {
                    SettingsNavRow(
                        icon = Icons.Default.GraphicEq,
                        title = "Equalizer",
                        onClick = onNavigateToEqualizer,
                        testTag = "settings_eq_row"
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        icon = Icons.Default.Bedtime,
                        title = "Sleep Timer",
                        onClick = onNavigateToSleepTimer,
                        testTag = "settings_sleep_row"
                    )
                    SettingsDivider()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Crossfade", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (crossfadeSeconds == 0) "Off" else "${crossfadeSeconds}s",
                                style = TabularCaption,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0, 2, 4, 6, 8).forEach { sec ->
                                EditorialPill(
                                    label = if (sec == 0) "Off" else "${sec}s",
                                    selected = crossfadeSeconds == sec,
                                    onClick = { viewModel.setCrossfadeSeconds(sec) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    SettingsDivider()
                    SettingsToggleRow(
                        title = "Skip Silence",
                        checked = skipSilence,
                        onCheckedChange = { viewModel.setSkipSilence(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "Picture-in-Picture",
                        checked = pipEnabled,
                        onCheckedChange = { viewModel.setPipEnabled(it) }
                    )
                }
            }

            item(key = "sec_library") {
                SettingsGroup(title = "Library") {
                    SettingsNavRow(
                        icon = Icons.Default.FileOpen,
                        title = "Import Audio Files",
                        onClick = onImportAudio,
                        testTag = "settings_import_audio"
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        icon = Icons.Default.Refresh,
                        title = "Rescan Music",
                        onClick = {
                            viewModel.loadMedia()
                            viewModel.showMessage("Rescanning library")
                        }
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        icon = Icons.Default.CleaningServices,
                        title = "Clear Artwork Cache",
                        onClick = { viewModel.clearImageCache(context) },
                        testTag = "settings_clear_cache"
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showCustomHexDialog) {
        var hexInput by remember { mutableStateOf(accentColor.toHexUpper()) }
        val parsed = remember(hexInput) {
            try {
                val clean = if (hexInput.startsWith("#")) hexInput else "#$hexInput"
                if (clean.length == 7) Color(android.graphics.Color.parseColor(clean)) else null
            } catch (_: Exception) {
                null
            }
        }

        GlassAlertDialog(
            onDismissRequest = { showCustomHexDialog = false },
            title = { Text("Custom Color") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(parsed ?: accentColor)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { hexInput = it.take(7) },
                        label = { Text("Hex") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        parsed?.let { viewModel.setAccentColor(it) }
                        showCustomHexDialog = false
                    },
                    enabled = parsed != null
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomHexDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ThemePreviewSwatch(
    mode: ThemeMode,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val bgBrush = when (mode) {
        ThemeMode.DARK_STUDIO -> Brush.verticalGradient(listOf(DarkBg, DarkSurfaceHigh))
        ThemeMode.OLED_BLACK -> Brush.verticalGradient(listOf(OledBg, OledSurfaceHigh))
        ThemeMode.LIGHT_STUDIO -> Brush.verticalGradient(listOf(LightSurface, LightBg))
        ThemeMode.SYSTEM_DYNAMIC -> Brush.linearGradient(listOf(DarkBg, LightBg))
    }
    val barColor = when (mode) {
        ThemeMode.LIGHT_STUDIO -> Color(0xFF1A1A22).copy(alpha = 0.16f)
        else -> Color.White.copy(alpha = 0.16f)
    }

    Column(
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(shape)
                .background(bgBrush)
                .border(
                    width = if (selected) 2.dp else 0.75.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    shape = shape
                )
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(barColor)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(barColor)
                    )
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                }
            }
        }
        Text(
            text = mode.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(shape = shape, level = GlassLevel.Card)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    testTag: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    testTag: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun Color.toHexUpper(): String {
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    return String.format("#%02X%02X%02X", r, g, b)
}

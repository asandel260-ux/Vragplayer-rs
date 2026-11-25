package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.EqualizerPreset
import com.example.ui.MusicViewModel
import com.example.ui.components.EditorialPill
import com.example.ui.components.VerticalEqFader
import com.example.ui.components.VragScrubber
import com.example.ui.theme.GlassLevel
import com.example.ui.theme.TabularCaption
import com.example.ui.theme.glassScreenBackground
import com.example.ui.theme.liquidGlass
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerContent(viewModel: MusicViewModel, onBack: () -> Unit) {
    val eqEnabled by viewModel.eqEnabled.collectAsState()
    val selectedPreset by viewModel.equalizerPreset.collectAsState()
    val bandLevelsList by viewModel.equalizerBands.collectAsState()
    val bassBoost by viewModel.bassBoostStrength.collectAsState()

    val bandLevels = remember(bandLevelsList) {
        mutableStateListOf<Float>().apply { addAll(bandLevelsList) }
    }
    val freqLabels = remember { listOf("60Hz", "230Hz", "910Hz", "3kHz", "14kHz") }
    val cardShape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(glassScreenBackground())
            .verticalScroll(rememberScrollState())
    ) {
        CenterAlignedTopAppBar(
            title = { Text("Equalizer", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                Switch(
                    checked = eqEnabled,
                    onCheckedChange = { viewModel.setEqEnabled(it) },
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .testTag("eq_master_switch")
                )
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = glassScreenBackground()
            )
        )

        // 1. Live Frequency Response Curve Console
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .liquidGlass(shape = cardShape, level = GlassLevel.Card)
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (eqEnabled) selectedPreset else "Bypassed",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (eqEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        viewModel.setEqualizerPreset(EqualizerPreset.FLAT.label)
                        viewModel.setBassBoostStrength(0)
                    }
                ) {
                    Text("Reset", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val curveColor = if (eqEnabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            val gridColor = MaterialTheme.colorScheme.outlineVariant

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
            ) {
                val padX = 12.dp.toPx()
                val usableW = (size.width - padX * 2).coerceAtLeast(1f)
                val midY = size.height / 2f
                val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

                // +12, 0, -12 dB reference grid lines
                drawLine(gridColor.copy(alpha = 0.45f), Offset(0f, size.height * 0.14f), Offset(size.width, size.height * 0.14f), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                drawLine(gridColor, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 1.dp.toPx())
                drawLine(gridColor.copy(alpha = 0.45f), Offset(0f, size.height * 0.86f), Offset(size.width, size.height * 0.86f), strokeWidth = 1.dp.toPx(), pathEffect = dash)

                val points = bandLevels.mapIndexed { idx, db ->
                    val x = if (bandLevels.size > 1) padX + idx * (usableW / (bandLevels.size - 1)) else size.width / 2f
                    val norm = (db.coerceIn(-12f, 12f) / 12f)
                    val y = midY - norm * (size.height * 0.38f)
                    Offset(x, y)
                }

                if (points.size >= 2) {
                    val linePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 0 until points.lastIndex) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val cx = (p0.x + p1.x) / 2f
                            cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                        }
                    }
                    val fillPath = Path().apply {
                        addPath(linePath)
                        lineTo(points.last().x, size.height)
                        lineTo(points.first().x, size.height)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(listOf(curveColor.copy(alpha = 0.24f), Color.Transparent))
                    )
                    drawPath(
                        path = linePath,
                        color = curveColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                points.forEach { pt ->
                    drawCircle(color = curveColor.copy(alpha = 0.22f), radius = 7.dp.toPx(), center = pt)
                    drawCircle(color = curveColor, radius = 3.5.dp.toPx(), center = pt)
                }
            }
        }

        // 2. Editorial Preset Pills (tapping a preset auto-enables EQ cleanly)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(EqualizerPreset.entries) { preset ->
                EditorialPill(
                    label = preset.label,
                    selected = eqEnabled && selectedPreset == preset.label,
                    onClick = {
                        if (!eqEnabled) viewModel.setEqEnabled(true)
                        viewModel.setEqualizerPreset(preset.label)
                    }
                )
            }
        }

        // 3. True Vertical 5-Band Studio Graphic EQ Fader Console
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .liquidGlass(shape = cardShape, level = GlassLevel.Card)
                .padding(horizontal = 12.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                freqLabels.forEachIndexed { index, label ->
                    val currentDb = bandLevels.getOrElse(index) { 0f }
                    val rounded = currentDb.roundToInt()

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${if (rounded > 0) "+" else ""}$rounded",
                            style = TabularCaption,
                            fontWeight = FontWeight.SemiBold,
                            color = if (eqEnabled && rounded != 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        VerticalEqFader(
                            valueDb = currentDb,
                            enabled = eqEnabled,
                            onValueChange = { bandLevels[index] = it },
                            onValueChangeFinished = {
                                viewModel.setEqualizerPreset("Custom")
                                viewModel.setEqualizerBands(bandLevels.toList())
                            }
                        )

                        Text(
                            text = label,
                            style = TabularCaption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 4. Precision Bass Boost Control
        Spacer(modifier = Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .liquidGlass(shape = cardShape, level = GlassLevel.Card)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bass Boost",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${bassBoost / 10}%",
                    style = TabularCaption,
                    fontWeight = FontWeight.Bold,
                    color = if (eqEnabled && bassBoost > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            VragScrubber(
                value = (bassBoost / 1000f).coerceIn(0f, 1f),
                onValueChange = { frac ->
                    if (!eqEnabled) viewModel.setEqEnabled(true)
                    viewModel.setBassBoostStrength((frac * 1000f).roundToInt())
                },
                onValueChangeFinished = {},
                activeColor = if (eqEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

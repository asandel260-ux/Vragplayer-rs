package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Song
import com.example.ui.MusicViewModel
import com.example.ui.components.TrackArtwork
import com.example.ui.theme.GlassAlertDialog
import com.example.ui.theme.GlassLevel
import com.example.ui.theme.TabularCaption
import com.example.ui.theme.TabularDisplay
import com.example.ui.theme.glassScreenBackground
import com.example.ui.theme.liquidGlass

@Composable
fun StatsContent(
    songs: List<Song>,
    viewModel: MusicViewModel,
    onSongClick: (Song, List<Song>, String) -> Unit
) {
    val username by viewModel.username.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val showCovers by viewModel.showCovers.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var editNameText by remember { mutableStateOf("") }

    val topTracks = remember(songs, stats.songPlayCounts) {
        val songMap = songs.associateBy { it.id }
        stats.songPlayCounts.entries
            .sortedByDescending { it.value }
            .mapNotNull { (id, count) -> songMap[id]?.let { it to count } }
            .take(5)
    }

    val formatBreakdown = remember(songs) {
        songs.groupingBy { it.formatLabel }.eachCount().entries.sortedByDescending { it.value }
    }

    val twoHourBuckets = remember(stats.hourlyActivity) {
        (0 until 24 step 2).map { h ->
            stats.hourlyActivity.getOrElse(h) { 0 } + stats.hourlyActivity.getOrElse(h + 1) { 0 }
        }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val cardShape = RoundedCornerShape(20.dp)
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(glassScreenBackground()),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = statusTop + 14.dp,
            bottom = 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Editorial Profile Header
        item(key = "stats_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .gpuScrollHeader(
                        listState = listState,
                        headerIndex = 0,
                        parallaxFactor = 0.18f,
                        fadeDistancePx = 200f
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            editNameText = username
                            showEditDialog = true
                        }
                ) {
                    Text(
                        text = username,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit name",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = {
                        viewModel.loadMedia()
                        viewModel.showMessage("Library refreshed")
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("stats_refresh_button")
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Clean 2x2 Summary Metrics
        item(key = "stats_grid_1") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    label = "Songs",
                    value = songs.size.toString(),
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Plays",
                    value = stats.lifetimePlays.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item(key = "stats_grid_2") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    label = "Today (min)",
                    value = stats.todayPlayTimeMinutes.toInt().toString(),
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Favorites",
                    value = likedSongs.size.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Listening Streak Banner
        item(key = "stats_streak") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(shape = cardShape, level = GlassLevel.Card)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Listening Streak",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${stats.totalPlayTimeMinutes.toInt()} total minutes listened",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${stats.currentStreakDays}d",
                    style = TabularDisplay,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Listening Activity Chart
        item(key = "stats_hours") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(shape = cardShape, level = GlassLevel.Card)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Listening Activity",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val maxVal = (twoHourBuckets.maxOrNull() ?: 0).coerceAtLeast(1)
                val primaryColor = MaterialTheme.colorScheme.primary
                val emptyColor = MaterialTheme.colorScheme.surfaceContainerHighest
                val guideColor = MaterialTheme.colorScheme.outlineVariant

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                ) {
                    // Subtle midpoint reference line
                    drawLine(
                        color = guideColor.copy(alpha = 0.5f),
                        start = Offset(0f, size.height * 0.5f),
                        end = Offset(size.width, size.height * 0.5f),
                        strokeWidth = 0.75.dp.toPx()
                    )

                    val count = twoHourBuckets.size
                    val gap = 8.dp.toPx()
                    val barW = ((size.width - gap * (count - 1)) / count).coerceAtLeast(4f)
                    val radius = CornerRadius(5.dp.toPx(), 5.dp.toPx())

                    twoHourBuckets.forEachIndexed { idx, value ->
                        val frac = if (value == 0) 0.10f else (0.20f + 0.80f * (value.toFloat() / maxVal))
                        val barH = size.height * frac
                        val x = idx * (barW + gap)
                        val y = size.height - barH

                        if (value == 0) {
                            drawRoundRect(
                                color = emptyColor,
                                topLeft = Offset(x, y),
                                size = Size(barW, barH),
                                cornerRadius = radius
                            )
                        } else {
                            val topAlpha = if (value == maxVal) 1f else 0.72f
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = topAlpha),
                                        primaryColor.copy(alpha = topAlpha * 0.42f)
                                    ),
                                    startY = y,
                                    endY = size.height
                                ),
                                topLeft = Offset(x, y),
                                size = Size(barW, barH),
                                cornerRadius = radius
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("12 AM", style = TabularCaption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("6 AM", style = TabularCaption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("12 PM", style = TabularCaption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("6 PM", style = TabularCaption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("11 PM", style = TabularCaption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Playable Most Played List (tapping plays the song!)
        if (topTracks.isNotEmpty()) {
            item(key = "stats_top_tracks") {
                val topSongList = remember(topTracks) { topTracks.map { it.first } }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(shape = cardShape, level = GlassLevel.Card)
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Most Played",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                    topTracks.forEachIndexed { idx, (song, playCount) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSongClick(song, topSongList, "Most Played") }
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "${idx + 1}",
                                style = TabularCaption,
                                fontWeight = FontWeight.Bold,
                                color = if (idx == 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(16.dp)
                            )
                            TrackArtwork(
                                song = song,
                                showCovers = showCovers,
                                sizePx = 96,
                                iconSize = 18.dp,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(42.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "$playCount",
                                style = TabularCaption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Audio Formats Breakdown
        if (formatBreakdown.isNotEmpty()) {
            item(key = "stats_formats") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    formatBreakdown.take(4).forEach { (fmt, count) ->
                        val pillShape = RoundedCornerShape(10.dp)
                        Text(
                            text = "$fmt · $count",
                            style = TabularCaption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .liquidGlass(shape = pillShape, level = GlassLevel.Subtle)
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        GlassAlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Display Name") },
            text = {
                OutlinedTextField(
                    value = editNameText,
                    onValueChange = { editNameText = it },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setUsername(editNameText)
                        showEditDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .liquidGlass(shape = shape, level = GlassLevel.Card)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            style = TabularDisplay,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.MusicViewModel
import com.example.ui.components.VragScrubber
import com.example.ui.components.formatCountdown
import com.example.ui.theme.GlassLevel
import com.example.ui.theme.TabularCaption
import com.example.ui.theme.TabularDisplay
import com.example.ui.theme.glassScreenBackground
import com.example.ui.theme.liquidGlass
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerContent(viewModel: MusicViewModel, onBack: () -> Unit) {
    val remainingMs by viewModel.sleepTimerRemainingMs.collectAsState()
    val sleepAfterTrack by viewModel.sleepAfterCurrentTrack.collectAsState()
    val presets = remember { listOf(10, 15, 30, 45, 60) }
    var customMinutes by remember { mutableFloatStateOf(20f) }

    val isActive = remainingMs > 0L || sleepAfterTrack
    val cardShape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(glassScreenBackground())
            .verticalScroll(rememberScrollState())
    ) {
        CenterAlignedTopAppBar(
            title = { Text("Sleep Timer", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = glassScreenBackground()
            )
        )

        // Countdown Status
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .liquidGlass(shape = cardShape, level = GlassLevel.Card)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = when {
                    remainingMs > 0L -> formatCountdown(remainingMs)
                    sleepAfterTrack -> "End of song"
                    else -> "Off"
                },
                style = TabularDisplay,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )

            if (isActive) {
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { viewModel.cancelSleepTimer() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = CircleShape,
                    modifier = Modifier.testTag("sleep_timer_cancel")
                ) {
                    Text("Cancel Timer", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Pause after current song
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .liquidGlass(shape = cardShape, level = GlassLevel.Card)
                .clickable { viewModel.setSleepAfterCurrentTrack(!sleepAfterTrack) }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pause after current song",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = sleepAfterTrack,
                onCheckedChange = { viewModel.setSleepAfterCurrentTrack(it) }
            )
        }

        // Preset Durations
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .liquidGlass(shape = cardShape, level = GlassLevel.Card)
        ) {
            presets.forEachIndexed { idx, mins ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setSleepTimer(mins) }
                        .padding(horizontal = 18.dp, vertical = 15.dp)
                        .testTag("sleep_preset_$mins"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (mins == 60) "1 hour" else "$mins minutes",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Set",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (idx < presets.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }

        // Custom Duration with precision VragScrubber
        Spacer(modifier = Modifier.height(14.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .liquidGlass(shape = cardShape, level = GlassLevel.Card)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val minsInt = customMinutes.roundToInt().coerceIn(5, 120)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Custom", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "$minsInt min",
                    style = TabularCaption,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            VragScrubber(
                value = ((customMinutes - 5f) / 115f).coerceIn(0f, 1f),
                onValueChange = { frac ->
                    customMinutes = ((5f + frac * 115f) / 5f).roundToInt() * 5f
                },
                onValueChangeFinished = {},
                activeColor = MaterialTheme.colorScheme.primary,
                inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Button(
                onClick = { viewModel.setSleepTimer(minsInt) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background
                )
            ) {
                Text("Start $minsInt min timer", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

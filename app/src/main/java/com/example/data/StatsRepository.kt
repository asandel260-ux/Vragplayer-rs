package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ListeningStats(
    val lifetimePlays: Int = 0,
    val totalPlayTimeMinutes: Float = 0f,
    val todayPlayTimeMinutes: Float = 0f,
    val currentStreakDays: Int = 0,
    val hourlyActivity: List<Int> = List(24) { 0 },
    val songPlayCounts: Map<Long, Int> = emptyMap()
)

class StatsRepository internal constructor(
    private val prefs: SharedPreferences
) {

    constructor(context: Context) : this(
        context.getSharedPreferences("vrag_prefs", Context.MODE_PRIVATE)
    )

    private val _stats = MutableStateFlow(loadFromPrefs())
    val stats: StateFlow<ListeningStats> = _stats.asStateFlow()

    private var bufferedSeconds = 0
    private var bufferedDate: String = todayString()

    private fun dateFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun todayString(nowMs: Long = System.currentTimeMillis()): String =
        dateFormat().format(Date(nowMs))

    private fun yesterdayString(nowMs: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return dateFormat().format(cal.time)
    }

    private fun loadFromPrefs(): ListeningStats {
        val today = todayString()
        val lastDate = prefs.getString("last_played_date", "") ?: ""
        val savedTodayMin = if (lastDate == today) {
            prefs.getFloat("today_play_time_minutes", 0f)
        } else {
            0f
        }

        val streakDate = prefs.getString("stats_streak_last_date", "") ?: ""
        val savedStreak = prefs.getInt("stats_streak_days", 0)
        val effectiveStreak = if (streakDate == today || streakDate == yesterdayString()) {
            savedStreak
        } else {
            0
        }

        val hourlyRaw = prefs.getString("stats_hourly_activity", "") ?: ""
        val hourlyList = if (hourlyRaw.isNotEmpty()) {
            val parsed = hourlyRaw.split(",").map { it.toIntOrNull() ?: 0 }
            if (parsed.size == 24) parsed else List(24) { parsed.getOrElse(it) { 0 } }
        } else {
            List(24) { 0 }
        }

        val countsRaw = prefs.getString("stats_song_play_counts", "") ?: ""
        val playCounts = buildMap<Long, Int> {
            if (countsRaw.isNotEmpty()) {
                countsRaw.split(";").forEach { entry ->
                    val kv = entry.split(":")
                    if (kv.size == 2) {
                        val id = kv[0].toLongOrNull()
                        val count = kv[1].toIntOrNull()
                        if (id != null && count != null && count > 0) put(id, count)
                    }
                }
            }
        }

        return ListeningStats(
            lifetimePlays = prefs.getInt("lifetime_plays", 0),
            totalPlayTimeMinutes = prefs.getFloat("total_play_time_minutes", 0f),
            todayPlayTimeMinutes = savedTodayMin,
            currentStreakDays = effectiveStreak,
            hourlyActivity = hourlyList,
            songPlayCounts = playCounts
        )
    }

    fun recordTrackPlay(songId: Long, nowMs: Long = System.currentTimeMillis()) {
        val current = _stats.value
        val today = todayString(nowMs)
        val yesterday = yesterdayString(nowMs)
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)

        val updatedHourly = current.hourlyActivity.toMutableList()
        updatedHourly[hour] = updatedHourly[hour] + 1

        val updatedCounts = current.songPlayCounts.toMutableMap()
        updatedCounts[songId] = (updatedCounts[songId] ?: 0) + 1

        val prevStreakDate = prefs.getString("stats_streak_last_date", "") ?: ""
        val newStreak = when (prevStreakDate) {
            today -> current.currentStreakDays.coerceAtLeast(1)
            yesterday -> current.currentStreakDays + 1
            else -> 1
        }

        val next = current.copy(
            lifetimePlays = current.lifetimePlays + 1,
            currentStreakDays = newStreak,
            hourlyActivity = updatedHourly,
            songPlayCounts = updatedCounts
        )
        _stats.value = next

        val countsSerialized = updatedCounts.entries
            .sortedByDescending { it.value }
            .take(200)
            .joinToString(";") { "${it.key}:${it.value}" }

        prefs.edit()
            .putInt("lifetime_plays", next.lifetimePlays)
            .putInt("stats_streak_days", newStreak)
            .putString("stats_streak_last_date", today)
            .putString("stats_hourly_activity", updatedHourly.joinToString(","))
            .putString("stats_song_play_counts", countsSerialized)
            .apply()
    }

    /**
     * Buffers listening seconds without emitting StateFlow updates every second
     * (prevents 1Hz UI recomposition across screens on low-end phones).
     * Emits & persists cleanly every 30 seconds or when [flushPlayTime] is called.
     */
    fun addPlaySecond(nowMs: Long = System.currentTimeMillis()) {
        val today = todayString(nowMs)
        if (today != bufferedDate && bufferedSeconds > 0) {
            flushPlayTime(bufferedDate)
        }
        bufferedDate = today
        bufferedSeconds++
        if (bufferedSeconds >= 30) {
            flushPlayTime(today)
        }
    }

    fun flushPlayTime(today: String = bufferedDate) {
        if (bufferedSeconds <= 0) return
        val deltaMin = bufferedSeconds / 60f
        bufferedSeconds = 0

        val current = _stats.value
        val lastDate = prefs.getString("last_played_date", "") ?: ""
        val baseToday = if (lastDate == today) current.todayPlayTimeMinutes else 0f

        val next = current.copy(
            totalPlayTimeMinutes = current.totalPlayTimeMinutes + deltaMin,
            todayPlayTimeMinutes = baseToday + deltaMin
        )
        _stats.value = next

        prefs.edit()
            .putString("last_played_date", today)
            .putFloat("total_play_time_minutes", next.totalPlayTimeMinutes)
            .putFloat("today_play_time_minutes", next.todayPlayTimeMinutes)
            .apply()
    }
}

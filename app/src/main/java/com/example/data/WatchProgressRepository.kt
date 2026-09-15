package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.data.models.EpisodeItem
import com.example.data.models.SeasonItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class WatchProgress(
    val mediaId: String,
    val episodeId: String,
    val seasonNumber: Int = 1,
    val episodeNumber: Int = 1,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isCompleted: Boolean = false,
    val lastWatched: Long = System.currentTimeMillis()
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val isWatching: Boolean
        get() = !isCompleted && positionMs > 5000L && (durationMs <= 0L || progressFraction < 0.90f)

    fun formattedPosition(): String {
        val totalSeconds = (positionMs / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hours = minutes / 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes % 60, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    fun formattedDuration(): String {
        val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hours = minutes / 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes % 60, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}

class WatchProgressRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("watch_progress_prefs", Context.MODE_PRIVATE)

    private val _progressMap = MutableStateFlow<Map<String, WatchProgress>>(loadAllProgress())
    val progressFlow: StateFlow<Map<String, WatchProgress>> = _progressMap.asStateFlow()

    companion object {
        fun buildEpisodeKey(mediaId: String, seasonNumber: Int, episodeNumber: Int): String {
            return "${mediaId}_s${seasonNumber}_e${episodeNumber}"
        }

        fun buildMovieKey(movieId: String): String {
            return "movie_${movieId}"
        }
    }

    private fun loadAllProgress(): Map<String, WatchProgress> {
        val result = mutableMapOf<String, WatchProgress>()
        try {
            val all = prefs.all
            for ((key, value) in all) {
                if (value is String) {
                    try {
                        val json = JSONObject(value)
                        val wp = WatchProgress(
                            mediaId = json.optString("mediaId", ""),
                            episodeId = json.optString("episodeId", key),
                            seasonNumber = json.optInt("seasonNumber", 1),
                            episodeNumber = json.optInt("episodeNumber", 1),
                            positionMs = json.optLong("positionMs", 0L),
                            durationMs = json.optLong("durationMs", 0L),
                            isCompleted = json.optBoolean("isCompleted", false),
                            lastWatched = json.optLong("lastWatched", System.currentTimeMillis())
                        )
                        result[key] = wp
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        return result
    }

    fun saveProgress(
        mediaId: String,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeId: String,
        positionMs: Long,
        durationMs: Long,
        isCompleted: Boolean = false
    ) {
        if (mediaId.isBlank()) return
        val key = buildEpisodeKey(mediaId, seasonNumber, episodeNumber)
        val shouldMarkCompleted = isCompleted || (durationMs > 10_000L && positionMs >= durationMs * 0.90f)

        val wp = WatchProgress(
            mediaId = mediaId,
            episodeId = episodeId.ifBlank { key },
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            positionMs = if (shouldMarkCompleted) 0L else positionMs,
            durationMs = durationMs,
            isCompleted = shouldMarkCompleted,
            lastWatched = System.currentTimeMillis()
        )

        val json = JSONObject().apply {
            put("mediaId", wp.mediaId)
            put("episodeId", wp.episodeId)
            put("seasonNumber", wp.seasonNumber)
            put("episodeNumber", wp.episodeNumber)
            put("positionMs", wp.positionMs)
            put("durationMs", wp.durationMs)
            put("isCompleted", wp.isCompleted)
            put("lastWatched", wp.lastWatched)
        }

        prefs.edit().putString(key, json.toString()).apply()

        // Update in-memory state flow
        val current = _progressMap.value.toMutableMap()
        current[key] = wp
        if (episodeId.isNotBlank() && episodeId != key) {
            current[episodeId] = wp
        }
        _progressMap.value = current
    }

    fun toggleEpisodeWatched(
        mediaId: String,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeId: String
    ) {
        val key = buildEpisodeKey(mediaId, seasonNumber, episodeNumber)
        val existing = _progressMap.value[key] ?: _progressMap.value[episodeId]
        val newCompleted = !(existing?.isCompleted ?: false)

        val wp = WatchProgress(
            mediaId = mediaId,
            episodeId = episodeId.ifBlank { key },
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            positionMs = 0L,
            durationMs = existing?.durationMs ?: 0L,
            isCompleted = newCompleted,
            lastWatched = System.currentTimeMillis()
        )

        val json = JSONObject().apply {
            put("mediaId", wp.mediaId)
            put("episodeId", wp.episodeId)
            put("seasonNumber", wp.seasonNumber)
            put("episodeNumber", wp.episodeNumber)
            put("positionMs", wp.positionMs)
            put("durationMs", wp.durationMs)
            put("isCompleted", wp.isCompleted)
            put("lastWatched", wp.lastWatched)
        }

        prefs.edit().putString(key, json.toString()).apply()

        val current = _progressMap.value.toMutableMap()
        current[key] = wp
        if (episodeId.isNotBlank() && episodeId != key) {
            current[episodeId] = wp
        }
        _progressMap.value = current
    }

    fun getEpisodeProgress(
        mediaId: String,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeId: String? = null
    ): WatchProgress? {
        val key = buildEpisodeKey(mediaId, seasonNumber, episodeNumber)
        return _progressMap.value[key] ?: (if (!episodeId.isNullOrBlank()) _progressMap.value[episodeId] else null)
    }

    /**
     * Determines which episode should be resumed for a given series.
     * 1. If an episode is currently in progress ("assistindo"), return that episode.
     * 2. Otherwise, find the next uncompleted episode after the last watched one.
     * 3. Fallback to Season 1, Episode 1.
     */
    fun findResumeEpisode(
        mediaId: String,
        seasons: List<SeasonItem>
    ): Triple<SeasonItem, EpisodeItem, Long>? {
        if (seasons.isEmpty()) return null

        // 1. Check for any in-progress episode sorted by most recently watched
        var latestInProgress: Triple<SeasonItem, EpisodeItem, WatchProgress>? = null
        for (season in seasons) {
            for (ep in season.episodes) {
                val progress = getEpisodeProgress(mediaId, season.seasonNumber, ep.episodeNumber, ep.id)
                if (progress != null && progress.isWatching) {
                    if (latestInProgress == null || progress.lastWatched > latestInProgress.third.lastWatched) {
                        latestInProgress = Triple(season, ep, progress)
                    }
                }
            }
        }

        if (latestInProgress != null) {
            return Triple(latestInProgress.first, latestInProgress.second, latestInProgress.third.positionMs)
        }

        // 2. Find the first uncompleted episode after the last watched one
        var lastCompletedSeason: SeasonItem? = null
        var lastCompletedEp: EpisodeItem? = null
        var lastCompletedTimestamp = 0L

        for (season in seasons) {
            for (ep in season.episodes) {
                val progress = getEpisodeProgress(mediaId, season.seasonNumber, ep.episodeNumber, ep.id)
                if (progress != null && progress.isCompleted) {
                    if (progress.lastWatched > lastCompletedTimestamp) {
                        lastCompletedTimestamp = progress.lastWatched
                        lastCompletedSeason = season
                        lastCompletedEp = ep
                    }
                }
            }
        }

        if (lastCompletedSeason != null && lastCompletedEp != null) {
            // Find next episode
            val sIndex = seasons.indexOf(lastCompletedSeason)
            if (sIndex >= 0) {
                val sObj = seasons[sIndex]
                val epIndex = sObj.episodes.indexOf(lastCompletedEp)
                if (epIndex >= 0 && epIndex + 1 < sObj.episodes.size) {
                    return Triple(sObj, sObj.episodes[epIndex + 1], 0L)
                } else if (sIndex + 1 < seasons.size && seasons[sIndex + 1].episodes.isNotEmpty()) {
                    return Triple(seasons[sIndex + 1], seasons[sIndex + 1].episodes.first(), 0L)
                }
            }
        }

        // 3. Fallback to first episode of first season
        val firstSeason = seasons.firstOrNull() ?: return null
        val firstEp = firstSeason.episodes.firstOrNull() ?: return null
        return Triple(firstSeason, firstEp, 0L)
    }

    fun getSeriesStats(mediaId: String, seasons: List<SeasonItem>): Pair<Int, Int> {
        val total = seasons.sumOf { it.episodes.size }
        var completed = 0
        for (season in seasons) {
            for (ep in season.episodes) {
                val progress = getEpisodeProgress(mediaId, season.seasonNumber, ep.episodeNumber, ep.id)
                if (progress?.isCompleted == true) {
                    completed++
                }
            }
        }
        return Pair(completed, total)
    }
}

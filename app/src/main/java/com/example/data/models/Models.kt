package com.example.data.models

const val OFFLINE_FALLBACK_URL = "https://www.revistacircuito.com/wp-content/uploads/2022/06/19083931_140186_GDO.jpg"

fun isOfflineFallbackUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val trimmed = url.trim()
    return trimmed.equals(OFFLINE_FALLBACK_URL, ignoreCase = true) ||
           trimmed.contains("19083931_140186_GDO.jpg", ignoreCase = true) ||
           trimmed.contains("revistacircuito.com", ignoreCase = true)
}

data class MatchItem(
    val id: String,
    val homeTeam: String,
    val homeLogoUrl: String,
    val awayTeam: String,
    val awayLogoUrl: String,
    val championship: String,
    val time: String,
    val dateTag: String,
    val detailUrl: String,
    val channels: List<ChannelOption> = emptyList(),
    val isLiveNow: Boolean = false,
    val isFavorite: Boolean = false
) {
    val displayTitle: String
        get() = "$homeTeam x $awayTeam"
}

data class ChannelOption(
    val id: String,
    val name: String,
    val pageUrl: String,
    val resolvedStreamUrl: String? = null,
    val embedUrl: String? = null,
    val headers: Map<String, String> = emptyMap()
)

data class PlayableVideo(
    val id: String,
    val title: String,
    val subtitle: String,
    val streamUrl: String,
    val posterUrl: String? = null,
    val isLive: Boolean = true,
    val headers: Map<String, String> = emptyMap(),
    val embedUrl: String? = null,
    val isFavorite: Boolean = false,
    val forceWebPlayer: Boolean = false,
    val category: String? = null,
    val isWorking: Boolean = true
)

val PlayableVideo.isSeries: Boolean
    get() {
        val cat = category?.lowercase() ?: ""
        val sub = subtitle.lowercase()
        val vidId = id.lowercase()
        val t = title.lowercase()
        return cat.contains("série") || cat.contains("serie") || cat.contains("series") ||
                cat.contains("novela") || cat.contains("anime") || cat.contains("tv") ||
                cat.contains("temporada") ||
                sub.contains("série") || sub.contains("serie") || sub.contains("series") ||
                sub.contains("temporada") || sub.contains("episódio") || sub.contains("episodio") ||
                vidId.startsWith("series") || vidId.contains("_series_") ||
                t.contains("temporada") || t.contains("season") ||
                t.contains(Regex("""\b(s\d{1,2}\s*e\d{1,2}|t\d{1,2}\s*:\s*e\d{1,2}|ep\s*\d+)\b""", RegexOption.IGNORE_CASE))
    }

enum class StreamFormat {
    HLS_M3U8,
    MP4,
    DASH_MPD,
    WEB_EMBED
}

data class ChannelTestSummary(
    val total: Int = 0,
    val workingCount: Int = 0,
    val offlineCount: Int = 0,
    val newlyOfflineChannels: List<PlayableVideo> = emptyList(),
    val offlineChannels: List<PlayableVideo> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

enum class MediaContentType {
    MOVIE,
    SERIES
}

data class EpisodeItem(
    val id: String = "",
    val episodeNumber: Int = 1,
    val title: String = "",
    val streamUrl: String = "",
    val isWebPlayer: Boolean = false,
    val duration: String? = null,
    val synopsis: String? = null
)

data class SeasonItem(
    val seasonNumber: Int = 1,
    val title: String = "Temporada $seasonNumber",
    val episodes: List<EpisodeItem> = emptyList()
)

data class MediaItem(
    val id: String,
    val title: String,
    val type: MediaContentType,
    val coverUrl: String,
    val backdropUrl: String? = null,
    val synopsis: String = "",
    val category: String = "Geral",
    val year: String = "",
    val rating: String = "",
    val movieStreamUrl: String? = null,
    val isWebPlayer: Boolean = false,
    val seasons: List<SeasonItem> = emptyList(),
    val isFavorite: Boolean = false,
    val isWorking: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    val totalEpisodes: Int
        get() = if (type == MediaContentType.SERIES) seasons.sumOf { it.episodes.size } else 1

    val totalSeasons: Int
        get() = if (type == MediaContentType.SERIES) seasons.size else 0
}

data class AutoCorrectionLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val itemType: String, // "CANAL", "FILME", "SÉRIE"
    val title: String,
    val description: String,
    val status: String = "Corrigido e Operacional"
)

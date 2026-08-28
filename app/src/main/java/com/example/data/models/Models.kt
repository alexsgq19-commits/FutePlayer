package com.example.data.models

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
    val category: String? = null
)

enum class StreamFormat {
    HLS_M3U8,
    MP4,
    DASH_MPD,
    WEB_EMBED
}

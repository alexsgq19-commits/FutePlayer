package com.example.data.models

data class MovieItem(
    val id: String,
    val title: String,
    val pageUrl: String,
    val posterUrl: String,
    val backdropUrl: String? = null,
    val year: String? = "2024",
    val quality: String? = "1080p Full HD",
    val audio: String? = "Dublado / Dual Áudio",
    val imdb: String? = "8.0",
    val category: String = "Filme", // "Filme" or "Série"
    val duration: String = "1h 52min",
    val synopsis: String? = null,
    val streamUrl: String? = null,
    val embedUrl: String? = null
)

data class MovieStreamServer(
    val id: String,
    val name: String,
    val quality: String,
    val audio: String,
    val streamUrl: String,
    val embedUrl: String? = null,
    val forceWebPlayer: Boolean = false
)

data class SeriesEpisode(
    val id: String,
    val number: Int,
    val title: String,
    val duration: String = "48min",
    val streamUrl: String,
    val embedUrl: String? = null,
    val synopsis: String? = null
)

data class SeriesSeason(
    val seasonNumber: Int,
    val name: String,
    val episodes: List<SeriesEpisode>
)

data class MovieDetail(
    val id: String,
    val title: String,
    val originalTitle: String? = null,
    val pageUrl: String,
    val posterUrl: String,
    val backdropUrl: String? = null,
    val synopsis: String,
    val year: String = "2024",
    val duration: String = "1h 55min",
    val genres: List<String> = emptyList(),
    val imdbScore: String = "7.8",
    val audioType: String = "Dublado (PT-BR) / Dual Áudio",
    val quality: String = "1080p Full HD",
    val isSeries: Boolean = false,
    val streamServers: List<MovieStreamServer> = emptyList(),
    val seasons: List<SeriesSeason> = emptyList()
)

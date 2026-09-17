package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.models.MovieApiSource
import com.example.data.models.PlayableVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class MovieApiRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val metadataResolver = MovieMetadataResolver.getInstance(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        private const val TAG = "MovieApiRepository"
        private const val PREFS_NAME = "movie_api_prefs"
        private const val KEY_API_SOURCES = "api_sources_json"

        fun normalizeApiUrl(rawUrl: String): String {
            var url = rawUrl.trim()
            if (url.isBlank()) return url
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            if (url.contains("embedplayapi.top", ignoreCase = true)) {
                val uri = try { android.net.Uri.parse(url) } catch (_: Exception) { null }
                val path = uri?.path?.trimEnd('/') ?: ""
                val query = uri?.query
                if (path.isBlank() || path == "/api" || path == "/api/" || path == "/") {
                    url = if (!query.isNullOrBlank()) {
                        "https://embedplayapi.top/api/all-ids?$query"
                    } else {
                        "https://embedplayapi.top/api/all-ids"
                    }
                } else if (path == "/library/shows" || path == "/shows") {
                    url = "https://embedplayapi.top/api/all-ids?type=series"
                } else if (path == "/library/movies" || path == "/movies") {
                    url = "https://embedplayapi.top/api/all-ids?type=movie"
                }
            }
            return url
        }

        private val DEFAULT_SOURCES = listOf(
            MovieApiSource(
                id = "embedplayapi_series",
                name = "EmbedplayApi - Séries",
                apiUrl = "https://embedplayapi.top/api/all-ids?type=series",
                apiType = "JSON / REST",
                isActive = true,
                itemCount = 0
            ),
            MovieApiSource(
                id = "embedplayapi_movies",
                name = "EmbedplayApi - Filmes",
                apiUrl = "https://embedplayapi.top/api/all-ids?type=movie",
                apiType = "JSON / REST",
                isActive = true,
                itemCount = 0
            )
        )
    }

    fun getSources(): List<MovieApiSource> {
        val jsonStr = prefs.getString(KEY_API_SOURCES, null) ?: return saveSources(DEFAULT_SOURCES)
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<MovieApiSource>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    MovieApiSource(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "API Sem Nome"),
                        apiUrl = obj.optString("apiUrl", ""),
                        apiType = obj.optString("apiType", "JSON / REST"),
                        isActive = obj.optBoolean("isActive", true),
                        itemCount = obj.optInt("itemCount", 0),
                        lastSynced = obj.optLong("lastSynced", 0L),
                        lastError = obj.optString("lastError", null).takeIf { !it.isNull_or_blank() }
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing API sources JSON: ${e.message}")
            DEFAULT_SOURCES
        }
    }

    private fun saveSources(list: List<MovieApiSource>): List<MovieApiSource> {
        try {
            val array = JSONArray()
            for (source in list) {
                val obj = JSONObject().apply {
                    put("id", source.id)
                    put("name", source.name)
                    put("apiUrl", source.apiUrl)
                    put("apiType", source.apiType)
                    put("isActive", source.isActive)
                    put("itemCount", source.itemCount)
                    put("lastSynced", source.lastSynced)
                    put("lastError", source.lastError ?: "")
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_API_SOURCES, array.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving API sources JSON: ${e.message}")
        }
        return list
    }

    fun addSource(source: MovieApiSource): List<MovieApiSource> {
        val current = getSources().toMutableList()
        current.removeAll { it.id == source.id || it.apiUrl.trim() == source.apiUrl.trim() }
        current.add(0, source)
        return saveSources(current)
    }

    fun updateSource(source: MovieApiSource): List<MovieApiSource> {
        val current = getSources().map {
            if (it.id == source.id) source else it
        }
        return saveSources(current)
    }

    fun deleteSource(id: String): List<MovieApiSource> {
        val current = getSources().filterNot { it.id == id }
        return saveSources(current)
    }

    fun toggleSourceActive(id: String): List<MovieApiSource> {
        val current = getSources().map {
            if (it.id == id) it.copy(isActive = !it.isActive) else it
        }
        return saveSources(current)
    }

    suspend fun testApiUrl(apiUrl: String, apiType: String): Result<Pair<Int, List<PlayableVideo>>> = withContext(Dispatchers.IO) {
        val cleanUrl = normalizeApiUrl(apiUrl)
        if (cleanUrl.isBlank() || (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://"))) {
            return@withContext Result.failure(IllegalArgumentException("URL da API inválida. Insira um link iniciado por http:// ou https://"))
        }

        try {
            val req = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                .header("Accept", "*/*")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("Servidor respondeu com código de erro HTTP ${resp.code}"))
            }

            val bodyText = resp.body?.string() ?: ""
            if (bodyText.isBlank()) {
                return@withContext Result.failure(Exception("A API respondeu com conteúdo vazio."))
            }

            val videos = parseApiResponseBody(cleanUrl, apiType, bodyText)
            if (videos.isEmpty()) {
                return@withContext Result.failure(Exception("A API respondeu, mas nenhum filme ou série válido foi encontrado no formato selecionado ($apiType)."))
            }

            Result.success(videos.size to videos)
        } catch (e: Exception) {
            Log.e(TAG, "Error testing API URL $cleanUrl: ${e.message}", e)
            Result.failure(Exception("Erro ao conectar à API: ${e.localizedMessage ?: e.message}"))
        }
    }

    suspend fun fetchMoviesFromSource(source: MovieApiSource): Result<List<PlayableVideo>> = withContext(Dispatchers.IO) {
        val cleanUrl = normalizeApiUrl(source.apiUrl)
        if (cleanUrl.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        try {
            val req = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                .header("Accept", "*/*")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                val err = "HTTP ${resp.code}"
                updateSource(source.copy(lastError = err, lastSynced = System.currentTimeMillis()))
                return@withContext Result.failure(Exception("Erro na API: $err"))
            }

            val bodyText = resp.body?.string() ?: ""
            val videos = parseApiResponseBody(cleanUrl, source.apiType, bodyText)

            updateSource(
                source.copy(
                    itemCount = videos.size,
                    lastSynced = System.currentTimeMillis(),
                    lastError = null
                )
            )
            Result.success(videos)
        } catch (e: Exception) {
            val errMessage = e.localizedMessage ?: "Erro de conexão"
            updateSource(source.copy(lastError = errMessage, lastSynced = System.currentTimeMillis()))
            Result.failure(e)
        }
    }

    suspend fun fetchAllActiveMovies(): List<PlayableVideo> = withContext(Dispatchers.IO) {
        val activeSources = getSources().filter { it.isActive && it.apiUrl.isNotBlank() }
        val allVideos = mutableListOf<PlayableVideo>()

        for (source in activeSources) {
            val result = fetchMoviesFromSource(source)
            result.getOrNull()?.let { videos ->
                allVideos.addAll(videos)
            }
        }
        allVideos
    }

    suspend fun enrichVideosMetadata(
        videos: List<PlayableVideo>,
        onProgress: (List<PlayableVideo>) -> Unit
    ) {
        metadataResolver.enrichVideosBatch(videos, onProgress)
    }

    private fun parseApiResponseBody(sourceUrl: String, apiType: String, bodyText: String): List<PlayableVideo> {
        val trimmed = bodyText.trim()

        if (apiType == "M3U Playlist" || trimmed.startsWith("#EXTM3U") || trimmed.contains("#EXTINF")) {
            return parseM3uPlaylist(sourceUrl, bodyText)
        }

        // Try JSON parsing
        try {
            if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
                val parsedRoot: Any = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
                val items = collectAllVideoItems(parsedRoot, sourceUrl)
                if (items.isNotEmpty()) {
                    return items
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed JSON parse, trying M3U line fallback: ${e.message}")
        }

        // Fallback: try M3U or line scanner
        return parseM3uPlaylist(sourceUrl, bodyText)
    }

    private fun extractFirstValidString(obj: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            if (!obj.has(key)) continue
            val raw = obj.opt(key) ?: continue
            if (raw === JSONObject.NULL) continue
            val str = raw.toString().trim()
            if (str.isNotBlank() &&
                !str.equals("null", ignoreCase = true) &&
                !str.equals("undefined", ignoreCase = true) &&
                !str.equals("false", ignoreCase = true) &&
                !str.equals("true", ignoreCase = true)
            ) {
                return str
            }
        }
        return null
    }

    private fun isSeriesJsonObject(item: JSONObject, parentKeyHint: String, sourceUrl: String): Boolean {
        val type = extractFirstValidString(item, "type", "tipo", "media_type", "stream_type", "content_type")?.lowercase() ?: ""
        val category = extractFirstValidString(item, "category_name", "category", "categoria", "genre", "genero")?.lowercase() ?: ""
        val hint = parentKeyHint.lowercase()
        val url = sourceUrl.lowercase()

        if (item.has("series_id") || item.has("series_name") || item.has("seasons") || item.has("episodes") ||
            item.has("episode_run_time") || item.has("season_number") || item.has("episode_number") ||
            item.has("season_num") || item.has("ep_num")
        ) {
            return true
        }

        if (hint.contains("serie") || hint.contains("série") || hint.contains("series")) return true
        if (url.contains("get_series") || url.contains("type=series") || url.contains("/series/") || url.contains("action=get_series")) return true
        if (type.contains("serie") || type.contains("série") || type.contains("series") || type.contains("tv")) return true
        if (category.contains("serie") || category.contains("série") || category.contains("series") || category.contains("novela") || category.contains("anime")) return true

        return false
    }

    private fun collectAllVideoItems(
        jsonElement: Any,
        sourceUrl: String,
        parentKeyHint: String = "",
        seriesNameHint: String? = null,
        parentPosterHint: String? = null
    ): List<PlayableVideo> {
        val list = mutableListOf<PlayableVideo>()

        when (jsonElement) {
            is JSONArray -> {
                for (i in 0 until jsonElement.length()) {
                    val item = jsonElement.opt(i)
                    if (item is JSONObject) {
                        val video = parseJsonObjectToVideo(sourceUrl, item, parentKeyHint, i, seriesNameHint, parentPosterHint)
                        if (video != null) {
                            list.add(video)
                        } else {
                            val detectedSeriesName = extractFirstValidString(
                                item,
                                "series_name", "series_title", "serie_name", "serie_title",
                                "show_name", "show_title", "name", "nome", "title", "titulo", "original_name"
                            ) ?: seriesNameHint
                            val detectedPoster = extractFirstValidString(
                                item, "posterUrl", "poster", "poster_path", "cover", "cover_url", "image"
                            ) ?: parentPosterHint
                            list.addAll(collectAllVideoItems(item, sourceUrl, parentKeyHint, detectedSeriesName, detectedPoster))
                        }
                    } else if (item is JSONArray) {
                        list.addAll(collectAllVideoItems(item, sourceUrl, parentKeyHint, seriesNameHint, parentPosterHint))
                    }
                }
            }
            is JSONObject -> {
                val video = parseJsonObjectToVideo(sourceUrl, jsonElement, parentKeyHint, 0, seriesNameHint, parentPosterHint)
                if (video != null) {
                    list.add(video)
                } else {
                    val detectedSeriesName = extractFirstValidString(
                        jsonElement,
                        "series_name", "series_title", "serie_name", "serie_title",
                        "show_name", "show_title", "name", "nome", "title", "titulo", "original_name"
                    ) ?: seriesNameHint

                    val detectedPoster = extractFirstValidString(
                        jsonElement, "posterUrl", "poster", "poster_path", "cover", "cover_url", "image"
                    ) ?: parentPosterHint

                    val keys = jsonElement.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val child = jsonElement.opt(key) ?: continue
                        val hint = when {
                            key.lowercase().contains("series") || key.lowercase().contains("serie") -> "Séries"
                            key.lowercase().contains("movie") || key.lowercase().contains("filme") -> "Filmes"
                            else -> parentKeyHint
                        }
                        if (child is JSONArray || child is JSONObject) {
                            list.addAll(collectAllVideoItems(child, sourceUrl, hint, detectedSeriesName, detectedPoster))
                        }
                    }
                }
            }
        }
        return list
    }

    private fun parseJsonObjectToVideo(
        sourceUrl: String,
        item: JSONObject,
        parentKeyHint: String,
        index: Int,
        seriesNameHint: String? = null,
        parentPosterHint: String? = null
    ): PlayableVideo? {
        val isSeries = isSeriesJsonObject(item, parentKeyHint, sourceUrl)

        // 1. Extract specific series name or parent series hint
        val itemSeriesName = extractFirstValidString(
            item,
            "series_name", "series_title", "serie_name", "serie_title", "show_name", "show_title",
            "series", "serie", "show", "tv_name", "tv_title", "nome_serie", "nome_da_serie"
        )
        val effectiveSeriesName = itemSeriesName ?: seriesNameHint

        // 2. Extract item title / name
        val itemTitleOpt = extractFirstValidString(
            item,
            "name", "nome", "title", "titulo", "título",
            "title_pt", "title_br", "nome_pt", "nome_br",
            "original_name", "original_title", "stream_name", "movie_name",
            "caption", "label", "heading", "display_name", "film_name"
        )

        val seasonNum = extractFirstValidString(item, "season_number", "season_num", "season", "temporada")?.toIntOrNull()
        val episodeNum = extractFirstValidString(item, "episode_number", "ep_num", "episode", "episodio", "episódio", "ep")?.toIntOrNull()

        val imdbId = extractFirstValidString(item, "imdb_id", "imdb", "imdbId")?.trim() ?: ""
        val tmdbId = extractFirstValidString(item, "tmdb_id", "tmdb", "tmdbId")?.trim() ?: ""
        val streamId = extractFirstValidString(item, "stream_id", "vod_id", "series_id")?.trim() ?: ""

        val cachedMeta = if (imdbId.isNotBlank()) metadataResolver.getCached(imdbId) else null

        // Compose the definitive title ensuring the series name is ALWAYS visible
        val title = when {
            // Case A: Episode with season and episode numbers
            effectiveSeriesName != null && episodeNum != null && seasonNum != null -> {
                val epName = itemTitleOpt?.takeIf {
                    !it.equals(effectiveSeriesName, ignoreCase = true) &&
                            !it.matches(Regex("""^(Epis[oó]dio|Cap[ií]tulo|\d+)$""", RegexOption.IGNORE_CASE))
                }
                if (epName != null) "$effectiveSeriesName - T${seasonNum}:E${episodeNum} - $epName" else "$effectiveSeriesName - T${seasonNum}:E${episodeNum}"
            }
            // Case B: Episode with just episode number
            effectiveSeriesName != null && episodeNum != null -> {
                val epName = itemTitleOpt?.takeIf {
                    !it.equals(effectiveSeriesName, ignoreCase = true) &&
                            !it.matches(Regex("""^(Epis[oó]dio|Cap[ií]tulo|\d+)$""", RegexOption.IGNORE_CASE))
                }
                if (epName != null) "$effectiveSeriesName - Ep $episodeNum - $epName" else "$effectiveSeriesName - Episódio $episodeNum"
            }
            // Case C: Series item with specific series name and separate item title
            effectiveSeriesName != null && itemTitleOpt != null && !itemTitleOpt.contains(effectiveSeriesName, ignoreCase = true) -> {
                "$effectiveSeriesName - $itemTitleOpt"
            }
            // Case D: Item has a series name
            effectiveSeriesName != null -> effectiveSeriesName
            // Case E: Regular item title
            !itemTitleOpt.isNullOrBlank() -> itemTitleOpt
            // Case F: Cached title
            cachedMeta != null && cachedMeta.title.isNotBlank() -> cachedMeta.title
            // Case G: Fallback with IDs
            imdbId.isNotBlank() -> if (isSeries) "Série (IMDb $imdbId)" else "Filme (IMDb $imdbId)"
            tmdbId.isNotBlank() -> if (isSeries) "Série (TMDB $tmdbId)" else "Filme (TMDB $tmdbId)"
            streamId.isNotBlank() -> if (isSeries) "Série #$streamId" else "Filme #$streamId"
            else -> ""
        }.trim()

        if (title.isBlank()) return null

        var streamUrl = extractFirstValidString(
            item,
            "streamUrl", "url", "link", "stream_url", "direct_source",
            "file", "play_url", "video_url", "src", "source", "m3u8"
        ) ?: ""

        var embedUrl = extractFirstValidString(
            item,
            "embedUrl", "embed_url", "player_url", "embed"
        ) ?: ""

        val isEmbedPlayApi = sourceUrl.contains("embedplayapi.top", ignoreCase = true) ||
                streamUrl.contains("embedplayapi.top", ignoreCase = true) ||
                embedUrl.contains("embedplayapi.top", ignoreCase = true)

        // Fallback for streamUrl / embedUrl if missing or for EmbedplayApi:
        if (isEmbedPlayApi && (streamUrl.isBlank() || embedUrl.isBlank())) {
            val mediaId = imdbId.ifBlank { tmdbId }
            if (mediaId.isNotBlank()) {
                embedUrl = if (isSeries) {
                    if (seasonNum != null && episodeNum != null) {
                        "https://embedplayapi.top/embed/$mediaId/$seasonNum/$episodeNum"
                    } else {
                        "https://embedplayapi.top/embed/$mediaId"
                    }
                } else {
                    "https://embedplayapi.top/embed/$mediaId"
                }
                streamUrl = embedUrl
            }
        } else if (streamUrl.isBlank() && embedUrl.isBlank()) {
            if (imdbId.isNotBlank()) {
                val mediaType = if (isSeries) "tv" else "movie"
                embedUrl = if (isSeries) "https://autoembed.co/tv/imdb/$imdbId-1-1" else "https://autoembed.co/movie/imdb/$imdbId"
                streamUrl = embedUrl
            } else if (tmdbId.isNotBlank()) {
                val mediaType = if (isSeries) "tv" else "movie"
                embedUrl = if (isSeries) "https://autoembed.co/tv/tmdb/$tmdbId-1-1" else "https://autoembed.co/movie/tmdb/$tmdbId"
                streamUrl = embedUrl
            } else if (streamId.isNotBlank()) {
                val ext = item.optString("container_extension", "mp4").ifBlank { "mp4" }
                val uri = try { android.net.Uri.parse(sourceUrl) } catch (_: Exception) { null }
                val scheme = uri?.scheme ?: "http"
                val host = uri?.host
                val port = if (uri?.port != null && uri.port != -1) ":${uri.port}" else ""
                val user = uri?.getQueryParameter("username") ?: ""
                val pass = uri?.getQueryParameter("password") ?: ""
                val type = if (isSeries) "series" else "movie"
                if (!host.isNullOrBlank() && user.isNotBlank() && pass.isNotBlank()) {
                    streamUrl = "$scheme://$host$port/$type/$user/$pass/$streamId.$ext"
                } else {
                    val base = sourceUrl.substringBefore("?").substringBeforeLast("/")
                    streamUrl = "$base/$type/$streamId.$ext"
                }
            }
        } else if (streamUrl.isBlank() && embedUrl.isNotBlank()) {
            streamUrl = embedUrl
        }

        if (streamUrl.isBlank()) return null

        val isDirectMedia = com.example.util.VideoLinkCompatibility.isDirectMediaStream(streamUrl)
        val isForceWeb = isEmbedPlayApi || !isDirectMedia || embedUrl.isNotBlank() ||
                streamUrl.contains("embed") || streamUrl.contains("player") ||
                streamUrl.contains("autoembed") || streamUrl.contains("vidsrc") ||
                streamUrl.contains("streamtape") || streamUrl.contains("superflix") ||
                streamUrl.contains("2embed") || streamUrl.contains(".php") ||
                streamUrl.contains(".html")

        if (isForceWeb && embedUrl.isBlank()) {
            embedUrl = streamUrl
        }

        var posterUrl = extractFirstValidString(
            item,
            "posterUrl", "poster", "poster_path", "cover", "cover_url",
            "img", "thumb", "stream_icon", "thumbnail", "image", "banner"
        ) ?: parentPosterHint ?: ""

        if (posterUrl.startsWith("/")) {
            posterUrl = "https://image.tmdb.org/t/p/w500$posterUrl"
        }

        // Guaranteed fallback poster from cached metadata or high-speed Metahub CDN
        if (posterUrl.isBlank()) {
            if (cachedMeta?.posterUrl?.isNotBlank() == true) {
                posterUrl = cachedMeta.posterUrl
            } else if (imdbId.isNotBlank()) {
                posterUrl = MovieMetadataResolver.getDefaultPosterUrl(imdbId)
            }
        }

        val categoryOpt = extractFirstValidString(item, "category_name", "category", "genre", "categoria", "type")?.trim() ?: ""
        val category = if (categoryOpt.isNotBlank()) {
            categoryOpt
        } else if (cachedMeta?.category?.isNotBlank() == true) {
            cachedMeta.category
        } else if (isSeries) {
            "Séries"
        } else if (parentKeyHint.isNotBlank()) {
            parentKeyHint
        } else {
            "Filmes"
        }

        val year = extractFirstValidString(item, "year", "ano", "release_date") ?: ""
        val yearClean = if (year.length >= 4) year.take(4) else cachedMeta?.year ?: year
        val subtitle = if (!yearClean.isNullOrBlank()) {
            if (isSeries) "Série ($yearClean)" else "Filme ($yearClean)"
        } else {
            if (isSeries) "Série" else "Filme"
        }

        val id = extractFirstValidString(item, "id") ?: "${if (isSeries) "series" else "movie"}_api_${imdbId.ifBlank { tmdbId.ifBlank { title.hashCode().toString() } }}_$index"

        return PlayableVideo(
            id = id,
            title = title,
            subtitle = subtitle,
            streamUrl = streamUrl,
            posterUrl = posterUrl.takeIf { it.isNotBlank() },
            isLive = false,
            category = category,
            embedUrl = embedUrl.takeIf { it.isNotBlank() },
            forceWebPlayer = isForceWeb
        )
    }

    private fun parseM3uPlaylist(sourceUrl: String, bodyText: String): List<PlayableVideo> {
        val list = mutableListOf<PlayableVideo>()
        val lines = bodyText.lines()

        var currentTitle: String? = null
        var currentLogo: String? = null
        var currentGroup: String? = null

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                // Extract group-title
                val groupMatch = Regex("""group-title=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(trimmed)
                currentGroup = groupMatch?.groupValues?.get(1)?.trim() ?: "Filmes M3U"

                // Extract tvg-logo
                val logoMatch = Regex("""tvg-logo=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(trimmed)
                currentLogo = logoMatch?.groupValues?.get(1)?.trim()

                // Extract tvg-name and tvg-series
                val nameMatch = Regex("""tvg-name=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(trimmed)?.groupValues?.get(1)?.trim()
                val seriesMatch = Regex("""(?:tvg-series|series-name)=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(trimmed)?.groupValues?.get(1)?.trim()

                // Extract title after comma
                val commaIndex = trimmed.lastIndexOf(',')
                val rawTitle = if (commaIndex != -1 && commaIndex < trimmed.length - 1) {
                    trimmed.substring(commaIndex + 1).trim()
                } else null

                val detectedSeries = seriesMatch ?: nameMatch
                currentTitle = when {
                    !detectedSeries.isNullOrBlank() && !rawTitle.isNullOrBlank() && !rawTitle.contains(detectedSeries, ignoreCase = true) -> {
                        "$detectedSeries - $rawTitle"
                    }
                    !rawTitle.isNullOrBlank() -> rawTitle
                    !detectedSeries.isNullOrBlank() -> detectedSeries
                    else -> null
                }
            } else if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                val isSeries = currentGroup?.contains("serie", ignoreCase = true) == true ||
                        currentGroup?.contains("série", ignoreCase = true) == true ||
                        currentTitle?.contains("temporada", ignoreCase = true) == true ||
                        currentTitle?.contains(Regex("""\b(s\d{1,2}\s*e\d{1,2}|t\d{1,2}\s*:\s*e\d{1,2}|ep\s*\d+)\b""", RegexOption.IGNORE_CASE)) == true

                val title = currentTitle ?: if (isSeries) "Série ${list.size + 1}" else "Filme ${list.size + 1}"
                val id = "m3u_${if (isSeries) "series" else "vod"}_${title.hashCode()}_${list.size}"

                list.add(
                    PlayableVideo(
                        id = id,
                        title = title,
                        subtitle = if (isSeries) "Série M3U" else (currentGroup ?: "Filme M3U"),
                        streamUrl = trimmed,
                        posterUrl = currentLogo?.takeIf { it.isNotBlank() },
                        isLive = false,
                        category = if (isSeries) "Séries" else (currentGroup ?: "Filmes M3U")
                    )
                )

                currentTitle = null
                currentLogo = null
                currentGroup = null
            }
        }
        return list
    }
}

private fun String.isNull_or_blank(): Boolean = this.isBlank()

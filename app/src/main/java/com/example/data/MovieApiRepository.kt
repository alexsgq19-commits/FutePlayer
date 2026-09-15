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
    val metadataResolver = MovieMetadataResolver(context)

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

        private val DEFAULT_SOURCES = listOf(
            MovieApiSource(
                id = "default_api_1",
                name = "Exemplo API Filmes (Demo)",
                apiUrl = "https://raw.githubusercontent.com/freevods/sample/main/movies.json",
                apiType = "JSON / REST",
                isActive = false,
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
        val cleanUrl = apiUrl.trim()
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
        val cleanUrl = source.apiUrl.trim()
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

    private fun collectAllVideoItems(
        jsonElement: Any,
        sourceUrl: String,
        parentKeyHint: String = ""
    ): List<PlayableVideo> {
        val list = mutableListOf<PlayableVideo>()

        when (jsonElement) {
            is JSONArray -> {
                for (i in 0 until jsonElement.length()) {
                    val item = jsonElement.opt(i)
                    if (item is JSONObject) {
                        val video = parseJsonObjectToVideo(sourceUrl, item, parentKeyHint, i)
                        if (video != null) {
                            list.add(video)
                        } else {
                            list.addAll(collectAllVideoItems(item, sourceUrl, parentKeyHint))
                        }
                    } else if (item is JSONArray) {
                        list.addAll(collectAllVideoItems(item, sourceUrl, parentKeyHint))
                    }
                }
            }
            is JSONObject -> {
                val video = parseJsonObjectToVideo(sourceUrl, jsonElement, parentKeyHint, 0)
                if (video != null) {
                    list.add(video)
                } else {
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
                            list.addAll(collectAllVideoItems(child, sourceUrl, hint))
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
        index: Int
    ): PlayableVideo? {
        val titleOpt = item.optString("title",
            item.optString("name",
                item.optString("nome",
                    item.optString("titulo",
                        item.optString("title_pt",
                            item.optString("original_title",
                                item.optString("stream_name",
                                    item.optString("movie_name",
                                        item.optString("series_name",
                                            item.optString("caption",
                                                item.optString("label", "")
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        ).trim()

        val imdbId = item.optString("imdb_id", item.optString("imdb", item.optString("imdbId", ""))).trim()
        val tmdbId = item.optString("tmdb_id", item.optString("tmdb", item.optString("tmdbId", ""))).trim()
        val streamId = item.optString("stream_id", item.optString("vod_id", item.optString("series_id", ""))).trim()

        val isSeries = parentKeyHint.lowercase().contains("série") ||
                parentKeyHint.lowercase().contains("series") ||
                item.optString("type", "").lowercase().contains("series") ||
                item.optString("tipo", "").lowercase().contains("series")

        val cachedMeta = if (imdbId.isNotBlank()) metadataResolver.getCached(imdbId) else null

        val title = if (titleOpt.isNotBlank()) {
            titleOpt
        } else if (cachedMeta != null && cachedMeta.title.isNotBlank()) {
            cachedMeta.title
        } else if (imdbId.isNotBlank()) {
            if (isSeries) "Série (IMDb $imdbId)" else "Filme (IMDb $imdbId)"
        } else if (tmdbId.isNotBlank()) {
            if (isSeries) "Série (TMDB $tmdbId)" else "Filme (TMDB $tmdbId)"
        } else if (streamId.isNotBlank()) {
            if (isSeries) "Série #$streamId" else "Filme #$streamId"
        } else {
            ""
        }

        if (title.isBlank()) return null

        var streamUrl = item.optString("streamUrl",
            item.optString("url",
                item.optString("link",
                    item.optString("stream_url",
                        item.optString("direct_source",
                            item.optString("file",
                                item.optString("play_url",
                                    item.optString("video_url",
                                        item.optString("src",
                                            item.optString("source",
                                                item.optString("m3u8", "")
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        ).trim()

        var embedUrl = item.optString("embedUrl",
            item.optString("embed_url",
                item.optString("player_url",
                    item.optString("embed", "")
                )
            )
        ).trim()

        // Fallback for streamUrl / embedUrl if missing:
        if (streamUrl.isBlank() && embedUrl.isBlank()) {
            if (tmdbId.isNotBlank()) {
                val mediaType = if (isSeries) "tv" else "movie"
                embedUrl = "https://vidsrc.me/embed/$mediaType?tmdb=$tmdbId"
                streamUrl = embedUrl
            } else if (imdbId.isNotBlank()) {
                val mediaType = if (isSeries) "tv" else "movie"
                embedUrl = "https://vidsrc.me/embed/$mediaType?imdb=$imdbId"
                streamUrl = embedUrl
            } else if (streamId.isNotBlank()) {
                val ext = item.optString("container_extension", "mp4").ifBlank { "mp4" }
                val base = sourceUrl.substringBefore("?")
                streamUrl = "$base/$streamId.$ext"
            }
        } else if (streamUrl.isBlank() && embedUrl.isNotBlank()) {
            streamUrl = embedUrl
        }

        if (streamUrl.isBlank()) return null

        var posterUrl = item.optString("posterUrl",
            item.optString("poster",
                item.optString("poster_path",
                    item.optString("cover",
                        item.optString("cover_url",
                            item.optString("img",
                                item.optString("thumb",
                                    item.optString("stream_icon",
                                        item.optString("thumbnail",
                                            item.optString("image",
                                                item.optString("banner", "")
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        ).trim()

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

        val categoryOpt = item.optString("category", item.optString("genre", item.optString("categoria", item.optString("type", "")))).trim()
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

        val year = item.optString("year", item.optString("ano", item.optString("release_date", "")))
        val yearClean = if (year.length >= 4) year.take(4) else cachedMeta?.year ?: year
        val subtitle = if (!yearClean.isNullOrBlank()) {
            if (isSeries) "Série ($yearClean)" else "Filme ($yearClean)"
        } else {
            if (isSeries) "Série" else "Filme"
        }

        val id = item.optString("id", "${if (isSeries) "series" else "movie"}_api_${imdbId.ifBlank { tmdbId.ifBlank { title.hashCode().toString() } }}_$index")

        return PlayableVideo(
            id = id,
            title = title,
            subtitle = subtitle,
            streamUrl = streamUrl,
            posterUrl = posterUrl.takeIf { it.isNotBlank() },
            isLive = false,
            category = category,
            embedUrl = embedUrl.takeIf { it.isNotBlank() }
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

                // Extract title after comma
                val commaIndex = trimmed.lastIndexOf(',')
                if (commaIndex != -1 && commaIndex < trimmed.length - 1) {
                    currentTitle = trimmed.substring(commaIndex + 1).trim()
                }
            } else if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                val title = currentTitle ?: "Filme ${list.size + 1}"
                val id = "m3u_vod_${title.hashCode()}_${list.size}"

                list.add(
                    PlayableVideo(
                        id = id,
                        title = title,
                        subtitle = currentGroup ?: "Filme M3U",
                        streamUrl = trimmed,
                        posterUrl = currentLogo?.takeIf { it.isNotBlank() },
                        isLive = false,
                        category = currentGroup ?: "Filmes M3U"
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

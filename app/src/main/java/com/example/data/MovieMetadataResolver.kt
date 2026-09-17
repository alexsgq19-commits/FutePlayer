package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.models.PlayableVideo
import com.example.data.models.isSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ResolvedMetadata(
    val title: String,
    val posterUrl: String? = null,
    val year: String? = null,
    val isSeries: Boolean = false,
    val category: String? = null
)

class MovieMetadataResolver(context: Context) {

    companion object {
        private const val TAG = "MovieMetadataResolver"
        private const val PREFS_NAME = "movie_meta_cache_v1"

        private val IMDB_REGEX = Regex("(tt\\d{6,10})")
        private val TMDB_REGEX = Regex("""(?:embedplayapi\.top/embed/|tmdb[=:/_]|tmdb_id["':\s]+|api_)(\d{4,9})""", RegexOption.IGNORE_CASE)

        @Volatile
        private var instance: MovieMetadataResolver? = null

        fun getInstance(context: Context): MovieMetadataResolver {
            return instance ?: synchronized(this) {
                instance ?: MovieMetadataResolver(context.applicationContext).also { instance = it }
            }
        }

        fun extractImdbId(text: String?): String? {
            if (text == null) return null
            return IMDB_REGEX.find(text)?.value
        }

        fun extractTmdbId(text: String?): String? {
            if (text == null) return null
            return TMDB_REGEX.find(text)?.groupValues?.get(1)
        }

        fun getDefaultPosterUrl(id: String): String {
            return if (id.startsWith("tt")) {
                "https://images.metahub.space/poster/medium/$id/img"
            } else {
                ""
            }
        }

        fun isPlaceholderTitle(title: String): Boolean {
            val trimmed = title.trim()
            return trimmed.isBlank() ||
                    trimmed.equals("null", ignoreCase = true) ||
                    trimmed.equals("undefined", ignoreCase = true) ||
                    trimmed.startsWith("Filme (IMDb", ignoreCase = true) ||
                    trimmed.startsWith("Série (IMDb", ignoreCase = true) ||
                    trimmed.startsWith("Filme (TMDB", ignoreCase = true) ||
                    trimmed.startsWith("Série (TMDB", ignoreCase = true) ||
                    trimmed.startsWith("Filme #", ignoreCase = true) ||
                    trimmed.startsWith("Série #", ignoreCase = true) ||
                    trimmed.matches(Regex("^(Filme|Série|Movie|Series)\\s*\\(?tt\\d+\\)?$", RegexOption.IGNORE_CASE)) ||
                    trimmed.matches(Regex("^(Filme|Série|Movie|Series)\\s*\\(?\\d+\\)?$", RegexOption.IGNORE_CASE)) ||
                    trimmed.matches(Regex("^tt\\d{6,10}$", RegexOption.IGNORE_CASE))
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val memoryCache = ConcurrentHashMap<String, ResolvedMetadata>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getCached(id: String): ResolvedMetadata? {
        if (id.isBlank()) return null

        memoryCache[id]?.let { return it }

        val cachedJson = prefs.getString(id, null) ?: return null
        return try {
            val obj = JSONObject(cachedJson)
            val meta = ResolvedMetadata(
                title = obj.optString("title"),
                posterUrl = obj.optString("poster").takeIf { it.isNotBlank() },
                year = obj.optString("year").takeIf { it.isNotBlank() },
                isSeries = obj.optBoolean("isSeries", false),
                category = obj.optString("category").takeIf { it.isNotBlank() }
            )
            if (meta.title.isNotBlank()) {
                memoryCache[id] = meta
                meta
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun saveCache(id: String, meta: ResolvedMetadata) {
        if (id.isBlank() || meta.title.isBlank()) return
        memoryCache[id] = meta
        try {
            val obj = JSONObject().apply {
                put("title", meta.title)
                put("poster", meta.posterUrl ?: "")
                put("year", meta.year ?: "")
                put("isSeries", meta.isSeries)
                put("category", meta.category ?: "")
            }
            prefs.edit().putString(id, obj.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving meta cache for $id: ${e.message}")
        }
    }

    suspend fun resolve(id: String, isSeriesHint: Boolean = false): ResolvedMetadata? = withContext(Dispatchers.IO) {
        val cleanId = id.trim()
        if (cleanId.isBlank()) return@withContext null

        getCached(cleanId)?.let { return@withContext it }

        var result: ResolvedMetadata? = null

        // 1. If starts with "tt", query fast Suggestion APIs (IMDb, Cinemeta, TVMaze)
        if (cleanId.startsWith("tt")) {
            result = fetchFromImdbSuggestion(cleanId, isSeriesHint)
            if (result == null || result.title.isBlank()) {
                result = fetchFromCinemeta(cleanId, isSeriesHint)
            }
            if ((result == null || result.title.isBlank()) && isSeriesHint) {
                result = fetchFromTvMaze(cleanId)
            }
        }

        // 2. Direct lookup via EmbedplayApi (gives exact Portuguese series/movie title from <title> tag)
        if (result == null || result.title.isBlank()) {
            result = fetchFromEmbedPlayApi(cleanId, isSeriesHint)
        }

        // 3. If TV Series with numeric TMDB/TVDB ID, try TVMaze
        if ((result == null || result.title.isBlank()) && isSeriesHint && cleanId.all { it.isDigit() }) {
            result = fetchFromTvMazeTvdb(cleanId)
        }

        if (result != null && result.title.isNotBlank()) {
            saveCache(cleanId, result)
            return@withContext result
        }

        // Fallback: provide default poster if available
        val fallbackPoster = getDefaultPosterUrl(cleanId).takeIf { it.isNotBlank() }
        if (fallbackPoster != null) {
            val fallbackMeta = ResolvedMetadata(
                title = "",
                posterUrl = fallbackPoster,
                isSeries = isSeriesHint
            )
            return@withContext fallbackMeta
        }

        null
    }

    private fun fetchFromImdbSuggestion(imdbId: String, isSeriesHint: Boolean): ResolvedMetadata? {
        return try {
            val firstChar = imdbId.firstOrNull()?.lowercaseChar() ?: 't'
            val url = "https://v3.sg.media-imdb.com/suggestion/$firstChar/$imdbId.json"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return null

            val body = resp.body?.string() ?: return null
            val root = JSONObject(body)
            val dArray = root.optJSONArray("d") ?: return null
            if (dArray.length() == 0) return null

            var matchedItem: JSONObject? = null
            for (k in 0 until dArray.length()) {
                val candidate = dArray.optJSONObject(k) ?: continue
                if (candidate.optString("id").equals(imdbId, ignoreCase = true)) {
                    matchedItem = candidate
                    break
                }
            }
            val item = matchedItem ?: dArray.getJSONObject(0)
            val title = item.optString("l").trim()
            if (title.isBlank() || title.equals("null", ignoreCase = true)) return null

            val posterObj = item.optJSONObject("i")
            val posterUrl = posterObj?.optString("imageUrl")?.trim().takeIf { !it.isNullOrBlank() }
                ?: getDefaultPosterUrl(imdbId)

            val yearInt = item.optInt("y", 0)
            val yearStr = if (yearInt > 0) yearInt.toString() else item.optString("yr", "")
            val qid = item.optString("qid", "").lowercase()
            val isSeries = qid.contains("series") || qid.contains("tv") || isSeriesHint

            val category = if (isSeries) "Séries" else "Filmes"

            ResolvedMetadata(
                title = title,
                posterUrl = posterUrl,
                year = yearStr.takeIf { it.isNotBlank() },
                isSeries = isSeries,
                category = category
            )
        } catch (e: Exception) {
            Log.d(TAG, "IMDb suggestion fetch failed for $imdbId: ${e.message}")
            null
        }
    }

    private fun fetchFromTvMaze(imdbId: String): ResolvedMetadata? {
        return try {
            val url = "https://api.tvmaze.com/lookup/shows?imdb=$imdbId"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val obj = JSONObject(body)
            val name = obj.optString("name").trim()
            if (name.isBlank()) return null
            val imageObj = obj.optJSONObject("image")
            val poster = imageObj?.optString("original") ?: imageObj?.optString("medium")
            val premiered = obj.optString("premiered", "")
            val year = if (premiered.length >= 4) premiered.take(4) else null
            ResolvedMetadata(
                title = name,
                posterUrl = poster,
                year = year,
                isSeries = true,
                category = "Séries"
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchFromCinemeta(imdbId: String, isSeriesHint: Boolean): ResolvedMetadata? {
        val types = if (isSeriesHint) listOf("series", "movie") else listOf("movie", "series")
        for (t in types) {
            try {
                val url = "https://v3-cinemeta.strem.io/meta/$t/$imdbId.json"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                    .header("Accept", "application/json")
                    .build()

                val resp = httpClient.newCall(req).execute()
                if (!resp.isSuccessful) continue

                val body = resp.body?.string() ?: continue
                val root = JSONObject(body)
                val meta = root.optJSONObject("meta") ?: continue

                val title = meta.optString("name").trim()
                if (title.isNotBlank()) {
                    val poster = meta.optString("poster").trim().takeIf { it.isNotBlank() }
                        ?: getDefaultPosterUrl(imdbId)
                    val year = meta.optString("year").trim().takeIf { it.isNotBlank() }
                    val isSeries = t == "series"
                    val category = if (isSeries) "Séries" else "Filmes"

                    return ResolvedMetadata(
                        title = title,
                        posterUrl = poster,
                        year = year,
                        isSeries = isSeries,
                        category = category
                    )
                }
            } catch (e: Exception) {
                Log.d(TAG, "Cinemeta fetch error ($t) for $imdbId: ${e.message}")
            }
        }
        return null
    }

    private fun fetchFromEmbedPlayApi(id: String, isSeriesHint: Boolean): ResolvedMetadata? {
        return try {
            val url = "https://embedplayapi.top/embed/$id"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null

            // Extracts title from:
            // <title>Assistir Synden [S01E01] - Você Foi Avisado Online</title>
            // <title>Assistir Capitanes de America [S01E01] - Episódio 1 Online</title>
            // <title>Assistir Avatar: Fogo e Cinzas - 2025 Online</title>
            val titleRegex = Regex("""<title>\s*Assistir\s+([^\[<\r\n]+?)(?:\s*\[|\s*-\s*\d{4}|\s*Online|</title>)""", RegexOption.IGNORE_CASE)
            val match = titleRegex.find(body)
            var extractedTitle = match?.groupValues?.get(1)?.trim() ?: ""

            if (extractedTitle.isBlank()) {
                val fallbackRegex = Regex("""<title>([^<\r\n]+?)(?:\s*-\s*Api\s+de\s+streaming|</title>)""", RegexOption.IGNORE_CASE)
                extractedTitle = fallbackRegex.find(body)?.groupValues?.get(1)?.replace("Assistir", "")?.trim() ?: ""
            }

            if (extractedTitle.isBlank() || extractedTitle.equals("null", ignoreCase = true)) return null

            val isSeries = isSeriesHint || body.contains("S01E01", ignoreCase = true) || body.contains("temporada", ignoreCase = true)
            val posterUrl = getDefaultPosterUrl(id).takeIf { it.isNotBlank() }

            ResolvedMetadata(
                title = extractedTitle,
                posterUrl = posterUrl,
                isSeries = isSeries,
                category = if (isSeries) "Séries" else "Filmes"
            )
        } catch (e: Exception) {
            Log.d(TAG, "EmbedplayApi title fetch failed for $id: ${e.message}")
            null
        }
    }

    private fun fetchFromTvMazeTvdb(tvdbOrTmdbId: String): ResolvedMetadata? {
        return try {
            val url = "https://api.tvmaze.com/lookup/shows?thetvdb=$tvdbOrTmdbId"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val obj = JSONObject(body)
            val name = obj.optString("name").trim()
            if (name.isBlank()) return null
            val imageObj = obj.optJSONObject("image")
            val poster = imageObj?.optString("original") ?: imageObj?.optString("medium")
            val premiered = obj.optString("premiered", "")
            val year = if (premiered.length >= 4) premiered.take(4) else null
            ResolvedMetadata(
                title = name,
                posterUrl = poster,
                year = year,
                isSeries = true,
                category = "Séries"
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun enrichVideosBatch(
        videos: List<PlayableVideo>,
        onProgress: (List<PlayableVideo>) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (videos.isEmpty()) return@withContext

        val mutableList = videos.toMutableList()
        var changed = false

        // Pass 1: Apply all cached metadata instantly (0ms latency)
        for (i in mutableList.indices) {
            val v = mutableList[i]
            val mediaId = extractImdbId(v.id) ?: extractImdbId(v.streamUrl) ?: extractImdbId(v.embedUrl)
                ?: extractImdbId(v.title) ?: extractTmdbId(v.id) ?: extractTmdbId(v.streamUrl) ?: extractTmdbId(v.embedUrl)
            if (!mediaId.isNullOrBlank()) {
                val cached = getCached(mediaId)
                if (cached != null) {
                    val newTitle = if (isPlaceholderTitle(v.title)) cached.title else v.title
                    val newPoster = v.posterUrl ?: cached.posterUrl ?: getDefaultPosterUrl(mediaId).takeIf { it.isNotBlank() }
                    val newSubtitle = if (cached.year != null) {
                        if (cached.isSeries) "Série (${cached.year})" else "Filme (${cached.year})"
                    } else v.subtitle
                    val newCategory = v.category?.takeIf { !it.contains("API", ignoreCase = true) } ?: cached.category ?: v.category

                    if (newTitle != v.title || newPoster != v.posterUrl || newSubtitle != v.subtitle) {
                        mutableList[i] = v.copy(
                            title = newTitle,
                            posterUrl = newPoster,
                            subtitle = newSubtitle,
                            category = newCategory
                        )
                        changed = true
                    }
                } else if (v.posterUrl.isNullOrBlank() && mediaId.startsWith("tt")) {
                    // Instantly set high-res poster from metahub even before title is resolved
                    mutableList[i] = v.copy(posterUrl = getDefaultPosterUrl(mediaId))
                    changed = true
                }
            }
        }

        if (changed) {
            onProgress(mutableList.toList())
        }

        // Pass 2: Identify uncached items that need resolution
        val toResolveIndices = mutableListOf<Pair<Int, String>>()
        for (i in mutableList.indices) {
            val v = mutableList[i]
            if (isPlaceholderTitle(v.title)) {
                val mediaId = extractImdbId(v.id)
                    ?: extractImdbId(v.streamUrl)
                    ?: extractImdbId(v.embedUrl)
                    ?: extractImdbId(v.title)
                    ?: extractImdbId(v.subtitle)
                    ?: extractTmdbId(v.id)
                    ?: extractTmdbId(v.streamUrl)
                    ?: extractTmdbId(v.embedUrl)
                if (!mediaId.isNullOrBlank() && getCached(mediaId) == null) {
                    toResolveIndices.add(Pair(i, mediaId))
                    if (toResolveIndices.size >= 250) break
                }
            }
        }

        if (toResolveIndices.isEmpty()) return@withContext

        // Run concurrent resolution with a semaphore
        val semaphore = Semaphore(8)
        val chunkSize = 8
        val chunks = toResolveIndices.chunked(chunkSize)

        for (chunk in chunks) {
            var chunkChanged = false
            chunk.map { (index, mediaId) ->
                async {
                    semaphore.withPermit {
                        val isSeries = mutableList[index].isSeries
                        val resolved = resolve(mediaId, isSeries)
                        if (resolved != null && resolved.title.isNotBlank()) {
                            val cur = mutableList[index]
                            val newSubtitle = if (resolved.year != null) {
                                if (resolved.isSeries) "Série (${resolved.year})" else "Filme (${resolved.year})"
                            } else cur.subtitle
                            val newCategory = cur.category?.takeIf { !it.contains("API", ignoreCase = true) } ?: resolved.category ?: cur.category
                            val newPoster = resolved.posterUrl ?: cur.posterUrl ?: getDefaultPosterUrl(mediaId).takeIf { it.isNotBlank() }

                            mutableList[index] = cur.copy(
                                title = resolved.title,
                                posterUrl = newPoster,
                                subtitle = newSubtitle,
                                category = newCategory
                            )
                            chunkChanged = true
                        }
                    }
                }
            }.awaitAll()

            if (chunkChanged) {
                onProgress(mutableList.toList())
            }
        }
    }
}

package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.models.PlayableVideo
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

        fun extractImdbId(text: String): String? {
            return IMDB_REGEX.find(text)?.value
        }

        fun getDefaultPosterUrl(imdbId: String): String {
            return "https://images.metahub.space/poster/medium/$imdbId/img"
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

    fun getCached(imdbId: String): ResolvedMetadata? {
        if (imdbId.isBlank()) return null

        memoryCache[imdbId]?.let { return it }

        val cachedJson = prefs.getString(imdbId, null) ?: return null
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
                memoryCache[imdbId] = meta
                meta
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun saveCache(imdbId: String, meta: ResolvedMetadata) {
        if (imdbId.isBlank() || meta.title.isBlank()) return
        memoryCache[imdbId] = meta
        try {
            val obj = JSONObject().apply {
                put("title", meta.title)
                put("poster", meta.posterUrl ?: "")
                put("year", meta.year ?: "")
                put("isSeries", meta.isSeries)
                put("category", meta.category ?: "")
            }
            prefs.edit().putString(imdbId, obj.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving meta cache for $imdbId: ${e.message}")
        }
    }

    suspend fun resolve(imdbId: String, isSeriesHint: Boolean = false): ResolvedMetadata? = withContext(Dispatchers.IO) {
        val cleanId = imdbId.trim()
        if (!cleanId.startsWith("tt")) return@withContext null

        getCached(cleanId)?.let { return@withContext it }

        // 1. Primary: Official IMDb suggestion API (lightning fast, high-res posters)
        val imdbResult = fetchFromImdbSuggestion(cleanId, isSeriesHint)
        if (imdbResult != null && imdbResult.title.isNotBlank()) {
            saveCache(cleanId, imdbResult)
            return@withContext imdbResult
        }

        // 2. Secondary: Cinemeta API (official Stremio open catalog)
        val cinemetaResult = fetchFromCinemeta(cleanId, isSeriesHint)
        if (cinemetaResult != null && cinemetaResult.title.isNotBlank()) {
            saveCache(cleanId, cinemetaResult)
            return@withContext cinemetaResult
        }

        // 3. Fallback: if title cannot be fetched, at least guarantee official poster
        val fallbackMeta = ResolvedMetadata(
            title = "",
            posterUrl = getDefaultPosterUrl(cleanId),
            isSeries = isSeriesHint
        )
        fallbackMeta
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

            val item = dArray.getJSONObject(0)
            val title = item.optString("l").trim()
            if (title.isBlank()) return null

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
            val imdbId = extractImdbId(v.id) ?: extractImdbId(v.streamUrl) ?: extractImdbId(v.title)
            if (!imdbId.isNullOrBlank()) {
                val cached = getCached(imdbId)
                if (cached != null) {
                    val newTitle = if (isPlaceholderTitle(v.title)) cached.title else v.title
                    val newPoster = v.posterUrl ?: cached.posterUrl ?: getDefaultPosterUrl(imdbId)
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
                } else if (v.posterUrl.isNullOrBlank()) {
                    // Instantly set high-res poster from metahub even before title is resolved!
                    mutableList[i] = v.copy(posterUrl = getDefaultPosterUrl(imdbId))
                    changed = true
                }
            }
        }

        if (changed) {
            onProgress(mutableList.toList())
        }

        // Pass 2: Identify uncached items that need resolution (limit to first 120 items to preserve bandwidth)
        val toResolveIndices = mutableListOf<Pair<Int, String>>()
        for (i in mutableList.indices) {
            val v = mutableList[i]
            if (isPlaceholderTitle(v.title)) {
                val imdbId = extractImdbId(v.id) ?: extractImdbId(v.streamUrl) ?: extractImdbId(v.title)
                if (!imdbId.isNullOrBlank() && getCached(imdbId) == null) {
                    toResolveIndices.add(Pair(i, imdbId))
                    if (toResolveIndices.size >= 120) break
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
            chunk.map { (index, imdbId) ->
                async {
                    semaphore.withPermit {
                        val isSeries = mutableList[index].category?.contains("série", ignoreCase = true) == true ||
                                mutableList[index].subtitle.contains("série", ignoreCase = true)
                        val resolved = resolve(imdbId, isSeries)
                        if (resolved != null && resolved.title.isNotBlank()) {
                            val cur = mutableList[index]
                            val newSubtitle = if (resolved.year != null) {
                                if (resolved.isSeries) "Série (${resolved.year})" else "Filme (${resolved.year})"
                            } else cur.subtitle
                            val newCategory = cur.category?.takeIf { !it.contains("API", ignoreCase = true) } ?: resolved.category ?: cur.category
                            val newPoster = resolved.posterUrl ?: cur.posterUrl ?: getDefaultPosterUrl(imdbId)

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

    private fun isPlaceholderTitle(title: String): Boolean {
        val trimmed = title.trim()
        return trimmed.isBlank() ||
                trimmed.startsWith("Filme (IMDb", ignoreCase = true) ||
                trimmed.startsWith("Série (IMDb", ignoreCase = true) ||
                trimmed.startsWith("Filme (TMDB", ignoreCase = true) ||
                trimmed.startsWith("Série (TMDB", ignoreCase = true) ||
                trimmed.startsWith("Filme #", ignoreCase = true) ||
                trimmed.startsWith("Série #", ignoreCase = true) ||
                trimmed.matches(Regex("^(Filme|Série|Movie|Series)\\s*\\(?tt\\d+\\)?$", RegexOption.IGNORE_CASE))
    }
}

package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.models.ChannelOption
import com.example.data.models.MatchItem
import com.example.data.models.PlayableVideo
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class FutemaisRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("futemais_prefs", Context.MODE_PRIVATE)
    
    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firestore", e)
            null
        }
    }

    init {
        syncFromFirestore {}
    }

    fun syncFromFirestore(onComplete: () -> Unit = {}) {
        firestore?.collection("app_data")?.document("channels")?.get()
            ?.addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val customCats = doc.getString("custom_channel_categories")
                    val customChannels = doc.getString("custom_quick_channels")
                    val deletedIds = doc.get("deleted_channel_ids") as? List<*>

                    val editor = prefs.edit()
                    if (customCats != null) {
                        editor.putString("custom_channel_categories", customCats)
                    }
                    if (customChannels != null) {
                        editor.putString("custom_quick_channels", customChannels)
                    }
                    if (deletedIds != null) {
                        val stringSet = deletedIds.mapNotNull { it?.toString() }.toSet()
                        editor.putStringSet("deleted_channel_ids", stringSet)
                    }
                    editor.apply()
                }
                onComplete()
            }
            ?.addOnFailureListener { e ->
                Log.e(TAG, "Error fetching channels from Firestore", e)
                onComplete()
            }
    }

    private fun syncToFirestore() {
        val customCats = prefs.getString("custom_channel_categories", "[]")
        val customChannels = prefs.getString("custom_quick_channels", "[]")
        val deletedIds = prefs.getStringSet("deleted_channel_ids", emptySet())?.toList() ?: emptyList()

        val data = hashMapOf(
            "custom_channel_categories" to customCats,
            "custom_quick_channels" to customChannels,
            "deleted_channel_ids" to deletedIds
        )

        firestore?.collection("app_data")?.document("channels")?.set(data)
            ?.addOnFailureListener { e ->
                Log.e(TAG, "Error saving channels to Firestore", e)
            }
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _favoriteIds = MutableStateFlow<Set<String>>(loadFavorites())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private fun loadFavorites(): Set<String> {
        return prefs.getStringSet("favorite_ids", emptySet()) ?: emptySet()
    }

    val defaultCategories = listOf(
        "Esportes",
        "Católicos (CXTV)",
        "Desenhos & Kids",
        "Filmes & Séries",
        "Abertos & Regionais"
    )

    fun getCustomCategories(): List<String> {
        val jsonStr = prefs.getString("custom_channel_categories", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val cat = arr.getString(i).trim()
                if (cat.isNotBlank() && !list.contains(cat)) {
                    list.add(cat)
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveCustomCategories(categories: List<String>) {
        try {
            val arr = org.json.JSONArray()
            categories.forEach { cat ->
                if (cat.isNotBlank()) {
                    arr.put(cat.trim())
                }
            }
            prefs.edit().putString("custom_channel_categories", arr.toString()).apply()
            syncToFirestore()
        } catch (_: Exception) {}
    }

    fun addCustomCategory(category: String): List<String> {
        val clean = category.trim()
        if (clean.isBlank()) return getCustomCategories()
        val current = getCustomCategories().toMutableList()
        val isDefault = defaultCategories.any { it.equals(clean, ignoreCase = true) }
        val alreadyExists = current.any { it.equals(clean, ignoreCase = true) }
        if (!isDefault && !alreadyExists) {
            current.add(clean)
            saveCustomCategories(current)
        }
        return getCustomCategories()
    }

    fun deleteCustomCategory(category: String): List<String> {
        val current = getCustomCategories().toMutableList()
        current.removeAll { it.equals(category.trim(), ignoreCase = true) }
        saveCustomCategories(current)
        return getCustomCategories()
    }

    fun getAllCategories(): List<String> {
        val custom = getCustomCategories()
        val channelCats = getQuickChannels().mapNotNull { it.category }.filter { it.isNotBlank() }
        return (defaultCategories + custom + channelCats).distinct()
    }

    private fun loadCustomChannels(): List<PlayableVideo> {
        val jsonStr = prefs.getString("custom_quick_channels", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(jsonStr)
            val list = mutableListOf<PlayableVideo>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    PlayableVideo(
                        id = obj.optString("id", "custom_${System.currentTimeMillis()}"),
                        title = obj.optString("title"),
                        subtitle = obj.optString("subtitle", "Canal Personalizado • Admin"),
                        streamUrl = obj.optString("streamUrl"),
                        posterUrl = obj.optString("posterUrl").takeIf { it.isNotBlank() },
                        isLive = obj.optBoolean("isLive", true),
                        embedUrl = obj.optString("embedUrl").takeIf { it.isNotBlank() },
                        forceWebPlayer = obj.optBoolean("forceWebPlayer", false),
                        category = obj.optString("category").takeIf { it.isNotBlank() } ?: "Esportes"
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveCustomChannels(list: List<PlayableVideo>) {
        try {
            val arr = org.json.JSONArray()
            list.forEach { ch ->
                val obj = org.json.JSONObject().apply {
                    put("id", ch.id)
                    put("title", ch.title)
                    put("subtitle", ch.subtitle)
                    put("streamUrl", ch.streamUrl)
                    put("posterUrl", ch.posterUrl ?: "")
                    put("isLive", ch.isLive)
                    put("embedUrl", ch.embedUrl ?: "")
                    put("forceWebPlayer", ch.forceWebPlayer)
                    put("category", ch.category ?: "Esportes")
                }
                arr.put(obj)
            }
            prefs.edit().putString("custom_quick_channels", arr.toString()).apply()
        } catch (_: Exception) {}
    }

    fun addCustomChannel(channel: PlayableVideo): List<PlayableVideo> {
        val cat = channel.category?.trim() ?: ""
        if (cat.isNotBlank()) {
            addCustomCategory(cat)
        }

        val current = loadCustomChannels().toMutableList()
        // Replace if exists, or prepend
        val idx = current.indexOfFirst { it.id == channel.id }
        if (idx >= 0) {
            current[idx] = channel
        } else {
            current.add(0, channel)
        }
        saveCustomChannels(current)

        val deleted = prefs.getStringSet("deleted_channel_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (deleted.contains(channel.id)) {
            deleted.remove(channel.id)
            prefs.edit().putStringSet("deleted_channel_ids", deleted).apply()
        }
        
        syncToFirestore()

        return getQuickChannels()
    }

    fun updateQuickChannel(channel: PlayableVideo): List<PlayableVideo> {
        return addCustomChannel(channel)
    }

    fun resetDefaultChannel(id: String): List<PlayableVideo> {
        val current = loadCustomChannels().toMutableList()
        current.removeAll { it.id == id }
        saveCustomChannels(current)

        val deleted = prefs.getStringSet("deleted_channel_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (deleted.contains(id)) {
            deleted.remove(id)
            prefs.edit().putStringSet("deleted_channel_ids", deleted).apply()
        }
        
        syncToFirestore()

        return getQuickChannels()
    }

    fun deleteCustomChannel(id: String): List<PlayableVideo> {
        val current = loadCustomChannels().toMutableList()
        current.removeAll { it.id == id }
        saveCustomChannels(current)

        val deleted = prefs.getStringSet("deleted_channel_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        deleted.add(id)
        prefs.edit().putStringSet("deleted_channel_ids", deleted).apply()

        syncToFirestore()

        return getQuickChannels()
    }

    fun toggleFavorite(id: String) {
        val current = _favoriteIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        prefs.edit().putStringSet("favorite_ids", current).apply()
        _favoriteIds.value = current
    }

    suspend fun fetchMatches(url: String = "https://futemais.link/app2/"): Result<List<MatchItem>> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                    )
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Referer", "https://futemais.link/")
                    .build()

                val response = client.newCall(request).execute()
                val html = response.body?.string() ?: ""

                if (html.isBlank()) {
                    return@withContext Result.success(getFallbackMatches())
                }

                val doc = Jsoup.parse(html)
                val matches = mutableListOf<MatchItem>()

                var currentDateTag = "HOJE"

                val allSections = doc.select(".data-separador, .match-container")
                for (element in allSections) {
                    if (element.hasClass("data-separador")) {
                        val tag = element.select(".sep-tag").text()
                        val date = element.select(".sep-data").text()
                        currentDateTag = if (tag.isNotBlank()) "$tag - $date".trim() else date
                    } else if (element.hasClass("match-container")) {
                        val link = element.selectFirst("a")?.attr("href") ?: ""
                        val homeName = element.select(".left-team .team-name").text().ifBlank {
                            element.select(".left-team img").attr("alt")
                        }.ifBlank { "Time A" }
                        val homeLogo = element.select(".left-team img").attr("src")

                        val awayName = element.select(".right-team .team-name").text().ifBlank {
                            element.select(".right-team img").attr("alt")
                        }.ifBlank { "Time B" }
                        val awayLogo = element.select(".right-team img").attr("src")

                        val championship = element.select("#match").text().ifBlank { "Futebol Ao Vivo" }
                        val time = element.select("#match-time").text().ifBlank { "Ao Vivo" }

                        val id = if (link.contains("id=")) {
                            link.substringAfter("id=")
                        } else {
                            "$homeName-$awayName-$time".replace("\\s+".toRegex(), "_")
                        }

                        val isLive = time.contains(":") == false || isCurrentTimeAround(time)

                        matches.add(
                            MatchItem(
                                id = id,
                                homeTeam = homeName,
                                homeLogoUrl = homeLogo,
                                awayTeam = awayName,
                                awayLogoUrl = awayLogo,
                                championship = championship,
                                time = time,
                                dateTag = currentDateTag,
                                detailUrl = link,
                                isLiveNow = isLive,
                                isFavorite = _favoriteIds.value.contains(id)
                            )
                        )
                    }
                }

                if (matches.isEmpty()) {
                    // Try parsing with fallback regex
                    val regexMatches = parseWithRegex(html, currentDateTag)
                    if (regexMatches.isNotEmpty()) {
                        return@withContext Result.success(regexMatches)
                    }
                    return@withContext Result.success(getFallbackMatches())
                }

                Result.success(matches)
            } catch (e: Exception) {
                Log.e(TAG, "Error scraping futemais matches: ${e.message}", e)
                Result.success(getFallbackMatches())
            }
        }

    private fun parseWithRegex(html: String, defaultDateTag: String): List<MatchItem> {
        val matches = mutableListOf<MatchItem>()
        val containerPattern = Pattern.compile(
            "<div class=[\"']match-container[\\s\\S]*?<a href=[\"']([^\"']+)[\"'][\\s\\S]*?src=[\"']([^\"']+)[\"'][\\s\\S]*?<div class=[\"']team-name[\"']>([^<]+)<[\\s\\S]*?id=[\"']match[\"']>([^<]+)<[\\s\\S]*?id=[\"']match-time[\"']>([^<]+)<[\\s\\S]*?src=[\"']([^\"']+)[\"'][\\s\\S]*?<div class=[\"']team-name[\"']>([^<]+)<",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = containerPattern.matcher(html)
        var index = 0
        while (matcher.find()) {
            val link = matcher.group(1) ?: ""
            val homeLogo = matcher.group(2) ?: ""
            val homeName = matcher.group(3) ?: "Time A"
            val champ = matcher.group(4) ?: "Campeonato"
            val time = matcher.group(5) ?: "Ao Vivo"
            val awayLogo = matcher.group(6) ?: ""
            val awayName = matcher.group(7) ?: "Time B"
            val id = "match_$index"
            matches.add(
                MatchItem(
                    id = id,
                    homeTeam = homeName.trim(),
                    homeLogoUrl = homeLogo.trim(),
                    awayTeam = awayName.trim(),
                    awayLogoUrl = awayLogo.trim(),
                    championship = champ.trim(),
                    time = time.trim(),
                    dateTag = defaultDateTag,
                    detailUrl = link.trim(),
                    isLiveNow = true,
                    isFavorite = _favoriteIds.value.contains(id)
                )
            )
            index++
        }
        return matches
    }

    suspend fun fetchChannelsForMatch(detailUrl: String): Result<List<ChannelOption>> =
        withContext(Dispatchers.IO) {
            try {
                if (detailUrl.isBlank()) {
                    return@withContext Result.success(getDefaultChannelOptions("default"))
                }

                val request = Request.Builder()
                    .url(detailUrl)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                    )
                    .header("Referer", "https://futemais.link/app2/")
                    .build()

                val response = client.newCall(request).execute()
                val html = response.body?.string() ?: ""

                val doc = Jsoup.parse(html)
                val channels = mutableListOf<ChannelOption>()

                // Look for channel buttons
                val channelLinks = doc.select("a.channel-card, table.canais a, .canais th a")
                var count = 1
                for (link in channelLinks) {
                    val name = link.text().ifBlank { "Canal $count" }
                    val onClick = link.attr("onclick")
                    var pageUrl = link.attr("href")

                    if (onClick.contains("changeChannel('")) {
                        pageUrl = onClick.substringAfter("changeChannel('").substringBefore("')")
                    }

                    if (pageUrl.startsWith("http")) {
                        channels.add(
                            ChannelOption(
                                id = "ch_${count}_${name.replace("\\s+".toRegex(), "")}",
                                name = name,
                                pageUrl = pageUrl,
                                headers = mapOf("Referer" to detailUrl, "User-Agent" to "Mozilla/5.0 (Linux; Android 13; Mobile) Chrome/122.0.0.0")
                            )
                        )
                        count++
                    }
                }

                if (channels.isEmpty()) {
                    // Extract iframe src if present
                    val iframeSrc = doc.selectFirst("iframe#Player, iframe")?.attr("src")
                    if (!iframeSrc.isNullOrBlank() && iframeSrc.startsWith("http")) {
                        channels.add(
                            ChannelOption(
                                id = "ch_main",
                                name = "Canal Principal",
                                pageUrl = iframeSrc,
                                headers = mapOf("Referer" to detailUrl)
                            )
                        )
                    } else {
                        return@withContext Result.success(getDefaultChannelOptions(detailUrl))
                    }
                }

                Result.success(channels)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching channels for $detailUrl: ${e.message}", e)
                Result.success(getDefaultChannelOptions(detailUrl))
            }
        }

    suspend fun resolveStream(
        channel: ChannelOption,
        matchTitle: String,
        championship: String,
        posterUrl: String? = null
    ): Result<PlayableVideo> = withContext(Dispatchers.IO) {
        try {
            if (channel.pageUrl.isBlank()) {
                return@withContext Result.success(
                    PlayableVideo(
                        id = channel.id,
                        title = matchTitle,
                        subtitle = championship,
                        streamUrl = channel.pageUrl,
                        posterUrl = posterUrl,
                        isLive = true,
                        embedUrl = channel.pageUrl
                    )
                )
            }

            val request = Request.Builder()
                .url(channel.pageUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                )
                .header("Referer", "https://temporariofutemais.com/")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""

            // 1. Look for Clappr / HLS source: 'https://...m3u8...'
            val hlsPattern = Pattern.compile(
                "(?:source|src|file):\\s*['\"](https?://[^'\"]+\\.m3u8[^'\"]*)['\"]",
                Pattern.CASE_INSENSITIVE
            )
            val matcher = hlsPattern.matcher(html)
            var directHlsUrl: String? = null
            if (matcher.find()) {
                directHlsUrl = matcher.group(1)
            }

            val directRegex = Pattern.compile("(https?://[^\"'<>\\s]+\\.m3u8(?:\\?[^\"'<>\\s]*)?)")

            // 2. Direct regex search for chunks.m3u8 or live m3u8
            if (directHlsUrl == null) {
                val directMatcher = directRegex.matcher(html)
                if (directMatcher.find()) {
                    directHlsUrl = directMatcher.group(1)
                }
            }

            // 3. Check for nested iframe inside channel page if not found
            if (directHlsUrl == null) {
                val iframeRegex = Pattern.compile("<iframe[^>]+src=[\"'](https?://[^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                val iframeMatcher = iframeRegex.matcher(html)
                if (iframeMatcher.find()) {
                    val iframeSrc = iframeMatcher.group(1)
                    if (!iframeSrc.isNullOrBlank()) {
                        try {
                            val subReq = Request.Builder()
                                .url(iframeSrc)
                                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36")
                                .header("Referer", channel.pageUrl)
                                .build()
                            val subRes = client.newCall(subReq).execute()
                            val subHtml = subRes.body?.string() ?: ""
                            val subMatcher = directRegex.matcher(subHtml)
                            if (subMatcher.find()) {
                                directHlsUrl = subMatcher.group(1)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            val headers = mapOf(
                "Referer" to "https://temporariofutemais.com/",
                "User-Agent" to "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
            )

            val resolvedVideo = PlayableVideo(
                id = channel.id,
                title = "$matchTitle (${channel.name})",
                subtitle = championship,
                streamUrl = directHlsUrl ?: channel.pageUrl,
                posterUrl = posterUrl,
                isLive = true,
                headers = headers,
                embedUrl = channel.pageUrl,
                isFavorite = _favoriteIds.value.contains(channel.id)
            )

            Result.success(resolvedVideo)
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving stream: ${e.message}", e)
            Result.success(
                PlayableVideo(
                    id = channel.id,
                    title = "$matchTitle (${channel.name})",
                    subtitle = championship,
                    streamUrl = channel.pageUrl,
                    posterUrl = posterUrl,
                    isLive = true,
                    embedUrl = channel.pageUrl
                )
            )
        }
    }

    private fun isCurrentTimeAround(timeStr: String): Boolean {
        return true // Mark as ready to play
    }

    fun getDefaultChannels(): List<PlayableVideo> = listOf(
            // === CANAIS CATÓLICOS (CXTV & FÉ) ===
            PlayableVideo(
                id = "canal_cxtv_aparecida",
                title = "TV Aparecida HD",
                subtitle = "Missa de Aparecida, Consagração e Fé • CXTV",
                streamUrl = "https://cdn.jmvstream.com/w/LVW-9716/LVW9716_HbtQtezcaw/playlist.m3u8",
                embedUrl = null,
                isLive = true,
                category = "Católicos (CXTV)"
            ),
            PlayableVideo(
                id = "canal_cxtv_redevida",
                title = "Rede Vida HD",
                subtitle = "O Canal da Família, Missas e Terço • CXTV",
                streamUrl = "https://cvd1.cds.ebtcvd.net/live-redevida/smil:redevida.smil/playlist.m3u8",
                embedUrl = null,
                isLive = true,
                category = "Católicos (CXTV)"
            ),
            PlayableVideo(
                id = "canal_cxtv_cancaonova",
                title = "TV Canção Nova HD",
                subtitle = "Evangelização, Orações e Louvor • CXTV",
                streamUrl = "https://5c65286fc6ace.streamlock.net/cancaonova/CancaoNova.stream_720p/playlist.m3u8",
                embedUrl = null,
                isLive = true,
                category = "Católicos (CXTV)"
            ),
            PlayableVideo(
                id = "canal_cxtv_evangelizar",
                title = "TV Evangelizar HD",
                subtitle = "Pe. Reginaldo Manzotti, Missas e Pregações • CXTV",
                streamUrl = "https://tvevangelizar.brasilstream.com.br/hls/tvevangelizar/index.m3u8",
                embedUrl = null,
                isLive = true,
                category = "Católicos (CXTV)"
            ),
            PlayableVideo(
                id = "canal_cxtv_seculo21",
                title = "TV Século 21 HD",
                subtitle = "Associação do Senhor Jesus e Novenas • CXTV",
                streamUrl = "http://tvseculo21-lh.akamaihd.net/i/tvseculo_1@16110/master.m3u8",
                embedUrl = null,
                isLive = true,
                category = "Católicos (CXTV)"
            ),
            PlayableVideo(
                id = "canal_cxtv_paieterno",
                title = "TV Pai Eterno HD",
                subtitle = "Santuário Basílica do Divino Pai Eterno • CXTV",
                streamUrl = "http://flash8.crossdigital.com.br/2306/2306/chunklist.m3u8",
                embedUrl = null,
                isLive = true,
                category = "Católicos (CXTV)"
            ),
            PlayableVideo(
                id = "canal_cxtv_nazare",
                title = "TV Nazaré HD",
                subtitle = "Arquidiocese de Belém e Fé Católica • CXTV",
                streamUrl = "https://5c65286fc6ace.streamlock.net/cancaonova/CancaoNova.stream_720p/playlist.m3u8",
                embedUrl = null,
                isLive = true,
                category = "Católicos (CXTV)"
            ),

            // === FILMES & ENTRETENIMENTO ===
            PlayableVideo(
                id = "canal_sonyone_cinema",
                title = "Sony One Cinema HD",
                subtitle = "Filmes Clássicos, Sucessos de Hollywood e Cinema 24h",
                streamUrl = "https://spt-sonyoneclassicas-1-br.samsung.wurl.tv/playlist.m3u8",
                embedUrl = null,
                isLive = true,
                category = "Filmes & Séries"
            ),
            PlayableVideo(
                id = "canal_gospel_cartoon",
                title = "Gospel Cartoon HD",
                subtitle = "Desenhos Bíblicos, Animações e Programação Infantil 24h",
                streamUrl = "https://stmv1.srvif.com/gospelcartoon/gospelcartoon/playlist.m3u8",
                embedUrl = null,
                isLive = true,
                category = "Desenhos & Kids"
            ),
            PlayableVideo(
                id = "canal_cartoon_network",
                title = "Cartoon Network HD",
                subtitle = "Desenhos Animados, Clássicos e Animações 24h • Web Player",
                streamUrl = "https://tv.embedtv.lat/cartoonnetwork",
                embedUrl = "https://tv.embedtv.lat/cartoonnetwork",
                forceWebPlayer = true,
                isLive = true,
                category = "Desenhos & Kids"
            ),
            PlayableVideo(
                id = "canal_cartoonito",
                title = "Cartoonito HD",
                subtitle = "Desenhos e Programação Pré-escolar Infantil 24h • Web Player",
                streamUrl = "https://redecanaistv.pk/player3/ch.php?categoria=live&canal=cantoonito",
                embedUrl = "https://redecanaistv.pk/player3/ch.php?categoria=live&canal=cantoonito",
                forceWebPlayer = true,
                isLive = true,
                category = "Desenhos & Kids"
            ),

            // === CANAIS ESPORTIVOS ===
            PlayableVideo(
                id = "canal_caze",
                title = "CazéTV HD",
                subtitle = "Futebol, Brasileirão, Copas e Ao Vivo",
                streamUrl = "https://amg01391-amg01391c10-tcl-br-9630.playouts.now.amagi.tv/playlist.m3u8",
                embedUrl = null,
                isLive = true,
                category = "Esportes"
            ),
            PlayableVideo(
                id = "canal_fifaplus",
                title = "FIFA Plus HD",
                subtitle = "Copas do Mundo, Documentários e Jogos Ao Vivo",
                streamUrl = "https://c2657533.wurl.com/master/f36d25e7e52f1ba8d7e56eb859c636563214f541/UGxleC1icl9GSUZBUGx1c1BvcnR1Z3Vlc2VfSExT/playlist.m3u8",
                embedUrl = null,
                isLive = true,
                category = "Esportes"
            ),
            PlayableVideo(
                id = "canal_globo",
                title = "Globo SP HD",
                subtitle = "Futebol e TV Aberta Ao Vivo • Web Player",
                streamUrl = "https://links.temporariofutemais.com/prime.php?c=canal1",
                embedUrl = "https://links.temporariofutemais.com/prime.php?c=canal1",
                forceWebPlayer = true,
                isLive = true,
                category = "Esportes"
            ),
            PlayableVideo(
                id = "canal_sportv",
                title = "SporTV HD",
                subtitle = "Brasileirão e Campeonatos Nacionais • Web Player",
                streamUrl = "https://links.temporariofutemais.com/prime.php?c=canal2",
                embedUrl = "https://links.temporariofutemais.com/prime.php?c=canal2",
                forceWebPlayer = true,
                isLive = true,
                category = "Esportes"
            ),
            PlayableVideo(
                id = "canal_premiere",
                title = "Premiere Clubes HD",
                subtitle = "Todos os jogos do futebol brasileiro • Web Player",
                streamUrl = "https://links.temporariofutemais.com/prime.php?c=canal3",
                embedUrl = "https://links.temporariofutemais.com/prime.php?c=canal3",
                forceWebPlayer = true,
                isLive = true,
                category = "Esportes"
            ),
            PlayableVideo(
                id = "canal_espn",
                title = "ESPN Brasil HD",
                subtitle = "Premier League, Champions & Libertadores • Web Player",
                streamUrl = "https://links2.temporariofutemais.com/canais3/opcao1.php?id=canal4",
                embedUrl = "https://links2.temporariofutemais.com/canais3/opcao1.php?id=canal4",
                forceWebPlayer = true,
                isLive = true,
                category = "Esportes"
            ),
            PlayableVideo(
                id = "canal_cazetv",
                title = "CazéTV Ao Vivo",
                subtitle = "Transmissões e Reações Ao Vivo • Web Player",
                streamUrl = "https://links.temporariofutemais.com/prime.php?c=canal5",
                embedUrl = "https://links.temporariofutemais.com/prime.php?c=canal5",
                forceWebPlayer = true,
                isLive = true,
                category = "Esportes"
            ),

            // === CANAIS ABERTOS & REGIONAIS ===
            PlayableVideo(
                id = "canal_tvbrasil",
                title = "TV Brasil HD",
                subtitle = "Jornalismo, Esportes e Cultura Nacional",
                streamUrl = "https://tvbrasil-stream.ebc.com.br/index.m3u8",
                embedUrl = null,
                isLive = true,
                category = "Abertos & Regionais"
            ),
            PlayableVideo(
                id = "canal_cultura",
                title = "TV Cultura HD",
                subtitle = "Cultura, Jornalismo e Esportes",
                streamUrl = "https://player-tvcultura.stream.uol.com.br/live/tvcultura.m3u8",
                embedUrl = null,
                isLive = true,
                category = "Abertos & Regionais"
            ),
            PlayableVideo(
                id = "canal_redetv",
                title = "RedeTV! HD",
                subtitle = "Esportes, Variedades e TV Ao Vivo",
                streamUrl = "https://cdn.jmvstream.com/w/AVJ-15235/playlist/playlist.m3u8",
                embedUrl = null,
                isLive = true,
                category = "Abertos & Regionais"
            ),
            PlayableVideo(
                id = "canal_megatv",
                title = "Mega TV HD",
                subtitle = "Variedades, Vendas e Ao Vivo • CXTV",
                streamUrl = "http://rtmp.cdn.upx.net.br:1935/00135_4/myStream.sdp/playlist.m3u8",
                embedUrl = null,
                isLive = true,
                category = "Abertos & Regionais"
            ),
            PlayableVideo(
                id = "canal_sbt",
                title = "SBT HD",
                subtitle = "Futebol, Entretenimento e Shows",
                streamUrl = "https://sbt-live.akamaized.net/hls/live/2039234/sbt-nacional/master.m3u8",
                embedUrl = null,
                isLive = true,
                category = "Abertos & Regionais"
            ),
            PlayableVideo(
                id = "canal_band",
                title = "Band TV HD",
                subtitle = "Futebol, Jogo Aberto e Notícias",
                streamUrl = "https://evp2.rbm.band.uol.com.br/bandtv/bandtv/playlist.m3u8",
                embedUrl = null,
                isLive = true,
                category = "Abertos & Regionais"
            ),
            PlayableVideo(
                id = "canal_tvfeira",
                title = "TV Feira HD",
                subtitle = "TV Brasil Feira de Santana, Cultura e Jornalismo • CXTV",
                streamUrl = "https://www.cxtv.com.br/tv-ao-vivo/tv-feira-de-santana",
                embedUrl = "https://www.cxtv.com.br/tv-ao-vivo/tv-feira-de-santana",
                forceWebPlayer = true,
                isLive = true,
                category = "Abertos & Regionais"
            )
        )

    fun getQuickChannels(): List<PlayableVideo> {
        val custom = loadCustomChannels()
        val deleted = prefs.getStringSet("deleted_channel_ids", emptySet()) ?: emptySet()
        val customIds = custom.map { it.id }.toSet()
        val defaultChannels = getDefaultChannels()
        val activeDefaults = defaultChannels.filter { !customIds.contains(it.id) && !deleted.contains(it.id) }
        return custom.filter { !deleted.contains(it.id) } + activeDefaults
    }

    private fun getDefaultChannelOptions(detailUrl: String): List<ChannelOption> {
        return listOf(
            ChannelOption(
                id = "ch_1",
                name = "Opção 1 (Full HD)",
                pageUrl = if (detailUrl.isNotBlank()) detailUrl else "https://links.temporariofutemais.com/prime.php?c=canal3"
            ),
            ChannelOption(
                id = "ch_2",
                name = "Opção 2 (HD)",
                pageUrl = "https://links.temporariofutemais.com/prime.php?c=canal3"
            ),
            ChannelOption(
                id = "ch_3",
                name = "Opção 3 (Mobile)",
                pageUrl = "https://links2.temporariofutemais.com/canais3/opcao1.php?id=canal3"
            ),
            ChannelOption(
                id = "ch_4",
                name = "Opção 4 (Alternativo)",
                pageUrl = "https://links2.temporariofutemais.com/canais3/opcao1.php?id=canal3"
            )
        )
    }

    private fun getFallbackMatches(): List<MatchItem> {
        return listOf(
            MatchItem(
                id = "14250",
                homeTeam = "Botafogo SP",
                homeLogoUrl = "https://imgs.temporariofutemais.com/imgs/botafogo-sp.png",
                awayTeam = "Atlético GO",
                awayLogoUrl = "https://imgs.temporariofutemais.com/imgs/atletico-go.png",
                championship = "Campeonato Brasileiro Série B",
                time = "19:30",
                dateTag = "HOJE - Ao Vivo",
                detailUrl = "https://temporariofutemais.com/canalapps.php?id=14250",
                isLiveNow = true
            ),
            MatchItem(
                id = "14251",
                homeTeam = "CRB",
                homeLogoUrl = "https://imgs.temporariofutemais.com/imgs/crb.png",
                awayTeam = "Juventude",
                awayLogoUrl = "https://imgs.temporariofutemais.com/imgs/juventude.png",
                championship = "Campeonato Brasileiro Série B",
                time = "19:30",
                dateTag = "HOJE - Ao Vivo",
                detailUrl = "https://temporariofutemais.com/canalapps.php?id=14251",
                isLiveNow = true
            ),
            MatchItem(
                id = "14252",
                homeTeam = "Flamengo",
                homeLogoUrl = "https://imgs.temporariofutemais.com/imgs/flamengo.png",
                awayTeam = "Palmeiras",
                awayLogoUrl = "https://imgs.temporariofutemais.com/imgs/palmeiras.png",
                championship = "Copa do Brasil",
                time = "21:30",
                dateTag = "HOJE - Destaque",
                detailUrl = "https://temporariofutemais.com/canalapps.php?id=14252",
                isLiveNow = true
            ),
            MatchItem(
                id = "14253",
                homeTeam = "Real Madrid",
                homeLogoUrl = "https://imgs.temporariofutemais.com/imgs/real-madrid.png",
                awayTeam = "Barcelona",
                awayLogoUrl = "https://imgs.temporariofutemais.com/imgs/barcelona.png",
                championship = "UEFA Champions League",
                time = "16:00",
                dateTag = "AMANHÃ",
                detailUrl = "https://temporariofutemais.com/canalapps.php?id=14253",
                isLiveNow = false
            ),
            MatchItem(
                id = "14254",
                homeTeam = "Corinthians",
                homeLogoUrl = "https://imgs.temporariofutemais.com/imgs/corinthians.png",
                awayTeam = "São Paulo",
                awayLogoUrl = "https://imgs.temporariofutemais.com/imgs/sao-paulo.png",
                championship = "Campeonato Paulista",
                time = "18:00",
                dateTag = "AMANHÃ",
                detailUrl = "https://temporariofutemais.com/canalapps.php?id=14254",
                isLiveNow = false
            )
        )
    }

    companion object {
        private const val TAG = "FutemaisRepository"
    }
}

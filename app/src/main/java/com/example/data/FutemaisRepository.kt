package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.models.ChannelOption
import com.example.data.models.ChannelTestSummary
import com.example.data.models.EpisodeItem
import com.example.data.models.MatchItem
import com.example.data.models.MediaContentType
import com.example.data.models.MediaItem
import com.example.data.models.PlayableVideo
import com.example.data.models.SeasonItem
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

    private var channelsListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var mediaListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    private fun loadFavorites(): Set<String> {
        return prefs.getStringSet("favorite_ids", emptySet()) ?: emptySet()
    }

    private val _favoriteIds = MutableStateFlow<Set<String>>(loadFavorites())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private val _mediaCatalogFlow = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaCatalogFlow: StateFlow<List<MediaItem>> = _mediaCatalogFlow.asStateFlow()

    init {
        syncFromFirestore {}
        syncMediaFromFirestore {}
        _mediaCatalogFlow.value = getMediaCatalog()
    }

    fun parseChannelsJson(jsonStr: String): List<PlayableVideo> {
        if (jsonStr.isBlank() || jsonStr == "[]") return emptyList()
        return try {
            val arr = org.json.JSONArray(jsonStr)
            val list = mutableListOf<PlayableVideo>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val idStr = obj.optString("id").takeIf { it.isNotBlank() } ?: "custom_${System.currentTimeMillis()}_$i"
                val streamUrl = obj.optString("streamUrl").takeIf { it.isNotBlank() } ?: obj.optString("embedUrl")
                val isForceWeb = obj.optBoolean("forceWebPlayer", false)
                val embedUrl = obj.optString("embedUrl").takeIf { it.isNotBlank() } ?: if (isForceWeb) streamUrl else null
                val title = obj.optString("title").takeIf { it.isNotBlank() } ?: "Canal Rápido"
                val subtitle = obj.optString("subtitle").takeIf { it.isNotBlank() } ?: "Canal Personalizado • Admin"
                val posterUrl = obj.optString("posterUrl").takeIf { it.isNotBlank() }
                val category = obj.optString("category").takeIf { it.isNotBlank() } ?: "Esportes"
                val isWorking = obj.optBoolean("isWorking", true)
                val isLive = obj.optBoolean("isLive", true)

                list.add(
                    PlayableVideo(
                        id = idStr,
                        title = title,
                        subtitle = subtitle,
                        streamUrl = streamUrl,
                        posterUrl = posterUrl,
                        isLive = isLive,
                        embedUrl = embedUrl,
                        forceWebPlayer = isForceWeb,
                        category = category,
                        isWorking = isWorking
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing custom channels JSON", e)
            emptyList()
        }
    }

    fun syncFromFirestore(onComplete: () -> Unit = {}) {
        channelsListenerRegistration?.remove()
        channelsListenerRegistration = firestore?.collection("app_data")?.document("channels")
            ?.addSnapshotListener { doc, error ->
                if (error != null) {
                    Log.w(TAG, "Notice: could not sync channels from Firestore (${error.message ?: "client offline"})")
                    onComplete()
                    return@addSnapshotListener
                }

                if (doc != null && doc.exists()) {
                    val customCats = doc.getString("custom_channel_categories")
                    val remoteChannelsJson = doc.getString("custom_quick_channels")
                    val hasDeletedKey = doc.contains("deleted_channel_ids") || doc.get("deleted_channel_ids") != null
                    val deletedIds = (doc.get("deleted_channel_ids") as? List<*>)?.mapNotNull { it?.toString() }?.toSet() ?: emptySet()
                    val hasOfflineKey = doc.contains("offline_channel_ids") || doc.get("offline_channel_ids") != null
                    val offlineIds = (doc.get("offline_channel_ids") as? List<*>)?.mapNotNull { it?.toString() }?.toSet() ?: emptySet()

                    val editor = prefs.edit()

                    if (hasDeletedKey) {
                        editor.putStringSet("deleted_channel_ids", deletedIds)
                    }

                    if (hasOfflineKey) {
                        editor.putStringSet("offline_channel_ids", offlineIds)
                    }

                    // Smart Merge Categories
                    if (!customCats.isNullOrBlank()) {
                        val localCats = getCustomCategories()
                        val remoteCats = try {
                            val arr = org.json.JSONArray(customCats)
                            (0 until arr.length()).map { arr.getString(it) }
                        } catch (_: Exception) { emptyList() }
                        val mergedCats = (localCats + remoteCats).distinct()
                        val arr = org.json.JSONArray()
                        mergedCats.forEach { arr.put(it) }
                        editor.putString("custom_channel_categories", arr.toString())
                    }

                    // Smart Merge Custom Channels
                    if (!remoteChannelsJson.isNullOrBlank()) {
                        val remoteList = parseChannelsJson(remoteChannelsJson)
                        val localList = loadCustomChannels()
                        val currentDeleted = (prefs.getStringSet("deleted_channel_ids", emptySet()) ?: emptySet()) + deletedIds

                        val map = mutableMapOf<String, PlayableVideo>()
                        // Local first
                        localList.forEach { ch ->
                            if (!currentDeleted.contains(ch.id)) {
                                map[ch.id] = ch
                            }
                        }
                        // Remote overrides / appends
                        remoteList.forEach { ch ->
                            if (!currentDeleted.contains(ch.id)) {
                                map[ch.id] = ch
                            }
                        }

                        val mergedList = map.values.toList()
                        saveCustomChannelsInternal(mergedList, editor)
                    }

                    editor.apply()
                }
                onComplete()
            }
    }

    private fun syncToFirestore() {
        val customCats = prefs.getString("custom_channel_categories", "[]") ?: "[]"
        val customChannels = prefs.getString("custom_quick_channels", "[]") ?: "[]"
        val deletedIds = prefs.getStringSet("deleted_channel_ids", emptySet())?.toList() ?: emptyList()
        val offlineIds = prefs.getStringSet("offline_channel_ids", emptySet())?.toList() ?: emptyList()

        val data = hashMapOf(
            "custom_channel_categories" to customCats,
            "custom_quick_channels" to customChannels,
            "deleted_channel_ids" to deletedIds,
            "offline_channel_ids" to offlineIds,
            "last_updated" to System.currentTimeMillis()
        )

        firestore?.collection("app_data")?.document("channels")?.set(data, com.google.firebase.firestore.SetOptions.merge())
            ?.addOnFailureListener { e ->
                Log.e(TAG, "Error saving channels to Firestore", e)
            }
    }

    fun syncUpdateFromFirestore(onUpdateFound: (url: String, version: String, timestamp: Long) -> Unit) {
        firestore?.collection("app_data")?.document("update_info")?.addSnapshotListener { doc, error ->
            if (error != null) return@addSnapshotListener
            if (doc != null && doc.exists()) {
                val url = doc.getString("latest_apk_url") ?: ""
                val version = doc.getString("latest_version_name") ?: ""
                val timestamp = doc.getLong("timestamp") ?: 0L
                if (url.isNotBlank() && version.isNotBlank()) {
                    val editor = prefs.edit()
                    editor.putString("latest_apk_url", url)
                    editor.putString("latest_version_name", version)
                    editor.apply()
                    onUpdateFound(url, version, timestamp)
                }
            }
        }
    }

    fun publishUpdateToFirestore(url: String, version: String) {
        val data = hashMapOf(
            "latest_apk_url" to url,
            "latest_version_name" to version,
            "timestamp" to System.currentTimeMillis()
        )
        firestore?.collection("app_data")?.document("update_info")?.set(data)
    }

    fun publishWvcUrlToFirestore(url: String) {
        val data = hashMapOf(
            "wvc_apk_url" to url
        )
        firestore?.collection("app_data")?.document("wvc_info")?.set(data)
    }

    fun publishRegistrationEnabledToFirestore(enabled: Boolean) {
        val data = hashMapOf(
            "registration_enabled" to enabled,
            "timestamp" to System.currentTimeMillis()
        )
        firestore?.collection("app_data")?.document("registration_info")?.set(data)
    }

    fun syncRegistrationEnabledFromFirestore(onStatusChanged: (enabled: Boolean) -> Unit) {
        firestore?.collection("app_data")?.document("registration_info")?.addSnapshotListener { doc, error ->
            if (error != null) return@addSnapshotListener
            if (doc != null && doc.exists()) {
                val enabled = doc.getBoolean("registration_enabled") ?: true
                prefs.edit().putBoolean("registration_enabled", enabled).apply()
                onStatusChanged(enabled)
            }
        }
    }

    fun syncWvcUrlFromFirestore(onUrlFound: (url: String) -> Unit) {
        firestore?.collection("app_data")?.document("wvc_info")?.addSnapshotListener { doc, error ->
            if (error != null) return@addSnapshotListener
            if (doc != null && doc.exists()) {
                val url = doc.getString("wvc_apk_url") ?: ""
                if (url.isNotBlank()) {
                    prefs.edit().putString("wvc_apk_url", url).apply()
                    onUrlFound(url)
                }
            }
        }
    }

    fun getSupportWhatsappNumber(): String {
        return prefs.getString("support_whatsapp_number", "(75) 9 9249-0975") ?: "(75) 9 9249-0975"
    }

    fun saveSupportWhatsappNumber(number: String) {
        prefs.edit().putString("support_whatsapp_number", number).apply()
        publishSupportWhatsappToFirestore(number)
    }

    fun publishSupportWhatsappToFirestore(number: String) {
        val data = hashMapOf(
            "support_whatsapp_number" to number,
            "timestamp" to System.currentTimeMillis()
        )
        firestore?.collection("app_data")?.document("support_info")?.set(data)
    }

    fun syncSupportWhatsappFromFirestore(onNumberFound: (number: String) -> Unit) {
        firestore?.collection("app_data")?.document("support_info")?.addSnapshotListener { doc, error ->
            if (error != null) return@addSnapshotListener
            if (doc != null && doc.exists()) {
                val number = doc.getString("support_whatsapp_number") ?: ""
                if (number.isNotBlank()) {
                    prefs.edit().putString("support_whatsapp_number", number).apply()
                    onNumberFound(number)
                }
            }
        }
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val channelTestClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
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

    fun getCategoryRenames(): Map<String, String> {
        val jsonStr = prefs.getString("category_renames_map", null) ?: return emptyMap()
        return try {
            val obj = org.json.JSONObject(jsonStr)
            val map = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.getString(k)
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveCategoryRename(oldName: String, newName: String) {
        val current = getCategoryRenames().toMutableMap()
        current[oldName] = newName
        current.forEach { (k, v) ->
            if (v.equals(oldName, ignoreCase = true)) {
                current[k] = newName
            }
        }
        val obj = org.json.JSONObject()
        current.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString("category_renames_map", obj.toString()).apply()
    }

    fun getEffectiveDefaultCategories(): List<String> {
        val renames = getCategoryRenames()
        return defaultCategories.map { cat ->
            renames[cat] ?: cat
        }.distinct()
    }

    fun addCustomCategory(category: String): List<String> {
        val clean = category.trim()
        if (clean.isBlank()) return getCustomCategories()
        val current = getCustomCategories().toMutableList()
        val isDefault = getEffectiveDefaultCategories().any { it.equals(clean, ignoreCase = true) }
        val alreadyExists = current.any { it.equals(clean, ignoreCase = true) }
        if (!isDefault && !alreadyExists) {
            current.add(clean)
            saveCustomCategories(current)
        }
        return getCustomCategories()
    }

    fun deleteCustomCategory(category: String): List<String> {
        val clean = category.trim()
        val current = getCustomCategories().toMutableList()
        current.removeAll { it.equals(clean, ignoreCase = true) }
        saveCustomCategories(current)

        if (defaultCategories.any { it.equals(clean, ignoreCase = true) } || getCategoryRenames().containsKey(clean)) {
            saveCategoryRename(clean, "Outros")
        }

        val customChannels = loadCustomChannels().toMutableList()
        val allChannels = getQuickChannels()
        var changed = false
        for (ch in allChannels) {
            if (ch.category.equals(clean, ignoreCase = true)) {
                val updatedCh = ch.copy(category = "Outros")
                val cIdx = customChannels.indexOfFirst { it.id == ch.id }
                if (cIdx >= 0) {
                    customChannels[cIdx] = updatedCh
                } else {
                    customChannels.add(updatedCh)
                }
                changed = true
            }
        }
        if (changed) {
            saveCustomChannels(customChannels)
            syncToFirestore()
        }

        return getCustomCategories()
    }

    fun updateCustomCategory(oldName: String, newName: String): List<String> {
        val cleanOld = oldName.trim()
        val cleanNew = newName.trim()
        if (cleanOld.isBlank() || cleanNew.isBlank()) return getCustomCategories()

        val isDefaultOrRenamed = defaultCategories.any { it.equals(cleanOld, ignoreCase = true) } ||
                getCategoryRenames().any { it.key.equals(cleanOld, ignoreCase = true) || it.value.equals(cleanOld, ignoreCase = true) }
        if (isDefaultOrRenamed) {
            saveCategoryRename(cleanOld, cleanNew)
        }

        val current = getCustomCategories().toMutableList()
        val idx = current.indexOfFirst { it.equals(cleanOld, ignoreCase = true) }
        if (idx >= 0) {
            current[idx] = cleanNew
            saveCustomCategories(current)
        } else {
            val isNewDefault = getEffectiveDefaultCategories().any { it.equals(cleanNew, ignoreCase = true) }
            val alreadyExists = current.any { it.equals(cleanNew, ignoreCase = true) }
            if (!isNewDefault && !alreadyExists) {
                current.add(cleanNew)
                saveCustomCategories(current)
            }
        }

        val customChannels = loadCustomChannels().toMutableList()
        val allChannels = getQuickChannels()
        var changed = false

        for (ch in allChannels) {
            if (ch.category.equals(cleanOld, ignoreCase = true)) {
                val updatedCh = ch.copy(category = cleanNew)
                val cIdx = customChannels.indexOfFirst { it.id == ch.id }
                if (cIdx >= 0) {
                    customChannels[cIdx] = updatedCh
                } else {
                    customChannels.add(updatedCh)
                }
                changed = true
            }
        }

        if (changed) {
            saveCustomChannels(customChannels)
            syncToFirestore()
        }

        return getCustomCategories()
    }

    fun getAllCategories(): List<String> {
        val custom = getCustomCategories()
        val channelCats = getQuickChannels().mapNotNull { it.category }.filter { it.isNotBlank() }
        return (getEffectiveDefaultCategories() + custom + channelCats).distinct()
    }

    fun loadCustomChannels(): List<PlayableVideo> {
        val jsonStr = prefs.getString("custom_quick_channels", null) ?: return emptyList()
        return parseChannelsJson(jsonStr)
    }

    private fun saveCustomChannelsInternal(list: List<PlayableVideo>, editor: SharedPreferences.Editor) {
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
                    put("isWorking", ch.isWorking)
                }
                arr.put(obj)
            }
            editor.putString("custom_quick_channels", arr.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error serializing custom channels", e)
        }
    }

    private fun saveCustomChannels(list: List<PlayableVideo>) {
        val editor = prefs.edit()
        saveCustomChannelsInternal(list, editor)
        editor.apply()
    }

    fun toggleChannelWorkingStatus(channelId: String): List<PlayableVideo> {
        val offline = (prefs.getStringSet("offline_channel_ids", emptySet()) ?: emptySet()).toMutableSet()
        val willBeWorking = offline.contains(channelId)
        if (willBeWorking) {
            offline.remove(channelId)
        } else {
            offline.add(channelId)
        }
        prefs.edit().putStringSet("offline_channel_ids", offline).apply()

        val custom = loadCustomChannels().toMutableList()
        val idx = custom.indexOfFirst { it.id == channelId }
        if (idx >= 0) {
            custom[idx] = custom[idx].copy(isWorking = willBeWorking)
            saveCustomChannels(custom)
        }

        syncToFirestore()
        return getQuickChannels()
    }

    fun setChannelWorkingStatus(channelId: String, isWorking: Boolean): List<PlayableVideo> {
        val offline = (prefs.getStringSet("offline_channel_ids", emptySet()) ?: emptySet()).toMutableSet()
        if (isWorking) {
            offline.remove(channelId)
        } else {
            offline.add(channelId)
        }
        prefs.edit().putStringSet("offline_channel_ids", offline).apply()

        val custom = loadCustomChannels().toMutableList()
        val idx = custom.indexOfFirst { it.id == channelId }
        if (idx >= 0) {
            custom[idx] = custom[idx].copy(isWorking = isWorking)
            saveCustomChannels(custom)
        }

        syncToFirestore()
        return getQuickChannels()
    }

    suspend fun testSingleChannel(channel: PlayableVideo): Boolean = withContext(Dispatchers.IO) {
        val targetUrl = channel.streamUrl.ifBlank { channel.embedUrl ?: "" }.trim()
        if (targetUrl.isBlank() || (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://"))) {
            return@withContext false
        }

        try {
            val reqBuilder = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "*/*")

            channel.headers.forEach { (k, v) ->
                reqBuilder.header(k, v)
            }

            var response: okhttp3.Response? = null
            try {
                // Tenta HEAD request primeiro para economia de dados e velocidade
                response = channelTestClient.newCall(reqBuilder.head().build()).execute()
            } catch (_: Exception) {
                // Tenta fallback GET abaixo
            }

            if (response == null || response.code == 405 || response.code == 403) {
                response?.close()
                // Fallback para GET com range leve
                val getReq = reqBuilder.get().header("Range", "bytes=0-2048").build()
                response = channelTestClient.newCall(getReq).execute()
            }

            response.use { resp ->
                val code = resp.code
                if (code in 200..399) {
                    val contentType = resp.header("Content-Type", "").orEmpty().lowercase()
                    if (contentType.contains("text/html") && !channel.forceWebPlayer && channel.streamUrl.endsWith(".m3u8", ignoreCase = true)) {
                        val bodySnippet = try { resp.peekBody(512).string().lowercase() } catch (_: Exception) { "" }
                        if (bodySnippet.contains("404 not found") || bodySnippet.contains("error 404") || bodySnippet.contains("file not found")) {
                            return@withContext false
                        }
                    }
                    return@withContext true
                } else {
                    return@withContext false
                }
            }
        } catch (_: Exception) {
            return@withContext false
        }
    }

    suspend fun testAllChannels(
        onProgress: (index: Int, total: Int, channel: PlayableVideo, isWorking: Boolean) -> Unit
    ): ChannelTestSummary = withContext(Dispatchers.IO) {
        val currentChannels = getQuickChannels()
        val total = currentChannels.size
        var workingCount = 0
        var offlineCount = 0
        val offlineList = mutableListOf<PlayableVideo>()
        val newlyOfflineList = mutableListOf<PlayableVideo>()

        currentChannels.forEachIndexed { index, channel ->
            val isWorking = testSingleChannel(channel)
            if (isWorking) {
                workingCount++
            } else {
                offlineCount++
                offlineList.add(channel.copy(isWorking = false))
                if (channel.isWorking) {
                    newlyOfflineList.add(channel.copy(isWorking = false))
                }
            }

            setChannelWorkingStatus(channel.id, isWorking)
            onProgress(index + 1, total, channel, isWorking)
        }

        ChannelTestSummary(
            total = total,
            workingCount = workingCount,
            offlineCount = offlineCount,
            newlyOfflineChannels = newlyOfflineList,
            offlineChannels = offlineList,
            timestamp = System.currentTimeMillis()
        )
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
        _mediaCatalogFlow.value = getMediaCatalog()
    }

    // =========================================================================
    // FILMES & SÉRIES CATALOG MANAGEMENT
    // =========================================================================

    fun parseMediaCatalogJson(jsonStr: String): List<MediaItem> {
        if (jsonStr.isBlank() || jsonStr == "[]") return emptyList()
        return try {
            val arr = org.json.JSONArray(jsonStr)
            val list = mutableListOf<MediaItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: "media_${System.currentTimeMillis()}_$i"
                val title = obj.optString("title", "Sem título")
                val typeStr = obj.optString("type", "MOVIE")
                val type = try { MediaContentType.valueOf(typeStr) } catch (_: Exception) { MediaContentType.MOVIE }
                val coverUrl = obj.optString("coverUrl", "")
                val backdropUrl = obj.optString("backdropUrl").takeIf { it.isNotBlank() }
                val synopsis = obj.optString("synopsis", "")
                val category = obj.optString("category", "Geral")
                val year = obj.optString("year", "")
                val rating = obj.optString("rating", "")
                val movieStreamUrl = obj.optString("movieStreamUrl").takeIf { it.isNotBlank() }
                val isWebPlayer = obj.optBoolean("isWebPlayer", false)
                val isWorking = obj.optBoolean("isWorking", true)
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())

                val seasonsList = mutableListOf<SeasonItem>()
                val seasonsArr = obj.optJSONArray("seasons")
                if (seasonsArr != null) {
                    for (s in 0 until seasonsArr.length()) {
                        val sObj = seasonsArr.getJSONObject(s)
                        val sNumber = sObj.optInt("seasonNumber", s + 1)
                        val sTitle = sObj.optString("title", "Temporada $sNumber")
                        val epsList = mutableListOf<EpisodeItem>()
                        val epsArr = sObj.optJSONArray("episodes")
                        if (epsArr != null) {
                            for (e in 0 until epsArr.length()) {
                                val eObj = epsArr.getJSONObject(e)
                                val eId = eObj.optString("id", "${id}_s${sNumber}_e${e + 1}")
                                val eNumber = eObj.optInt("episodeNumber", e + 1)
                                val eTitle = eObj.optString("title", "Episódio $eNumber")
                                val eUrl = eObj.optString("streamUrl", "")
                                val eWeb = eObj.optBoolean("isWebPlayer", false)
                                val eDur = eObj.optString("duration").takeIf { it.isNotBlank() }
                                val eSyn = eObj.optString("synopsis").takeIf { it.isNotBlank() }
                                epsList.add(
                                    EpisodeItem(
                                        id = eId,
                                        episodeNumber = eNumber,
                                        title = eTitle,
                                        streamUrl = eUrl,
                                        isWebPlayer = eWeb,
                                        duration = eDur,
                                        synopsis = eSyn
                                    )
                                )
                            }
                        }
                        seasonsList.add(SeasonItem(seasonNumber = sNumber, title = sTitle, episodes = epsList))
                    }
                }

                list.add(
                    MediaItem(
                        id = id,
                        title = title,
                        type = type,
                        coverUrl = coverUrl,
                        backdropUrl = backdropUrl,
                        synopsis = synopsis,
                        category = category,
                        year = year,
                        rating = rating,
                        movieStreamUrl = movieStreamUrl,
                        isWebPlayer = isWebPlayer,
                        seasons = seasonsList,
                        isWorking = isWorking,
                        createdAt = createdAt
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing media catalog JSON", e)
            emptyList()
        }
    }

    fun serializeMediaCatalog(list: List<MediaItem>): String {
        return try {
            val arr = org.json.JSONArray()
            list.forEach { item ->
                val obj = org.json.JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("type", item.type.name)
                    put("coverUrl", item.coverUrl)
                    put("backdropUrl", item.backdropUrl ?: "")
                    put("synopsis", item.synopsis)
                    put("category", item.category)
                    put("year", item.year)
                    put("rating", item.rating)
                    put("movieStreamUrl", item.movieStreamUrl ?: "")
                    put("isWebPlayer", item.isWebPlayer)
                    put("isWorking", item.isWorking)
                    put("createdAt", item.createdAt)

                    val sArr = org.json.JSONArray()
                    item.seasons.forEach { season ->
                        val sObj = org.json.JSONObject().apply {
                            put("seasonNumber", season.seasonNumber)
                            put("title", season.title)
                            val eArr = org.json.JSONArray()
                            season.episodes.forEach { ep ->
                                val eObj = org.json.JSONObject().apply {
                                    put("id", ep.id)
                                    put("episodeNumber", ep.episodeNumber)
                                    put("title", ep.title)
                                    put("streamUrl", ep.streamUrl)
                                    put("isWebPlayer", ep.isWebPlayer)
                                    put("duration", ep.duration ?: "")
                                    put("synopsis", ep.synopsis ?: "")
                                }
                                eArr.put(eObj)
                            }
                            put("episodes", eArr)
                        }
                        sArr.put(sObj)
                    }
                    put("seasons", sArr)
                }
                arr.put(obj)
            }
            arr.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error serializing media catalog", e)
            "[]"
        }
    }

    fun getDefaultMediaCatalog(): List<MediaItem> {
        return listOf(
            MediaItem(
                id = "movie_oppenheimer",
                title = "Oppenheimer",
                type = MediaContentType.MOVIE,
                coverUrl = "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/w780/fm6KqXpk3M2HVveHwCrBSSBaO0V.jpg",
                synopsis = "A fascinante história do físico J. Robert Oppenheimer, seu papel central no Projeto Manhattan e o desenvolvimento da bomba atômica durante a Segunda Guerra Mundial.",
                category = "Drama / História",
                year = "2023",
                rating = "8.9",
                movieStreamUrl = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
                isWebPlayer = false
            ),
            MediaItem(
                id = "movie_interstellar",
                title = "Interestelar",
                type = MediaContentType.MOVIE,
                coverUrl = "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/w780/rAiYTPIENuFaYYahM2sQ875MkWk.jpg",
                synopsis = "Uma equipe de bravos exploradores viaja através de um buraco de minhoca no espaço profundo em uma corrida contra o tempo para garantir a sobrevivência da raça humana.",
                category = "Ficção Científica",
                year = "2014",
                rating = "8.7",
                movieStreamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                isWebPlayer = false
            ),
            MediaItem(
                id = "movie_avengers_endgame",
                title = "Vingadores: Ultimato",
                type = MediaContentType.MOVIE,
                coverUrl = "https://image.tmdb.org/t/p/w500/or06FN3Dka5tukK1e9sl16pB3iy.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/w780/7RyHsO4yDXtBv1zUU3mTpHeQ0d5.jpg",
                synopsis = "Após o estalar de dedos devastador de Thanos, os heróis sobreviventes se reúnem mais uma vez para desfazer o caos e restaurar o equilíbrio em todo o universo.",
                category = "Ação / Aventura",
                year = "2019",
                rating = "8.4",
                movieStreamUrl = "https://bitmovin-a.akamaihd.net/content/MI201109210084_1/m3u8s/f08e80da-bf1d-4e3d-8899-f0f6155f6efa.m3u8",
                isWebPlayer = false
            ),
            MediaItem(
                id = "movie_spider_verse",
                title = "Homem-Aranha: Através do Aranhaverso",
                type = MediaContentType.MOVIE,
                coverUrl = "https://image.tmdb.org/t/p/w500/8Vt6mWEReuy4Of61Lnj5Xj704m8.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/w780/4HodYYKEIsGOdinkGi2Ucz6X9i0.jpg",
                synopsis = "Miles Morales é arremessado através do multiverso, unindo forças com Gwen Stacy e uma equipe de heróis-aranha para enfrentar uma ameaça colossal.",
                category = "Animação",
                year = "2023",
                rating = "8.8",
                movieStreamUrl = "https://bitmovin-a.akamaihd.net/content/sintel/hls/playlist.m3u8",
                isWebPlayer = false
            ),
            MediaItem(
                id = "series_stranger_things",
                title = "Stranger Things",
                type = MediaContentType.SERIES,
                coverUrl = "https://image.tmdb.org/t/p/w500/49WJfeN0moxb9IPfGn8AIqMGskD.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/w780/56v2KjBlU4XaOv9rVYEQypROD7P.jpg",
                synopsis = "Quando um jovem garoto desaparece repentinamente, uma pequena cidade descobre mistérios paranormais, experimentos governamentais ultrassecretos e uma jovem extraordinária chamada Eleven.",
                category = "Suspense / Ficção",
                year = "2016",
                rating = "8.7",
                seasons = listOf(
                    SeasonItem(
                        seasonNumber = 1,
                        title = "1ª Temporada",
                        episodes = listOf(
                            EpisodeItem(
                                id = "st_s1_e1",
                                episodeNumber = 1,
                                title = "Capítulo Um: O Desaparecimento de Will Byers",
                                streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                                duration = "48m",
                                synopsis = "No caminho para casa depois de um jogo de RPG com os amigos, o jovem Will vê algo aterrorizante."
                            ),
                            EpisodeItem(
                                id = "st_s1_e2",
                                episodeNumber = 2,
                                title = "Capítulo Dois: A Estranha da Rua Maple",
                                streamUrl = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
                                duration = "55m",
                                synopsis = "Lucas, Mike e Dustin tentam conversar com a garota que encontraram na floresta."
                            ),
                            EpisodeItem(
                                id = "st_s1_e3",
                                episodeNumber = 3,
                                title = "Capítulo Três: Caramanchão",
                                streamUrl = "https://bitmovin-a.akamaihd.net/content/sintel/hls/playlist.m3u8",
                                duration = "51m",
                                synopsis = "Joyce se recusa a acreditar que Will se foi e tenta se comunicar com ele por meio de luzes de Natal."
                            ),
                            EpisodeItem(
                                id = "st_s1_e4",
                                episodeNumber = 4,
                                title = "Capítulo Quatro: O Corpo",
                                streamUrl = "https://bitmovin-a.akamaihd.net/content/MI201109210084_1/m3u8s/f08e80da-bf1d-4e3d-8899-f0f6155f6efa.m3u8",
                                duration = "50m",
                                synopsis = "Os meninos transformam Eleven para que ela se pareça com uma garota normal e use o rádio da escola."
                            )
                        )
                    ),
                    SeasonItem(
                        seasonNumber = 2,
                        title = "2ª Temporada",
                        episodes = listOf(
                            EpisodeItem(
                                id = "st_s2_e1",
                                episodeNumber = 1,
                                title = "Capítulo Um: MADMAX",
                                streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                                duration = "48m",
                                synopsis = "Com a chegada do Halloween, uma nova garota na escola atrai a atenção dos meninos."
                            ),
                            EpisodeItem(
                                id = "st_s2_e2",
                                episodeNumber = 2,
                                title = "Capítulo Dois: Gostosuras ou Travessuras, Aberração",
                                streamUrl = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
                                duration = "56m",
                                synopsis = "Will tem uma visão perturbadora na noite de Halloween e Dustin encontra um animal de estimação incomum."
                            )
                        )
                    )
                )
            ),
            MediaItem(
                id = "series_breaking_bad",
                title = "Breaking Bad",
                type = MediaContentType.SERIES,
                coverUrl = "https://image.tmdb.org/t/p/w500/ggFHVNu6YYI5L9pCfOacjizRGt.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/w780/tsRy63Mu5cu8etL1X7ZLyf7UP1M.jpg",
                synopsis = "Walter White, um professor de química do ensino médio diagnosticado com câncer de pulmão em estágio terminal, decide produzir metanfetamina de alta pureza com seu ex-aluno Jesse Pinkman.",
                category = "Drama / Policial",
                year = "2008",
                rating = "9.5",
                seasons = listOf(
                    SeasonItem(
                        seasonNumber = 1,
                        title = "1ª Temporada",
                        episodes = listOf(
                            EpisodeItem(
                                id = "bb_s1_e1",
                                episodeNumber = 1,
                                title = "Piloto",
                                streamUrl = "https://bitmovin-a.akamaihd.net/content/MI201109210084_1/m3u8s/f08e80da-bf1d-4e3d-8899-f0f6155f6efa.m3u8",
                                duration = "58m",
                                synopsis = "Diagnosticado com câncer terminal, Walter White decide entrar no negócio do tráfico de drogas."
                            ),
                            EpisodeItem(
                                id = "bb_s1_e2",
                                episodeNumber = 2,
                                title = "O Gato no Saco...",
                                streamUrl = "https://bitmovin-a.akamaihd.net/content/sintel/hls/playlist.m3u8",
                                duration = "48m",
                                synopsis = "Walt e Jesse tentam lidar com as consequências de sua primeira experiência de cozimento no deserto."
                            ),
                            EpisodeItem(
                                id = "bb_s1_e3",
                                episodeNumber = 3,
                                title = "...E o Saco no Rio",
                                streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                                duration = "48m",
                                synopsis = "Walt trava uma batalha moral com a difícil decisão que precisa tomar no porão de Jesse."
                            )
                        )
                    )
                )
            ),
            MediaItem(
                id = "series_the_last_of_us",
                title = "The Last of Us",
                type = MediaContentType.SERIES,
                coverUrl = "https://image.tmdb.org/t/p/w500/uKvVjHNqB5VmOrdxqAt2V7JMrHG.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/w780/uDgy6hyPd82kOHh6I95FLtLnj6p.jpg",
                synopsis = "Vinte anos após uma pandemia fúngica devastar a civilização moderna, Joel, um sobrevivente endurecido, é contratado para contrabandear Ellie, uma jovem de 14 anos, para fora de uma zona de quarentena opressiva.",
                category = "Ação / Ficção",
                year = "2023",
                rating = "8.8",
                seasons = listOf(
                    SeasonItem(
                        seasonNumber = 1,
                        title = "1ª Temporada",
                        episodes = listOf(
                            EpisodeItem(
                                id = "tlou_s1_e1",
                                episodeNumber = 1,
                                title = "Quando Estiver Perdido na Escuridão",
                                streamUrl = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
                                duration = "1h 21m",
                                synopsis = "Vinte anos após um surto fúngico arrasar o planeta, os sobreviventes Joel e Tess são encarregados de uma missão que pode mudar o mundo."
                            ),
                            EpisodeItem(
                                id = "tlou_s1_e2",
                                episodeNumber = 2,
                                title = "Infectados",
                                streamUrl = "https://bitmovin-a.akamaihd.net/content/sintel/hls/playlist.m3u8",
                                duration = "53m",
                                synopsis = "Joel e Tess navegam por uma Boston abandonada e inundada com Ellie para escoltá-la ao ponto de encontro dos Vaga-Lumes."
                            ),
                            EpisodeItem(
                                id = "tlou_s1_e3",
                                episodeNumber = 3,
                                title = "Muito, Muito Tempo",
                                streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                                duration = "1h 15m",
                                synopsis = "Quando um estranho se aproxima do seu complexo fortificado, o sobrevivente Bill forja uma conexão improvável com Frank."
                            )
                        )
                    )
                )
            )
        )
    }

    fun loadCustomMediaCatalog(): List<MediaItem> {
        val jsonStr = prefs.getString("custom_media_catalog", null) ?: return emptyList()
        return parseMediaCatalogJson(jsonStr)
    }

    private fun saveCustomMediaCatalog(list: List<MediaItem>) {
        val jsonStr = serializeMediaCatalog(list)
        prefs.edit().putString("custom_media_catalog", jsonStr).apply()
        _mediaCatalogFlow.value = getMediaCatalog()
    }

    fun getMediaCatalog(): List<MediaItem> {
        val custom = loadCustomMediaCatalog()
        val deleted = prefs.getStringSet("deleted_media_ids", emptySet()) ?: emptySet()
        val customIds = custom.map { it.id }.toSet()
        val defaultList = getDefaultMediaCatalog()
        val activeDefaults = defaultList.filter { !customIds.contains(it.id) && !deleted.contains(it.id) }
        val allMedia = custom.filter { !deleted.contains(it.id) } + activeDefaults
        val favs = _favoriteIds.value
        return allMedia.map { item ->
            item.copy(isFavorite = favs.contains(item.id))
        }
    }

    fun addOrUpdateMediaItem(item: MediaItem): List<MediaItem> {
        val custom = loadCustomMediaCatalog().toMutableList()
        val idx = custom.indexOfFirst { it.id == item.id }
        if (idx >= 0) {
            custom[idx] = item
        } else {
            custom.add(0, item)
        }
        saveCustomMediaCatalog(custom)

        val deleted = (prefs.getStringSet("deleted_media_ids", emptySet()) ?: emptySet()).toMutableSet()
        if (deleted.contains(item.id)) {
            deleted.remove(item.id)
            prefs.edit().putStringSet("deleted_media_ids", deleted).apply()
        }

        syncMediaToFirestore()
        val catalog = getMediaCatalog()
        _mediaCatalogFlow.value = catalog
        return catalog
    }

    fun deleteMediaItem(id: String): List<MediaItem> {
        val custom = loadCustomMediaCatalog().toMutableList()
        custom.removeAll { it.id == id }
        saveCustomMediaCatalog(custom)

        val deleted = (prefs.getStringSet("deleted_media_ids", emptySet()) ?: emptySet()).toMutableSet()
        deleted.add(id)
        prefs.edit().putStringSet("deleted_media_ids", deleted).apply()

        syncMediaToFirestore()
        val catalog = getMediaCatalog()
        _mediaCatalogFlow.value = catalog
        return catalog
    }

    fun toggleMediaFavorite(id: String): List<MediaItem> {
        toggleFavorite(id)
        val catalog = getMediaCatalog()
        _mediaCatalogFlow.value = catalog
        return catalog
    }

    private fun syncMediaFromFirestore(onComplete: () -> Unit = {}) {
        mediaListenerRegistration?.remove()
        mediaListenerRegistration = firestore?.collection("app_data")?.document("media_catalog")
            ?.addSnapshotListener { doc, error ->
                if (error != null) {
                    Log.w(TAG, "Notice: could not sync media catalog from Firestore (${error.message ?: "client offline"})")
                    onComplete()
                    return@addSnapshotListener
                }

                if (doc != null && doc.exists()) {
                    val remoteMediaJson = doc.getString("media_catalog_json")
                    val deletedIds = (doc.get("deleted_media_ids") as? List<*>)?.mapNotNull { it?.toString() }?.toSet() ?: emptySet()

                    val editor = prefs.edit()
                    if (deletedIds.isNotEmpty()) {
                        editor.putStringSet("deleted_media_ids", deletedIds)
                    }

                    if (!remoteMediaJson.isNullOrBlank()) {
                        val remoteList = parseMediaCatalogJson(remoteMediaJson)
                        val localList = loadCustomMediaCatalog()
                        val currentDeleted = (prefs.getStringSet("deleted_media_ids", emptySet()) ?: emptySet()) + deletedIds

                        val map = mutableMapOf<String, MediaItem>()
                        localList.forEach { if (!currentDeleted.contains(it.id)) map[it.id] = it }
                        remoteList.forEach { if (!currentDeleted.contains(it.id)) map[it.id] = it }

                        val mergedList = map.values.toList()
                        editor.putString("custom_media_catalog", serializeMediaCatalog(mergedList))
                    }

                    editor.apply()
                    _mediaCatalogFlow.value = getMediaCatalog()
                }
                onComplete()
            }
    }

    private fun syncMediaToFirestore() {
        val customMedia = prefs.getString("custom_media_catalog", "[]") ?: "[]"
        val deletedIds = prefs.getStringSet("deleted_media_ids", emptySet())?.toList() ?: emptyList()

        val data = hashMapOf(
            "media_catalog_json" to customMedia,
            "deleted_media_ids" to deletedIds,
            "last_updated" to System.currentTimeMillis()
        )

        firestore?.collection("app_data")?.document("media_catalog")
            ?.set(data, com.google.firebase.firestore.SetOptions.merge())
            ?.addOnFailureListener { e ->
                Log.e(TAG, "Error saving media catalog to Firestore", e)
            }
    }


    suspend fun fetchMatches(url: String = "https://futemais.link/app2/"): Result<List<MatchItem>> =
        withContext(Dispatchers.IO) {
            try {
                val targetUrl = url
                val request = Request.Builder()
                    .url(targetUrl)
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
                    .header("Referer", "https://futemais.link/")
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
                .header("Referer", "https://futemais.link/")
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
                "Referer" to "https://futemais.link/",
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
                streamUrl = "https://cdn.jmvstream.com/w/LVW-10874/LVW10874_Xg72X/playlist.m3u8",
                embedUrl = null,
                isLive = true,
                category = "Católicos (CXTV)"
            ),
            PlayableVideo(
                id = "canal_cxtv_paieterno",
                title = "TV Pai Eterno HD",
                subtitle = "Santuário Basílica do Divino Pai Eterno • CXTV",
                streamUrl = "https://cdn.jmvstream.com/w/LVW-10313/LVW10313_live/playlist.m3u8",
                embedUrl = null,
                isLive = true,
                category = "Católicos (CXTV)"
            ),
            PlayableVideo(
                id = "canal_cxtv_nazare",
                title = "TV Nazaré HD",
                subtitle = "Arquidiocese de Belém e Fé Católica • CXTV",
                streamUrl = "https://cdn.jmvstream.com/w/LVW-8149/LVW8149_4g/playlist.m3u8",
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
                id = "canal_warnertv",
                title = "Warner TV HD",
                subtitle = "Séries, Filmes e Entretenimento 24h • Web Player",
                streamUrl = "https://tv.embedtv.lat/warnertv",
                embedUrl = "https://tv.embedtv.lat/warnertv",
                forceWebPlayer = true,
                isLive = true,
                category = "Filmes & Séries"
            ),
            PlayableVideo(
                id = "canal_tnt",
                title = "TNT HD",
                subtitle = "Filmes, Eventos e Entretenimento 24h • Web Player",
                streamUrl = "https://tv.embedtv.lat/tnt",
                embedUrl = "https://tv.embedtv.lat/tnt",
                forceWebPlayer = true,
                isLive = true,
                category = "Filmes & Séries"
            ),
            PlayableVideo(
                id = "canal_space",
                title = "Space HD",
                subtitle = "Filmes de Ação, Ficção e Terror 24h • Web Player",
                streamUrl = "https://tv.embedtv.lat/space",
                embedUrl = "https://tv.embedtv.lat/space",
                forceWebPlayer = true,
                isLive = true,
                category = "Filmes & Séries"
            ),
            PlayableVideo(
                id = "canal_megapix",
                title = "Megapix HD",
                subtitle = "Sucessos do Cinema Dublados 24h • Web Player",
                streamUrl = "https://tv.embedtv.lat/megapix",
                embedUrl = "https://tv.embedtv.lat/megapix",
                forceWebPlayer = true,
                isLive = true,
                category = "Filmes & Séries"
            ),
            PlayableVideo(
                id = "canal_telecine_pipoca",
                title = "Telecine Pipoca HD",
                subtitle = "Filmes Dublados e Sucessos de Bilheteria • Web Player",
                streamUrl = "https://tv.embedtv.lat/telecinepipoca",
                embedUrl = "https://tv.embedtv.lat/telecinepipoca",
                forceWebPlayer = true,
                isLive = true,
                category = "Filmes & Séries"
            ),
            PlayableVideo(
                id = "canal_hbo",
                title = "HBO HD",
                subtitle = "Séries Exclusivas, Filmes e Lançamentos • Web Player",
                streamUrl = "https://tv.embedtv.lat/hbo",
                embedUrl = "https://tv.embedtv.lat/hbo",
                forceWebPlayer = true,
                isLive = true,
                category = "Filmes & Séries"
            ),
            PlayableVideo(
                id = "canal_universal_tv",
                title = "Universal TV HD",
                subtitle = "Séries Policiais, Ação e Filmes • Web Player",
                streamUrl = "https://tv.embedtv.lat/universaltv",
                embedUrl = "https://tv.embedtv.lat/universaltv",
                forceWebPlayer = true,
                isLive = true,
                category = "Filmes & Séries"
            ),
            PlayableVideo(
                id = "canal_discovery_channel",
                title = "Discovery Channel HD",
                subtitle = "Documentários, Ciência e Natureza • Web Player",
                streamUrl = "https://tv.embedtv.lat/discoverychannel",
                embedUrl = "https://tv.embedtv.lat/discoverychannel",
                forceWebPlayer = true,
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
                streamUrl = "https://tv.embedtv.lat/cartoonito",
                embedUrl = "https://tv.embedtv.lat/cartoonito",
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
        val renames = getCategoryRenames()
        val defaultChannels = getDefaultChannels().map { ch ->
            val mappedCat = renames[ch.category]
            if (mappedCat != null) ch.copy(category = mappedCat) else ch
        }
        val activeDefaults = defaultChannels.filter { !customIds.contains(it.id) && !deleted.contains(it.id) }
        val allChannels = custom.filter { !deleted.contains(it.id) } + activeDefaults
        val favs = _favoriteIds.value
        val offline = prefs.getStringSet("offline_channel_ids", emptySet()) ?: emptySet()
        return allChannels.map { ch ->
            val working = if (offline.contains(ch.id)) false else ch.isWorking
            ch.copy(isFavorite = favs.contains(ch.id), isWorking = working)
        }
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

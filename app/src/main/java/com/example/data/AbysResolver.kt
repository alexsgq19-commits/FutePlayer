package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.FormBody
import java.util.concurrent.TimeUnit

object AbysResolver {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun resolveAbysUrl(embedplayUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val req1 = Request.Builder()
                .url(embedplayUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            
            val resp1 = client.newCall(req1).execute()
            if (!resp1.isSuccessful) return@withContext null
            val text1 = resp1.body?.string() ?: return@withContext null
            
            val serverRegex = Regex("""class="server dropdown-item"\s+data-id="([^"]+)"""")
            val movieRegex = Regex("""data-movie-id="([^"]+)"""")
            
            val serverId = serverRegex.find(text1)?.groupValues?.get(1) ?: return@withContext null
            val movieId = movieRegex.find(text1)?.groupValues?.get(1) ?: return@withContext null
            
            val url2 = "https://embedplayapi.top/ajax/get_stream_link?id=$serverId&movie=$movieId&is_init=false"
            val req2 = Request.Builder()
                .url(url2)
                .header("User-Agent", "Mozilla/5.0")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()
                
            val resp2 = client.newCall(req2).execute()
            val text2 = resp2.body?.string() ?: return@withContext null
            val link = Regex(""""link":"([^"]+)"""").find(text2)?.groupValues?.get(1) ?: return@withContext null
            
            val req3 = Request.Builder()
                .url(link)
                .header("User-Agent", "Mozilla/5.0")
                .build()
                
            val resp3 = client.newCall(req3).execute()
            val text3 = resp3.body?.string() ?: return@withContext null
            
            var playerId = Regex("""data-id="([^"]+)">(?:.(?!data-id))*?ABYS""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .find(text3)?.groupValues?.get(1)
                
            if (playerId == null) {
                playerId = Regex("""class="player_select_item"\s+data-id="([^"]+)"""")
                    .find(text3)?.groupValues?.get(1)
            }
            
            if (playerId == null) return@withContext null
            
            val formBody = FormBody.Builder()
                .add("action", "getPlayer")
                .add("video_id", playerId)
                .build()
                
            val req4 = Request.Builder()
                .url("https://www.embedplay.one/api")
                .header("User-Agent", "Mozilla/5.0")
                .post(formBody)
                .build()
                
            val resp4 = client.newCall(req4).execute()
            val text4 = resp4.body?.string() ?: return@withContext null
            
            val videoUrl = Regex(""""video_url":"([^"]+)"""").find(text4)?.groupValues?.get(1)?.replace("\\/", "/")
            if (videoUrl != null && videoUrl.contains("embedplayabyss.top/player.html?v=")) {
                val slug = videoUrl.substringAfter("?v=").substringBefore("&")
                return@withContext "https://abysscdn.com/?v=$slug"
            }
            return@withContext videoUrl
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

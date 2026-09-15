package com.example.util

import android.net.Uri
import android.webkit.CookieManager
import androidx.media3.common.MimeTypes

/**
 * Universal video link compatibility engine inspired by Web Video Caster (WVC).
 * Provides format detection, MIME type resolution, anti-hotlink bypass headers,
 * cookie injection, and stream type verification for movies, series, and live streams.
 */
object VideoLinkCompatibility {

    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    /**
     * Identifies if a URL is a known ad, dummy video, or preroll clip (e.g. cdn.embadtv.lat/a.mp4)
     */
    fun isAdVideoUrl(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        val clean = rawUrl.trim().lowercase()
        return clean.contains("embadtv.lat") ||
               clean.endsWith("/a.mp4") ||
               clean.contains("/a.mp4?") ||
               clean.contains("/a.mp4#") ||
               clean.endsWith("/blank.mp4") ||
               clean.endsWith("/dummy.mp4")
    }

    /**
     * Identifies if a URL points to a direct playable media stream (HLS, DASH, MP4, MKV, TS, etc.)
     */
    fun isDirectMediaStream(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        val clean = rawUrl.trim().lowercase()

        // Exclude dummy ad videos
        if (isAdVideoUrl(clean)) {
            return false
        }

        // Exclude pure web hosting or known HTML-only paths
        if (clean.contains(".php") && !clean.contains("m3u8") && !clean.contains("mp4")) {
            if (clean.contains("embed") || clean.contains("player") || clean.contains("cxtv.com.br") || clean.contains("futemais.link")) {
                return false
            }
        }

        val uriPath = try {
            Uri.parse(clean).path?.lowercase() ?: clean
        } catch (_: Exception) {
            clean
        }

        return uriPath.contains(".m3u8") ||
                uriPath.contains(".mp4") ||
                uriPath.contains(".ts") ||
                uriPath.contains(".mkv") ||
                uriPath.contains(".webm") ||
                uriPath.contains(".mpd") ||
                uriPath.contains(".m4v") ||
                uriPath.contains(".m4a") ||
                uriPath.contains(".m2ts") ||
                uriPath.contains(".flv") ||
                uriPath.contains(".f4v") ||
                uriPath.contains(".avi") ||
                uriPath.contains(".mov") ||
                uriPath.contains(".3gp") ||
                uriPath.contains(".ogv") ||
                uriPath.contains(".ogg") ||
                uriPath.endsWith("/manifest") ||
                uriPath.contains(".ism") ||
                clean.contains("mime=video") ||
                clean.contains("format=m3u8") ||
                clean.contains("format=mp4") ||
                clean.contains("type=m3u8") ||
                clean.contains("type=mp4") ||
                clean.contains("hls/master") ||
                clean.contains("playlist.m3u8")
    }

    /**
     * Resolves the appropriate ExoPlayer MimeType for any media link format.
     */
    fun resolveMimeType(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        val clean = rawUrl.trim().lowercase()

        val uriPath = try {
            Uri.parse(clean).path?.lowercase() ?: clean
        } catch (_: Exception) {
            clean
        }

        return when {
            uriPath.contains(".m3u8") || clean.contains("format=m3u8") || clean.contains("type=m3u8") || clean.contains("hls") ->
                MimeTypes.APPLICATION_M3U8

            uriPath.contains(".mpd") || clean.contains("format=mpd") || clean.contains("dash") ->
                MimeTypes.APPLICATION_MPD

            uriPath.contains(".ism") || uriPath.endsWith("/manifest") ->
                MimeTypes.APPLICATION_SS

            uriPath.contains(".mp4") || uriPath.contains(".m4v") || clean.contains("video/mp4") ->
                MimeTypes.VIDEO_MP4

            uriPath.contains(".mkv") || clean.contains("video/x-matroska") ->
                MimeTypes.VIDEO_MATROSKA

            uriPath.contains(".webm") || clean.contains("video/webm") ->
                MimeTypes.VIDEO_WEBM

            uriPath.contains(".ts") || uriPath.contains(".m2ts") || clean.contains("video/mp2t") ->
                MimeTypes.VIDEO_MP2T

            uriPath.contains(".flv") || uriPath.contains(".f4v") ->
                MimeTypes.VIDEO_FLV

            uriPath.contains(".avi") ->
                MimeTypes.VIDEO_AVI

            uriPath.contains(".3gp") ->
                MimeTypes.VIDEO_H263

            else -> null
        }
    }

    /**
     * Returns a user-friendly format badge label for UI display.
     */
    fun getFormatLabel(url: String?): String {
        if (url.isNullOrBlank()) return "WEB"
        val clean = url.trim().lowercase()
        return when {
            clean.contains(".m3u8") || clean.contains("hls") -> "HLS • m3u8"
            clean.contains(".mpd") || clean.contains("dash") -> "DASH • mpd"
            clean.contains(".mp4") || clean.contains(".m4v") -> "MP4 HD"
            clean.contains(".mkv") -> "MKV"
            clean.contains(".webm") -> "WebM"
            clean.contains(".ts") -> "MPEG-TS"
            clean.contains(".ism") -> "SmoothStream"
            clean.contains(".flv") -> "FLV"
            else -> "Web Player"
        }
    }

    /**
     * Constructs complete HTTP Request headers including User-Agent, Referer,
     * Origin, Cross-Site flags, and dynamic session cookies (WVC-style).
     */
    fun buildWvcHeaders(
        streamUrl: String,
        embedUrl: String? = null,
        customHeaders: Map<String, String>? = null
    ): Map<String, String> {
        val headers = mutableMapOf<String, String>()

        val userAgent = customHeaders?.get("User-Agent") ?: DEFAULT_USER_AGENT
        headers["User-Agent"] = userAgent
        headers["Accept"] = "*/*"
        headers["Accept-Language"] = "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7"
        headers["Sec-Fetch-Mode"] = "cors"
        headers["Sec-Fetch-Site"] = "cross-site"
        headers["Sec-Fetch-Dest"] = "video"

        // Inject explicit custom headers
        customHeaders?.forEach { (k, v) ->
            if (v.isNotBlank()) {
                headers[k] = v
            }
        }

        // Auto-configure Referer and Origin if not set
        if (!headers.containsKey("Referer")) {
            val referer = when {
                !embedUrl.isNullOrBlank() -> embedUrl
                else -> try {
                    val uri = Uri.parse(streamUrl)
                    val scheme = uri.scheme ?: "https"
                    val host = uri.host
                    if (!host.isNullOrBlank()) "$scheme://$host/" else null
                } catch (_: Exception) {
                    null
                }
            }
            if (!referer.isNullOrBlank()) {
                headers["Referer"] = referer
            }
        }

        if (!headers.containsKey("Origin")) {
            val origin = try {
                val ref = headers["Referer"] ?: embedUrl ?: streamUrl
                val uri = Uri.parse(ref)
                val scheme = uri.scheme ?: "https"
                val host = uri.host
                if (!host.isNullOrBlank()) "$scheme://$host" else null
            } catch (_: Exception) {
                null
            }
            if (!origin.isNullOrBlank()) {
                headers["Origin"] = origin
            }
        }

        // Cookie Manager integration: Retrieve any stored cookies for this stream domain
        try {
            val cookie = CookieManager.getInstance().getCookie(streamUrl)
            if (!cookie.isNullOrBlank() && !headers.containsKey("Cookie")) {
                headers["Cookie"] = cookie
            }
        } catch (_: Exception) {}

        return headers
    }
}

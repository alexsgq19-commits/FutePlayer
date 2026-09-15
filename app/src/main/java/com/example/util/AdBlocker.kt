package com.example.util

object AdBlocker {
    private val AD_HOSTS = setOf(
        "adsystem", "popads", "propeller", "exoclick", "onclickads", "tsyndicate",
        "tracking", "analytics", "doubleclick", "google-analytics", "facebook.com/tr",
        "betano", "1xbet", "adcash", "adsterra", "popcash", "taboola", "outbrain",
        "adthor", "onclick", "popunder", "bet365", "blaze", "pgsoft", "fortune-tiger"
    )

    fun isAd(url: String?): Boolean {
        if (url == null) return false
        val lowerUrl = url.lowercase()
        return AD_HOSTS.any { lowerUrl.contains(it) }
    }

    fun createEmptyResource(): android.webkit.WebResourceResponse {
        return android.webkit.WebResourceResponse(
            "text/plain",
            "UTF-8",
            java.io.ByteArrayInputStream("".toByteArray())
        )
    }
}

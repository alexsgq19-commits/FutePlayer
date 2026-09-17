package com.example.util

object AdBlocker {
    private val AD_HOSTS = setOf(
        "adsystem", "popads", "propeller", "exoclick", "onclickads", "tsyndicate",
        "tracking", "analytics", "doubleclick", "google-analytics", "facebook.com/tr",
        "betano", "1xbet", "adcash", "adsterra", "popcash", "taboola", "outbrain",
        "adthor", "onclick", "popunder", "bet365", "blaze", "pgsoft", "fortune-tiger",
        "bidgear", "terraclicks", "adbull", "adskeeper", "mgid", "zeroredirect",
        "rtb", "trafficjunky", "eroadvertising", "juicyads", "wpush", "pushnotifications",
        "push-ad", "richaudience", "rubiconproject", "pubmatic", "criteo", "appnexus",
        "realsrv.com", "bonga", "chaturbate", "stripchat", "betway", "rivalo", "brazino",
        "f12.bet", "sportingbet", "betfair", "kto", "pixbet", "galera.bet", "estrela", "betsul",
        "aliexpress", "shopee", "shorte.st", "adfly", "adf.ly", "linkvertise", "aylink",
        "pnd.tl", "ouo.io", "shrinkme.io", "zondor", "plarium", "nordvpn", "surfshark",
        "expressvpn", "cyberghost", "adx", "bidswitch", "smartadserver", "monetizer",
        "admaven", "ad-maven", "propellerads", "hilltopads", "evadav", "clickadu",
        "infolinks", "revenuehits", "popmyads", "ad-center", "ad-deliver", "go.affise",
        "voluum", "bemob", "ads-twitter", "ads-linkedin", "ads-tiktok", "ads-snapchat",
        "ad.direct", "ads.direct", "direct-ad", "direct-ads", "directad", "directads"
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

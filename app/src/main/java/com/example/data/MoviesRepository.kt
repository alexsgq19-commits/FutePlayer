package com.example.data

import android.util.Log
import com.example.data.models.MovieDetail
import com.example.data.models.MovieItem
import com.example.data.models.MovieStreamServer
import com.example.data.models.SeriesEpisode
import com.example.data.models.SeriesSeason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class MoviesRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        private const val TAG = "MoviesRepository"
        const val BASE_URL = "https://www.pobreflixtv.irish/"

        // Cache for dynamically resolved IMDb IDs
        private val imdbCache = ConcurrentHashMap<String, String>()

        fun cleanMovieTitle(raw: String): String {
            return raw
                .replace(Regex("""Torrent\s*Dublado\s*e\s*Legendado""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""Torrent\s*Dublado""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""Torrent\s*Dual\s*Áudio""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""Torrent\s*Download""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""Download\s*Torrent""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\(\d{4}\)\s*BluRay\s*.*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""BluRay\s*720p\s*1080p\s*4K.*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""WEB-DL\s*.*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s+-\s+\d+ª\s+Temporada.*""", RegexOption.IGNORE_CASE), "")
                .trim()
        }

        fun normalizeForMatch(str: String): String {
            return cleanMovieTitle(str).lowercase()
                .replace(Regex("""\b(19\d{2}|20\d{2})\b"""), " ")
                .replace(Regex("""\b(dublado|legendado|dual\s*audio|dual\s*áudio|bluray|web-dl|1080p|720p|4k|uhd|filme|serie|hd|torrent|download)\b""", RegexOption.IGNORE_CASE), " ")
                .replace(":", " ")
                .replace("-", " ")
                .replace("–", " ")
                .replace("—", " ")
                .replace(".", " ")
                .replace(",", " ")
                .replace("'", "")
                .replace("\"", "")
                .replace("ç", "c")
                .replace("ã", "a")
                .replace("á", "a")
                .replace("à", "a")
                .replace("â", "a")
                .replace("é", "e")
                .replace("ê", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ô", "o")
                .replace("õ", "o")
                .replace("ú", "u")
                .replace(Regex("""\s+"""), " ")
                .trim()
        }

        private fun extractSequelNumber(str: String): String? {
            val match = Regex("""\b(parte\s*\d+|part\s*\d+|\d+|ii|iii|iv|v|vi)\b""", RegexOption.IGNORE_CASE).find(str)
            return match?.value?.lowercase()?.replace(" ", "")
        }

        fun isTitleMatch(searchTitle: String, candidateTitle: String): Boolean {
            val normSearch = normalizeForMatch(searchTitle)
            val normCandidate = normalizeForMatch(candidateTitle)

            if (normSearch.isBlank() || normCandidate.isBlank()) return false
            if (normSearch == normCandidate) return true

            // Sequel number verification (e.g. 2, 3, 4, ii, iii, parte 2)
            val searchNum = extractSequelNumber(normSearch)
            val candidateNum = extractSequelNumber(normCandidate)
            if (searchNum != candidateNum) {
                return false
            }

            val stopWords = setOf("o", "a", "os", "as", "um", "uma", "de", "do", "da", "dos", "das", "em", "no", "na", "nos", "nas", "e", "the", "an", "of", "and", "in", "on", "at", "to", "for", "with")
            val searchTokens = normSearch.split(" ").filter { it.isNotBlank() && it !in stopWords }
            val candidateTokens = normCandidate.split(" ").filter { it.isNotBlank() && it !in stopWords }

            if (searchTokens.isEmpty() || candidateTokens.isEmpty()) return false

            if (searchTokens.size == 1) {
                val token = searchTokens.first()
                return candidateTokens.contains(token) || normCandidate == token
            }

            val matchedTokens = searchTokens.count { candidateTokens.contains(it) }
            val matchRatio = matchedTokens.toDouble() / searchTokens.size.toDouble()

            return matchRatio >= 0.75 || (matchedTokens >= 2 && matchedTokens == candidateTokens.size)
        }

        fun getImdbIdForTitle(title: String, hintUrlOrId: String? = null): String {
            // 1. Check if hint contains an IMDb ID
            if (!hintUrlOrId.isNullOrBlank()) {
                val match = Regex("""tt\d{6,9}""").find(hintUrlOrId)
                if (match != null) return match.value
            }
            val matchInTitle = Regex("""tt\d{6,9}""").find(title)
            if (matchInTitle != null) return matchInTitle.value

            val clean = normalizeForMatch(title)

            // 2. Check dynamic memory cache
            imdbCache[clean]?.let { return it }

            // 3. Exact & Specific Dictionary (Ordered by specificity to prevent wrong link assignments)
            val dictionary = mapOf(
                // Blockbusters & Movies
                "oppenheimer" to "tt15398776",
                "interestelar" to "tt0816692",
                "interstellar" to "tt0816692",
                "vingadores ultimato" to "tt4154796",
                "avengers endgame" to "tt4154796",
                "vingadores guerra infinita" to "tt4154756",
                "avengers infinity war" to "tt4154756",
                "vingadores the avengers" to "tt0848228",
                "os vingadores" to "tt0848228",
                "homem aranha atraves do aranhaverso" to "tt9362722",
                "homem aranha no aranhaverso" to "tt4633694",
                "spider man across the spider verse" to "tt9362722",
                "spider man into the spider verse" to "tt4633694",
                "homem aranha sem volta para casa" to "tt10872600",
                "homem aranha de volta ao lar" to "tt2250912",
                "homem aranha longe de casa" to "tt6320628",
                "deadpool e wolverine" to "tt6263850",
                "deadpool wolverine" to "tt6263850",
                "deadpool 3" to "tt6263850",
                "deadpool 2" to "tt5463162",
                "deadpool" to "tt1431045",
                "divertida mente 2" to "tt22022452",
                "divertidamente 2" to "tt22022452",
                "inside out 2" to "tt22022452",
                "divertida mente" to "tt2096673",
                "divertidamente" to "tt2096673",
                "inside out" to "tt2096673",
                "duna parte 2" to "tt15239678",
                "duna parte dois" to "tt15239678",
                "duna 2" to "tt15239678",
                "dune part two" to "tt15239678",
                "duna parte 1" to "tt1160419",
                "duna parte um" to "tt1160419",
                "duna 1" to "tt1160419",
                "duna" to "tt1160419",
                "dune" to "tt1160419",
                "gladiador 2" to "tt2066051",
                "gladiador ii" to "tt2066051",
                "gladiator 2" to "tt2066051",
                "gladiador" to "tt0172495",
                "gladiator" to "tt0172495",
                "furiosa uma saga mad max" to "tt12037194",
                "furiosa" to "tt12037194",
                "a substancia" to "tt17526714",
                "substancia" to "tt17526714",
                "the substance" to "tt17526714",
                "terrifier 3" to "tt27911000",
                "terrifier 2" to "tt10403420",
                "terrifier" to "tt4281724",
                "robo selvagem" to "tt29623480",
                "the wild robot" to "tt29623480",
                "alien romulus" to "tt18412256",
                "alien o oitavo passageiro" to "tt0078748",
                "aliens o resgate" to "tt0090605",
                "bad boys ate o fim" to "tt4919268",
                "bad boys 4" to "tt4919268",
                "bad boys para sempre" to "tt1502397",
                "bad boys 3" to "tt1502397",
                "bad boys 2" to "tt0173664",
                "bad boys" to "tt0112442",
                "coringa delirio a dois" to "tt11315808",
                "coringa 2" to "tt11315808",
                "joker folie a deux" to "tt11315808",
                "coringa" to "tt7286456",
                "joker" to "tt7286456",
                "sorria 2" to "tt27552554",
                "smile 2" to "tt27552554",
                "sorria" to "tt15474916",
                "smile" to "tt15474916",
                "venom a ultima rodada" to "tt16366836",
                "venom ultima rodada" to "tt16366836",
                "venom a ultima danca" to "tt16366836",
                "venom 3" to "tt16366836",
                "venom tempo de carnificina" to "tt7097896",
                "venom 2" to "tt7097896",
                "venom" to "tt6751668",
                "a odisseia" to "tt1423403",
                "dia d a batalha decisiva" to "tt8917838",
                "dia d" to "tt8917838",
                "o resgate do soldado ryan" to "tt0120591",
                "soldado ryan" to "tt0120591",
                "saving private ryan" to "tt0120591",
                "o conde de monte cristo" to "tt26443598",
                "conde de monte cristo" to "tt26443598",
                "napoleao" to "tt1528830",
                "napoleon" to "tt1528830",
                "1917" to "tt8579674",
                "dunkirk" to "tt5013056",
                "resgate 2" to "tt12263384",
                "extraction 2" to "tt12263384",
                "resgate" to "tt8936646",
                "extraction" to "tt8936646",
                "moana 2" to "tt31186510",
                "moana" to "tt3521164",
                "kung fu panda 4" to "tt21692408",
                "kung fu panda 3" to "tt2267968",
                "kung fu panda 2" to "tt1302011",
                "kung fu panda" to "tt0441773",
                "super mario bros o filme" to "tt6718170",
                "super mario" to "tt6718170",
                "mario bros" to "tt6718170",
                "barbie" to "tt1517268",
                "john wick 4" to "tt10366206",
                "john wick 3" to "tt6146586",
                "john wick 2" to "tt4425200",
                "john wick" to "tt2911666",
                "top gun maverick" to "tt1745960",
                "top gun" to "tt0092099",
                "avatar o caminho da agua" to "tt1630029",
                "avatar 2" to "tt1630029",
                "avatar" to "tt0499549",
                "planeta dos macacos o reinado" to "tt11384580",
                "o reinado do planeta dos macacos" to "tt11384580",
                "velozes e furiosos 10" to "tt5433140",
                "fast x" to "tt5433140",
                "velozes e furiosos" to "tt0232500",
                "matrix resurrections" to "tt10838180",
                "matrix" to "tt0133093",
                "a origem" to "tt1375666",
                "inception" to "tt1375666",
                "batman" to "tt1877830",
                "the batman" to "tt1877830",
                "the dark knight" to "tt0468569",
                "o cavaleiro das trevas" to "tt0468569",
                "homem de ferro 3" to "tt1300854",
                "homem de ferro 2" to "tt1228705",
                "homem de ferro" to "tt0371746",
                "doutor estranho no multiverso da loucura" to "tt9419884",
                "doutor estranho 2" to "tt9419884",
                "doutor estranho" to "tt1211837",
                "pantera negra wakanda para sempre" to "tt9114286",
                "pantera negra 2" to "tt9114286",
                "pantera negra" to "tt1825683",
                "thor amor e trovao" to "tt10648342",
                "thor ragnarok" to "tt3501632",
                "thor" to "tt0800369",
                "guardioes da galaxia vol 3" to "tt6791350",
                "guardioes da galaxia vol 2" to "tt3896198",
                "guardioes da galaxia" to "tt2015381",
                "capitao america guerra civil" to "tt3498820",
                "capitao america o primeiro vingador" to "tt0458339",
                "capitao america o soldado invernal" to "tt1843866",
                "capitao america" to "tt14513804",
                "aquaman 2 o reino perdido" to "tt9663764",
                "aquaman" to "tt1477834",
                "the flash" to "tt0439572",
                "flash" to "tt0439572",
                "godzilla e kong o novo imperio" to "tt14539740",
                "godzilla vs kong" to "tt5034838",
                "godzilla" to "tt0837563",
                "twisters" to "tt12584954",
                "twister" to "tt0117998",
                "harry potter e a pedra filosofal" to "tt0241527",
                "harry potter" to "tt0241527",
                "o senhor dos aneis a sociedade do anel" to "tt0120737",
                "senhor dos aneis" to "tt0120737",
                "transformers o despertar das feras" to "tt5090568",
                "transformers" to "tt0418279",
                "invocacao do mal 3" to "tt7069210",
                "invocacao do mal 2" to "tt2788710",
                "invocacao do mal" to "tt1457767",
                "a freira 2" to "tt10160976",
                "a freira" to "tt5814060",
                "annabelle 3 de volta para casa" to "tt8350360",
                "annabelle 2 a criacao do mal" to "tt5140878",
                "annabelle" to "tt3322940",
                "panico 6" to "tt17663992",
                "panico 5" to "tt11245972",
                "panico" to "tt0117571",
                "five nights at freddys o pesadelo sem fim" to "tt4589218",
                "five nights at freddys" to "tt4589218",
                "fnaf" to "tt4589218",
                "se beber nao case 3" to "tt1951261",
                "se beber nao case 2" to "tt1411697",
                "se beber nao case" to "tt1119646",
                "gente grande 2" to "tt2191701",
                "gente grande" to "tt1375670",

                // Series
                "stranger things" to "tt4574334",
                "breaking bad" to "tt0903747",
                "the last of us" to "tt3581920",
                "last of us" to "tt3581920",
                "a casa do dragao" to "tt11198330",
                "casa do dragao" to "tt11198330",
                "house of the dragon" to "tt11198330",
                "the boys" to "tt1190634",
                "fallout" to "tt12637874",
                "pinguim" to "tt15474916",
                "the penguin" to "tt15474916",
                "xogum a gloriosa saga do japao" to "tt2788316",
                "xogum" to "tt2788316",
                "shogun" to "tt2788316",
                "vikings valhalla" to "tt11311302",
                "vikings" to "tt5180504",
                "better call saul" to "tt3032476",
                "game of thrones" to "tt0944947",
                "a guerra dos tronos" to "tt0944947",
                "wandinha" to "tt13443470",
                "wednesday" to "tt13443470",
                "one piece a serie" to "tt11737520",
                "one piece" to "tt11737520",
                "peaky blinders" to "tt2442560",
                "round 6" to "tt10919420",
                "squid game" to "tt10919420",
                "la casa de papel" to "tt6468322",
                "casa de papel" to "tt6468322",
                "arcane" to "tt11126994",
                "the witcher" to "tt5180504",
                "yellowstone" to "tt4236770",
                "cobra kai" to "tt7221388",
                "loki" to "tt9140554",
                "rick and morty" to "tt2861424",
                "rick e morty" to "tt2861424",
                "invencivel" to "tt6741278",
                "invincible" to "tt6741278",
                "solo leveling" to "tt21209876",
                "demon slayer kimetsu no yaiba" to "tt9335498",
                "demon slayer" to "tt9335498",
                "kimetsu no yaiba" to "tt9335498",
                "jujutsu kaisen" to "tt12343534",
                "attack on titan" to "tt2560140",
                "shingeki no kyojin" to "tt2560140",
                "naruto shippuden" to "tt0988824",
                "naruto" to "tt0409591",
                "dragon ball super" to "tt4644488",
                "dragon ball z" to "tt0121955",
                "dragon ball" to "tt0121955",
                "sobrenatural" to "tt0460681",
                "supernatural" to "tt0460681",
                "prison break" to "tt0455275",
                "dexter" to "tt0773262",
                "dr house" to "tt0412142",
                "house md" to "tt0412142",
                "friends" to "tt0108778",
                "the office" to "tt0386676"
            )

            // Direct dictionary match
            dictionary[clean]?.let {
                imdbCache[clean] = it
                return it
            }

            // Exact match via isTitleMatch check
            val sortedKeys = dictionary.keys.sortedByDescending { it.length }
            for (key in sortedKeys) {
                if (isTitleMatch(clean, key)) {
                    val foundId = dictionary[key]!!
                    imdbCache[clean] = foundId
                    return foundId
                }
            }

            // Return empty if not definitively found (prevents mapping arbitrary unknown titles to wrong movies)
            return ""
        }

        fun generateStreamServers(title: String, isSeries: Boolean, hintUrlOrId: String? = null, scrapedTapeUrl: String? = null): List<MovieStreamServer> {
            val imdbId = getImdbIdForTitle(title, hintUrlOrId).ifBlank {
                if (!hintUrlOrId.isNullOrBlank() && hintUrlOrId.startsWith("tt")) hintUrlOrId else ""
            }

            if (imdbId.isBlank()) {
                return emptyList()
            }

            // Streamtape link format
            val tapeUrl = scrapedTapeUrl ?: if (isSeries) "https://streamtape.com/e/$imdbId" else "https://tapecontent.net/e/$imdbId"
            val autoEmbedUrl = if (isSeries) "https://autoembed.co/tv/imdb/$imdbId-1-1" else "https://autoembed.co/movie/imdb/$imdbId"
            val superflixUrl = if (isSeries) "https://superflixapi.beer/serie/$imdbId/1/1" else "https://superflixapi.beer/filme/$imdbId"
            val vidSrcUrl = if (isSeries) "https://vidsrc.to/embed/tv/$imdbId/1/1" else "https://vidsrc.to/embed/movie/$imdbId"
            val twoEmbedUrl = if (isSeries) "https://www.2embed.cc/embedseries/$imdbId/1/1" else "https://www.2embed.cc/embed/$imdbId"
            val smashyUrl = if (isSeries) "https://embed.smashystream.com/playere.php?imdb=$imdbId&season=1&episode=1" else "https://embed.smashystream.com/playere.php?imdb=$imdbId"

            val servers = mutableListOf<MovieStreamServer>()
            
            // Priority 1: Stremtape is top priority for series per user requirement
            if (isSeries) {
                servers.add(
                    MovieStreamServer(
                        id = "server_streamtape",
                        name = "Servidor 1 - Streamtape (Preferencial Séries)",
                        quality = "1080p Full HD",
                        audio = "Dublado (PT-BR) / Dual Áudio",
                        streamUrl = tapeUrl,
                        embedUrl = tapeUrl,
                        forceWebPlayer = true
                    )
                )
            } else {
                servers.add(
                    MovieStreamServer(
                        id = "server_tapecontent",
                        name = "Servidor 1 - TapeContent (Preferencial Filme)",
                        quality = "1080p Full HD",
                        audio = "Dublado (PT-BR)",
                        streamUrl = tapeUrl,
                        embedUrl = tapeUrl,
                        forceWebPlayer = true
                    )
                )
            }

            servers.addAll(listOf(
                MovieStreamServer(
                    id = "server_autoembed",
                    name = "Servidor 2 - AutoEmbed (HD / Rápido)",
                    quality = "1080p Full HD",
                    audio = "Dual Áudio / Dublado",
                    streamUrl = autoEmbedUrl,
                    embedUrl = autoEmbedUrl,
                    forceWebPlayer = true
                ),
                MovieStreamServer(
                    id = "server_superflix",
                    name = "Servidor 3 - Superflix Pro (Dublado PT-BR)",
                    quality = "1080p Ultra",
                    audio = "Dublado (PT-BR)",
                    streamUrl = superflixUrl,
                    embedUrl = superflixUrl,
                    forceWebPlayer = true
                ),
                MovieStreamServer(
                    id = "server_vidsrc",
                    name = "Servidor 4 - VidSrc Stream (Full HD 1080p)",
                    quality = "1080p Full HD",
                    audio = "Multi Áudio / Legendas",
                    streamUrl = vidSrcUrl,
                    embedUrl = vidSrcUrl,
                    forceWebPlayer = true
                ),
                MovieStreamServer(
                    id = "server_2embed",
                    name = "Servidor 5 - 2Embed Player (Multi Áudio)",
                    quality = "720p / 1080p HD",
                    audio = "Original com Legendas",
                    streamUrl = twoEmbedUrl,
                    embedUrl = twoEmbedUrl,
                    forceWebPlayer = true
                ),
                MovieStreamServer(
                    id = "server_smashystream",
                    name = "Servidor 6 - SmashyStream (Stream Rápido)",
                    quality = "1080p HD",
                    audio = "Dual Áudio 5.1",
                    streamUrl = smashyUrl,
                    embedUrl = smashyUrl,
                    forceWebPlayer = true
                )
            ))
            return servers
        }
    }

    fun cleanMovieTitle(raw: String): String = Companion.cleanMovieTitle(raw)
    fun getImdbIdForTitle(title: String, hintUrlOrId: String? = null): String = Companion.getImdbIdForTitle(title, hintUrlOrId)
    fun generateStreamServers(title: String, isSeries: Boolean, hintUrlOrId: String? = null, scrapedTapeUrl: String? = null): List<MovieStreamServer> = Companion.generateStreamServers(title, isSeries, hintUrlOrId, scrapedTapeUrl)

    suspend fun testMovieServer(serverUrl: String): Boolean = withContext(Dispatchers.IO) {
        val url = serverUrl.trim()
        if (url.isBlank() ||
            (!url.startsWith("http://") && !url.startsWith("https://")) ||
            com.example.data.models.isOfflineFallbackUrl(url)) {
            return@withContext false
        }
        try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "*/*")

            var response: okhttp3.Response? = null
            try {
                response = client.newCall(reqBuilder.head().build()).execute()
            } catch (_: Exception) {}

            if (response == null || response.code == 405 || response.code == 403) {
                response?.close()
                val getReq = reqBuilder.get().header("Range", "bytes=0-2048").build()
                response = client.newCall(getReq).execute()
            }

            response.use { resp ->
                val code = resp.code
                if (code in 200..399) {
                    val bodySnippet = try { resp.peekBody(1024).string().lowercase() } catch (_: Exception) { "" }
                    if (bodySnippet.contains("404 not found") ||
                        bodySnippet.contains("video deleted") ||
                        bodySnippet.contains("file not found") ||
                        bodySnippet.contains("conteúdo indisponível") ||
                        bodySnippet.contains("video was deleted") ||
                        bodySnippet.contains("erro 404")) {
                        return@withContext false
                    }
                    return@withContext true
                }
                return@withContext false
            }
        } catch (_: Exception) {
            return@withContext false
        }
    }

    /**
     * Searches movie sites using their respective search fields/endpoints and extracts streams for matching title
     */
    suspend fun searchMovieOnWebsites(title: String, isSeries: Boolean): List<MovieStreamServer> = withContext(Dispatchers.IO) {
        val servers = mutableListOf<MovieStreamServer>()
        val clean = cleanMovieTitle(title).trim()
        if (clean.isBlank()) return@withContext emptyList()

        // Prioritize dubbed query first
        val searchQueries = listOf("$clean dublado", clean)

        for (query in searchQueries) {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")

            // 1. Search PobreFlix TV
            try {
                val searchUrl = "$BASE_URL?s=$encodedQuery"
                val request = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    val doc = Jsoup.parse(html)
                    val items = doc.select("article, .post, .item, .capa-box, div.post-item, .film-item, div[class*='item-filme']")
                    
                    // Sort items giving priority to those with 'dublado' or 'dual audio'
                    val sortedItems = items.sortedByDescending { el ->
                        val t = el.text().lowercase()
                        if (t.contains("dublado") || t.contains("dual")) 2 else 1
                    }

                    for (item in sortedItems) {
                        val linkEl = item.selectFirst("a[href]") ?: continue
                        val itemUrl = linkEl.attr("href")
                        val titleEl = item.selectFirst("h2, h3, .title, .nome, .entry-title")
                        val itemTitle = titleEl?.text()?.trim() ?: linkEl.attr("title").trim()
                        
                        if (isTitleMatch(clean, itemTitle)) {
                            // Extract detail page
                            val detailReq = Request.Builder().url(itemUrl).header("User-Agent", "Mozilla/5.0").build()
                            val detailResp = client.newCall(detailReq).execute()
                            if (detailResp.isSuccessful) {
                                val detailHtml = detailResp.body?.string() ?: ""
                                val detailDoc = Jsoup.parse(detailHtml)
                                
                                // Prioritize dubbed iframes / options if present
                                val dubbedIframes = detailDoc.select(".dublado iframe, .audio-dublado iframe, [data-audio*='dublado'] iframe, a[href*='streamtape'], a[href*='tapecontent']")
                                val iframes = if (dubbedIframes.isNotEmpty()) dubbedIframes else detailDoc.select("iframe[src], source[src], a[href*='streamtape'], a[href*='tapecontent']")
                                
                                for (iframe in iframes) {
                                    val src = iframe.attr("src").ifBlank { iframe.attr("href") }
                                    if (src.startsWith("http")) {
                                        servers.add(
                                            MovieStreamServer(
                                                id = "server_scraped_${servers.size + 1}",
                                                name = "Servidor Dublado Principal (${servers.size + 1})",
                                                quality = "1080p Full HD",
                                                audio = "Dublado (PT-BR)",
                                                streamUrl = src,
                                                embedUrl = src,
                                                forceWebPlayer = true
                                            )
                                        )
                                    }
                                }
                            }
                            if (servers.isNotEmpty()) break
                        }
                    }
                }
            } catch (_: Exception) {}

            // 2. Search MixFilmes
            try {
                val mixSearchUrl = "https://mixfilmes.net/?s=$encodedQuery"
                val mixReq = Request.Builder()
                    .url(mixSearchUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Referer", "https://mixfilmes.net/")
                    .build()
                val mixResp = client.newCall(mixReq).execute()
                if (mixResp.isSuccessful) {
                    val html = mixResp.body?.string() ?: ""
                    val doc = Jsoup.parse(html)
                    val elements = doc.select("article, .post, .item, .capa-box, div.post-item, .film-item")
                    
                    val sortedElements = elements.sortedByDescending { el ->
                        val t = el.text().lowercase()
                        if (t.contains("dublado") || t.contains("dual")) 2 else 1
                    }

                    for (el in sortedElements) {
                        val linkEl = el.selectFirst("a[href]") ?: continue
                        val itemUrl = linkEl.attr("href")
                        val titleEl = el.selectFirst("h2, h3, .title, .nome, .entry-title")
                        val itemTitle = titleEl?.text()?.trim() ?: linkEl.attr("title").trim()

                        if (isTitleMatch(clean, itemTitle)) {
                            val detailReq = Request.Builder().url(itemUrl).header("User-Agent", "Mozilla/5.0").build()
                            val detailResp = client.newCall(detailReq).execute()
                            if (detailResp.isSuccessful) {
                                val detailHtml = detailResp.body?.string() ?: ""
                                val detailDoc = Jsoup.parse(detailHtml)
                                val iframes = detailDoc.select("iframe[src], a[href*='streamtape'], a[href*='tapecontent']")
                                for (iframe in iframes) {
                                    val src = iframe.attr("src").ifBlank { iframe.attr("href") }
                                    if (src.startsWith("http")) {
                                        servers.add(
                                            MovieStreamServer(
                                                id = "server_mix_${servers.size + 1}",
                                                name = "Servidor MixFilmes Dublado (${servers.size + 1})",
                                                quality = "1080p HD",
                                                audio = "Dublado (PT-BR)",
                                                streamUrl = src,
                                                embedUrl = src,
                                                forceWebPlayer = true
                                            )
                                        )
                                    }
                                }
                            }
                            if (servers.isNotEmpty()) break
                        }
                    }
                }
            } catch (_: Exception) {}

            if (servers.isNotEmpty()) {
                break
            }
        }

        return@withContext servers
    }

    suspend fun findWorkingMovieServer(title: String, isSeries: Boolean, currentUrl: String? = null): MovieStreamServer? = withContext(Dispatchers.IO) {
        val clean = cleanMovieTitle(title).trim()
        if (clean.isBlank()) return@withContext null

        // 1. First, search specifically on movie websites
        val scrapedServers = searchMovieOnWebsites(clean, isSeries)
        for (server in scrapedServers) {
            val works = testMovieServer(server.streamUrl)
            if (works) {
                return@withContext server
            }
        }

        // 2. Second, strictly resolve the exact IMDb ID for the title
        val dynamicImdbId = resolveImdbIdOnline(clean, isSeries)
        if (dynamicImdbId.isBlank()) {
            return@withContext null
        }

        // 3. Generate standard servers strictly for this verified IMDb ID
        val servers = generateStreamServers(clean, isSeries, dynamicImdbId)
        for (server in servers) {
            val works = testMovieServer(server.streamUrl)
            if (works) {
                return@withContext server
            }
        }
        return@withContext null
    }

    /**
     * Resolves the exact IMDb ID for any title dynamically via Cinemeta / IMDb public lookup with STRICT title matching
     */
    suspend fun resolveImdbIdOnline(title: String, isSeries: Boolean = false): String = withContext(Dispatchers.IO) {
        val clean = cleanMovieTitle(title).trim()
        if (clean.isBlank()) return@withContext ""
        val cacheKey = normalizeForMatch(clean)

        imdbCache[cacheKey]?.let { return@withContext it }

        // 1. Try Stremio Cinemeta catalog search API with STRICT title validation
        try {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val type = if (isSeries) "series" else "movie"
            val url = "https://v3-cinemeta.strem.io/catalog/$type/top/search=$encoded.json"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val metas = json.optJSONArray("metas")
                if (metas != null && metas.length() > 0) {
                    for (i in 0 until metas.length()) {
                        val meta = metas.getJSONObject(i)
                        val metaName = meta.optString("name")
                        val id = meta.optString("id")
                        // STRICT check: candidate name MUST match the searched title
                        if (isTitleMatch(clean, metaName) && id.startsWith("tt")) {
                            imdbCache[cacheKey] = id
                            return@withContext id
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Try IMDb suggestion search with STRICT title validation
        try {
            val queryFirstChar = clean.firstOrNull()?.lowercaseChar() ?: 'a'
            val encodedQuery = URLEncoder.encode(clean.replace(" ", "_"), "UTF-8")
            val url = "https://v3.sg.media-imdb.com/suggestion/$queryFirstChar/$encodedQuery.json"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val d = json.optJSONArray("d")
                if (d != null && d.length() > 0) {
                    for (i in 0 until d.length()) {
                        val item = d.getJSONObject(i)
                        val label = item.optString("l")
                        val id = item.optString("id")
                        // STRICT check: suggestion label MUST match the searched title
                        if (isTitleMatch(clean, label) && id.startsWith("tt")) {
                            imdbCache[cacheKey] = id
                            return@withContext id
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Fall back to exact dictionary matching
        val fallback = getImdbIdForTitle(title)
        if (fallback.isNotBlank()) {
            imdbCache[cacheKey] = fallback
            return@withContext fallback
        }

        // Do NOT assign any default ID if not found
        return@withContext ""
    }

    suspend fun getMoviesCatalog(
        categoryName: String? = null,
        categoryUrl: String? = null,
        page: Int = 1,
        query: String? = null
    ): List<MovieItem> = withContext(Dispatchers.IO) {
        val targetUrl = when {
            !query.isNullOrBlank() -> {
                val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
                if (page > 1) {
                    "$BASE_URL/page/$page/?s=$encodedQuery"
                } else {
                    "$BASE_URL?s=$encodedQuery"
                }
            }
            !categoryUrl.isNullOrBlank() -> {
                val cleanUrl = categoryUrl.trimEnd('/')
                if (page > 1) {
                    "$cleanUrl/page/$page/"
                } else {
                    "$cleanUrl/"
                }
            }
            page > 1 -> "$BASE_URL/page/$page/"
            else -> BASE_URL
        }

        val allMovies = mutableListOf<MovieItem>()

        try {
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                val movies = parseMoviesFromHtml(html)
                allMovies.addAll(movies)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to scrape $targetUrl: ${e.message}")
        }

        // Also scrape MixFilmes (https://mixfilmes.net/inicio/)
        try {
            val mixUrl = if (!query.isNullOrBlank()) {
                val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
                "https://mixfilmes.net/?s=$encodedQuery"
            } else {
                "https://mixfilmes.net/inicio/"
            }
            val mixRequest = Request.Builder()
                .url(mixUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Referer", "https://mixfilmes.net/")
                .build()

            val mixResponse = client.newCall(mixRequest).execute()
            if (mixResponse.isSuccessful) {
                val mixHtml = mixResponse.body?.string() ?: ""
                val mixMovies = parseMoviesFromHtml(mixHtml)
                allMovies.addAll(mixMovies)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to scrape MixFilmes: ${e.message}")
        }

        val distinctMovies = allMovies.distinctBy { it.pageUrl.lowercase() }
        if (distinctMovies.isNotEmpty()) {
            return@withContext filterMoviesByCategoryOrGenre(distinctMovies, categoryName, categoryUrl)
        }

        // Return curated and rich catalog filtered properly
        return@withContext getCuratedCatalog(query, categoryName, categoryUrl)
    }

    private fun filterMoviesByCategoryOrGenre(
        items: List<MovieItem>,
        categoryName: String?,
        categoryUrl: String?
    ): List<MovieItem> {
        if (categoryName.isNullOrBlank() || categoryName.equals("Todos", ignoreCase = true)) {
            return items
        }

        val cat = categoryName.lowercase()
        return items.filter { item ->
            val title = item.title.lowercase()
            val itemCat = item.category.lowercase()
            val synopsis = item.synopsis?.lowercase() ?: ""

            when {
                cat.contains("série") || cat.contains("serie") -> itemCat.contains("série") || title.contains("temporada")
                cat.contains("filme") -> !itemCat.contains("série") && !title.contains("temporada")
                cat.contains("lançamento") || cat.contains("lancamento") -> true
                cat.contains("ação") || cat.contains("acao") -> itemCat.contains("ação") || synopsis.contains("ação") || title.contains("guerra") || title.contains("combate")
                cat.contains("comédia") || cat.contains("comedia") -> itemCat.contains("comédia") || synopsis.contains("comédia") || synopsis.contains("divertido")
                cat.contains("terror") -> itemCat.contains("terror") || synopsis.contains("terror") || synopsis.contains("medo") || synopsis.contains("assombrado")
                cat.contains("animação") || cat.contains("animacao") || cat.contains("anime") -> itemCat.contains("animação") || synopsis.contains("animação") || synopsis.contains("desenho")
                cat.contains("ficção") || cat.contains("ficcao") -> itemCat.contains("ficção") || synopsis.contains("ficção") || synopsis.contains("futuro") || synopsis.contains("espaço")
                cat.contains("suspense") -> itemCat.contains("suspense") || synopsis.contains("suspense") || synopsis.contains("mistério")
                cat.contains("aventura") -> itemCat.contains("aventura") || synopsis.contains("aventura") || synopsis.contains("jornada")
                cat.contains("drama") -> itemCat.contains("drama") || synopsis.contains("drama")
                else -> true
            }
        }.ifEmpty { items }
    }

    private fun parseMoviesFromHtml(html: String): List<MovieItem> {
        val items = mutableListOf<MovieItem>()
        try {
            val doc = Jsoup.parse(html)
            val elements = doc.select("article, .post, .item, .capa-box, div.post-item, .film-item, div[class*='item-filme'], div[class*='post-']")

            for (el in elements) {
                val linkEl = el.selectFirst("a[href*='filmestorrentdublado.com'], h2 a, h3 a, a.capa, .title a") ?: el.selectFirst("a[href]")
                val pageUrl = linkEl?.attr("href") ?: continue
                if (pageUrl.contains("/categoria/") || pageUrl.contains("/tag/") || pageUrl.contains("/page/")) continue

                val titleEl = el.selectFirst("h2, h3, .title, .nome, .entry-title")
                var title = titleEl?.text()?.trim() ?: linkEl.attr("title").trim()
                if (title.isBlank()) {
                    val img = el.selectFirst("img")
                    title = img?.attr("alt")?.trim() ?: img?.attr("title")?.trim() ?: ""
                }
                if (title.isBlank()) continue

                val imgEl = el.selectFirst("img")
                var posterUrl = imgEl?.attr("data-src")?.takeIf { it.isNotBlank() }
                    ?: imgEl?.attr("data-lazy-src")?.takeIf { it.isNotBlank() }
                    ?: imgEl?.attr("src")?.takeIf { it.isNotBlank() }
                    ?: ""

                if (posterUrl.startsWith("//")) {
                    posterUrl = "https:$posterUrl"
                }

                val textContent = el.text()
                val yearMatch = Regex("""\b(19\d{2}|20\d{2})\b""").find(textContent)
                val year = yearMatch?.value ?: "2024"

                val quality = when {
                    textContent.contains("4K", ignoreCase = true) || textContent.contains("2160p", ignoreCase = true) -> "4K UHD"
                    textContent.contains("1080p", ignoreCase = true) -> "1080p Full HD"
                    textContent.contains("720p", ignoreCase = true) -> "720p HD"
                    else -> "1080p Full HD"
                }

                val audio = when {
                    textContent.contains("Dual Áudio", ignoreCase = true) || textContent.contains("Dual Audio", ignoreCase = true) -> "Dual Áudio (5.1)"
                    textContent.contains("Dublado", ignoreCase = true) -> "Dublado (PT-BR)"
                    textContent.contains("Legendado", ignoreCase = true) -> "Legendado"
                    else -> "Dublado (PT-BR)"
                }

                val imdbMatch = Regex("""IMDb:?\s*([0-9.,]+)""", RegexOption.IGNORE_CASE).find(textContent)
                val imdb = imdbMatch?.groupValues?.getOrNull(1) ?: "7.8"

                val isSeries = pageUrl.contains("serie", ignoreCase = true) || 
                               textContent.contains("temporada", ignoreCase = true) ||
                               title.contains("temporada", ignoreCase = true)

                val cleanedTitle = cleanMovieTitle(title)
                val id = pageUrl.hashCode().toString()

                val directStreams = generateStreamServers(cleanedTitle, isSeries, pageUrl)

                items.add(
                    MovieItem(
                        id = id,
                        title = cleanedTitle,
                        pageUrl = pageUrl,
                        posterUrl = posterUrl.ifBlank { "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500" },
                        year = year,
                        quality = quality,
                        audio = audio,
                        imdb = imdb,
                        category = if (isSeries) "Série" else "Filme",
                        synopsis = "Acompanhe este grande sucesso em alta definição. Reproduza com áudio dublado ou original com máxima qualidade de som e imagem.",
                        streamUrl = directStreams.firstOrNull()?.streamUrl,
                        embedUrl = directStreams.firstOrNull()?.embedUrl
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing HTML", e)
        }
        return items.distinctBy { it.pageUrl }
    }

    suspend fun getMovieDetail(movieItem: MovieItem): MovieDetail = withContext(Dispatchers.IO) {
        val isSeries = movieItem.category == "Série" || movieItem.title.contains("temporada", ignoreCase = true)

        // Dynamically resolve IMDb ID for the title
        val dynamicImdbId = resolveImdbIdOnline(movieItem.title, isSeries)
        
        try {
            val request = Request.Builder()
                .url(movieItem.pageUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                val doc = Jsoup.parse(html)
                
                val synopsisEl = doc.selectFirst(".sinopse, .entry-content p, #sinopse, .description, div[class*='sinopse']")
                val extractedSynopsis = synopsisEl?.text()?.trim()
                    ?: doc.select(".entry-content p").joinToString("\n\n") { it.text().trim() }
                        .ifBlank { movieItem.synopsis ?: "Assista agora em alta definição com áudio dublado em português." }

                val genres = mutableListOf<String>()
                val genreElements = doc.select("a[href*='/categoria/'], .genres a, .genero a")
                for (g in genreElements) {
                    val txt = g.text().trim()
                    if (txt.isNotBlank() && txt !in genres && txt.length < 25) {
                        genres.add(txt)
                    }
                }

                                var scrapedTapeUrl: String? = null
                val iframeElements = doc.select("iframe[src], source[src], a[href]")
                for (el in iframeElements) {
                    val src = el.attr("src").ifBlank { el.attr("href") }
                    if (src.contains("tapecontent", ignoreCase = true) || src.contains("streamtape", ignoreCase = true)) {
                        scrapedTapeUrl = src
                        break
                    }
                }

                val streamServers = generateStreamServers(movieItem.title, isSeries, dynamicImdbId, scrapedTapeUrl)
                val seasons = if (isSeries) generateSeriesSeasons(movieItem.title, dynamicImdbId) else emptyList()

                return@withContext MovieDetail(
                    id = movieItem.id,
                    title = movieItem.title,
                    pageUrl = movieItem.pageUrl,
                    posterUrl = movieItem.posterUrl,
                    synopsis = extractedSynopsis,
                    year = movieItem.year ?: "2024",
                    duration = if (isSeries) "8 Episódios" else "1h 58min",
                    genres = if (genres.isNotEmpty()) genres else listOf("Ação", "Lançamento", "Dublado"),
                    imdbScore = movieItem.imdb ?: "8.1",
                    audioType = movieItem.audio ?: "Dublado (PT-BR) / Dual Áudio",
                    quality = movieItem.quality ?: "1080p Full HD",
                    isSeries = isSeries,
                    streamServers = streamServers,
                    seasons = seasons
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to scrape detail for ${movieItem.title}: ${e.message}")
        }

        return@withContext generateFallbackDetail(movieItem)
    }

    private fun generateSeriesSeasons(title: String, hintUrlOrId: String? = null): List<SeriesSeason> {
        val imdbId = getImdbIdForTitle(title, hintUrlOrId).ifBlank {
            if (!hintUrlOrId.isNullOrBlank() && hintUrlOrId.startsWith("tt")) hintUrlOrId else ""
        }

        if (imdbId.isBlank()) return emptyList()

        return listOf(
            SeriesSeason(
                seasonNumber = 1,
                name = "1ª Temporada (Dublado & Dual HD - Streamtape)",
                episodes = (1..8).map { epNum ->
                    val epStreamTape = "https://streamtape.com/e/$imdbId-s1-e$epNum"
                    SeriesEpisode(
                        id = "s1_ep$epNum",
                        number = epNum,
                        title = "Episódio $epNum: O Início da Jornada",
                        duration = "${45 + (epNum * 2)}min",
                        streamUrl = epStreamTape,
                        embedUrl = epStreamTape,
                        isWebPlayer = true,
                        synopsis = "Acompanhe os acontecimentos eletrizantes do episódio $epNum desta temporada em alta definição."
                    )
                }
            )
        )
    }

    private fun generateFallbackDetail(item: MovieItem): MovieDetail {
        val isSeries = item.category == "Série"
        val hint = item.embedUrl ?: item.streamUrl ?: item.pageUrl
        return MovieDetail(
            id = item.id,
            title = item.title,
            pageUrl = item.pageUrl,
            posterUrl = item.posterUrl,
            synopsis = item.synopsis ?: "Assista agora a este título em alta resolução com áudio dublado em português e som surround imersivo.",
            year = item.year ?: "2024",
            duration = if (isSeries) "8 Episódios" else item.duration,
            genres = listOf("Ação", "Aventura", "Dublado", "Lançamento"),
            imdbScore = item.imdb ?: "8.2",
            audioType = item.audio ?: "Dublado (PT-BR) / Dual Áudio",
            quality = item.quality ?: "1080p Full HD",
            isSeries = isSeries,
            streamServers = generateStreamServers(item.title, isSeries, hint),
            seasons = if (isSeries) generateSeriesSeasons(item.title, hint) else emptyList()
        )
    }

    private fun getCuratedCatalog(query: String?, categoryName: String?, categoryUrl: String?): List<MovieItem> {
        val allItems = listOf(
            // Lançamentos & Históricos & Blockbusters
            MovieItem(
                id = "mov_odisseia",
                title = "A Odisséia",
                pageUrl = "https://autoembed.co/movie/imdb/tt1423403",
                posterUrl = "https://image.tmdb.org/t/p/w500/kSgR4qV5Q4eDq4l9xZp1aBf5gK0.jpg",
                year = "2024",
                quality = "4K UHD / 1080p",
                audio = "Dublado 5.1 / Dual Áudio",
                imdb = "7.8",
                category = "Aventura / Drama",
                duration = "2h 02min",
                synopsis = "A magnífica saga épica adaptada da clássica história de Homero, narrando os perigos, monstros e deuses enfrentados na longa jornada de volta para casa.",
                streamUrl = "https://autoembed.co/movie/imdb/tt1423403",
                embedUrl = "https://autoembed.co/movie/imdb/tt1423403"
            ),
            MovieItem(
                id = "mov_dia_d",
                title = "Dia D: A Batalha Decisiva",
                pageUrl = "https://autoembed.co/movie/imdb/tt8917838",
                posterUrl = "https://image.tmdb.org/t/p/w500/b1kCkg6zC5m9xKkH6w5G2q3P4aM.jpg",
                year = "2024",
                quality = "1080p Full HD",
                audio = "Dublado 5.1",
                imdb = "7.5",
                category = "Guerra / Ação",
                duration = "1h 55min",
                synopsis = "A história épica e realista da invasão da Normandia em 6 de junho de 1944, onde soldados corajosos mudaram para sempre o rumo da Segunda Guerra Mundial.",
                streamUrl = "https://autoembed.co/movie/imdb/tt8917838",
                embedUrl = "https://autoembed.co/movie/imdb/tt8917838"
            ),
            MovieItem(
                id = "mov_deadpool_wolverine",
                title = "Deadpool & Wolverine",
                pageUrl = "https://autoembed.co/movie/imdb/tt6263850",
                posterUrl = "https://image.tmdb.org/t/p/w500/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
                year = "2024",
                quality = "4K UHD / 1080p",
                audio = "Dublado 5.1",
                imdb = "7.9",
                category = "Ação / Comédia",
                duration = "2h 08min",
                synopsis = "Wolverine está se recuperando de seus ferimentos quando cruza o caminho do tagarela Deadpool. Eles se unem para derrotar um inimigo em comum que ameaça a estabilidade do multiverso.",
                streamUrl = "https://autoembed.co/movie/imdb/tt6263850",
                embedUrl = "https://autoembed.co/movie/imdb/tt6263850"
            ),
            MovieItem(
                id = "mov_divertida_mente_2",
                title = "Divertida Mente 2",
                pageUrl = "https://autoembed.co/movie/imdb/tt22022452",
                posterUrl = "https://image.tmdb.org/t/p/w500/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg",
                year = "2024",
                quality = "1080p Full HD",
                audio = "Dublado 5.1",
                imdb = "7.7",
                category = "Animação / Comédia",
                duration = "1h 36min",
                synopsis = "Com um salto temporal, Riley se encontra mais velha, passando pela tão temida puberdade. Junto com o amadurecimento, a sala de controle ganha novas emoções: Ansiedade, Inveja, Tédio e Vergonha.",
                streamUrl = "https://autoembed.co/movie/imdb/tt22022452",
                embedUrl = "https://autoembed.co/movie/imdb/tt22022452"
            ),
            MovieItem(
                id = "mov_duna_parte_2",
                title = "Duna: Parte 2",
                pageUrl = "https://autoembed.co/movie/imdb/tt15239678",
                posterUrl = "https://image.tmdb.org/t/p/w500/czembW0Rk1Ke7AYVicXuZaRygDc.jpg",
                year = "2024",
                quality = "4K UHD / 1080p",
                audio = "Dual Áudio 5.1",
                imdb = "8.6",
                category = "Ficção Científica / Ação",
                duration = "2h 46min",
                synopsis = "Paul Atreides se une a Chani e aos Fremen enquanto busca vingança contra os conspiradores que destruíram sua família.",
                streamUrl = "https://autoembed.co/movie/imdb/tt15239678",
                embedUrl = "https://autoembed.co/movie/imdb/tt15239678"
            ),
            MovieItem(
                id = "mov_gladiador_2",
                title = "Gladiador II",
                pageUrl = "https://autoembed.co/movie/imdb/tt2066051",
                posterUrl = "https://image.tmdb.org/t/p/w500/2cxhvwyEwRlysAmRH4iodkvo0z5.jpg",
                year = "2024",
                quality = "4K UHD",
                audio = "Dublado 5.1",
                imdb = "7.8",
                category = "Ação / Drama",
                duration = "2h 28min",
                synopsis = "Anos após testemunhar a morte de Maximus pelas mãos de seu tio, Lucius deve entrar no Coliseu depois que sua casa é conquistada pelos imperadores tirânicos que agora lideram Roma com mão de ferro.",
                streamUrl = "https://autoembed.co/movie/imdb/tt2066051",
                embedUrl = "https://autoembed.co/movie/imdb/tt2066051"
            ),
            MovieItem(
                id = "mov_furiosa",
                title = "Furiosa: Uma Saga Mad Max",
                pageUrl = "https://autoembed.co/movie/imdb/tt12037194",
                posterUrl = "https://image.tmdb.org/t/p/w500/iADOJ8Zymht2JPMoy3R7xUMZ51Q.jpg",
                year = "2024",
                quality = "1080p Full HD",
                audio = "Dublado 5.1",
                imdb = "7.6",
                category = "Ação / Ficção Científica",
                duration = "2h 28min",
                synopsis = "A jovem Furiosa cai nas mãos de uma grande horda de motoqueiros liderada pelo Senhor da Guerra Dementus. Varrendo o deserto, eles encontram a Cidadela.",
                streamUrl = "https://autoembed.co/movie/imdb/tt12037194",
                embedUrl = "https://autoembed.co/movie/imdb/tt12037194"
            ),
            MovieItem(
                id = "mov_substancia",
                title = "A Substância",
                pageUrl = "https://autoembed.co/movie/imdb/tt17526714",
                posterUrl = "https://image.tmdb.org/t/p/w500/lqoMzCcZYEFK729d6qGuNkAKag2.jpg",
                year = "2024",
                quality = "4K UHD / 1080p",
                audio = "Dual Áudio 5.1",
                imdb = "7.6",
                category = "Terror / Suspense",
                duration = "2h 21min",
                synopsis = "Uma celebridade em declínio decide usar uma droga do mercado negro que replica células temporariamente, criando uma versão mais jovem e perfeita de si mesma.",
                streamUrl = "https://autoembed.co/movie/imdb/tt17526714",
                embedUrl = "https://autoembed.co/movie/imdb/tt17526714"
            ),
            MovieItem(
                id = "mov_terrifier_3",
                title = "Terrifier 3",
                pageUrl = "https://autoembed.co/movie/imdb/tt27911000",
                posterUrl = "https://image.tmdb.org/t/p/w500/l1175hgL5doXnqeAhPP899HO083.jpg",
                year = "2024",
                quality = "1080p Full HD",
                audio = "Dublado 5.1",
                imdb = "7.0",
                category = "Terror",
                duration = "2h 05min",
                synopsis = "Art the Clown está pronto para liberar o caos sobre os moradores desavisados do Condado de Miles enquanto eles adormecem pacificamente na véspera de Natal.",
                streamUrl = "https://autoembed.co/movie/imdb/tt27911000",
                embedUrl = "https://autoembed.co/movie/imdb/tt27911000"
            ),
            MovieItem(
                id = "mov_robo_selvagem",
                title = "Robô Selvagem",
                pageUrl = "https://autoembed.co/movie/imdb/tt29623480",
                posterUrl = "https://image.tmdb.org/t/p/w500/8wW2q6nQ96lW2pWq8v9dG9tJ9fP.jpg",
                year = "2024",
                quality = "1080p Full HD",
                audio = "Dublado 5.1",
                imdb = "8.4",
                category = "Animação / Aventura",
                duration = "1h 42min",
                synopsis = "Após um naufrágio, um robô inteligente chamado Roz fica preso em uma ilha desabitada. Para sobreviver ao ambiente hostil, Roz cria laços com os animais da ilha e adota um gansinho órfão.",
                streamUrl = "https://autoembed.co/movie/imdb/tt29623480",
                embedUrl = "https://autoembed.co/movie/imdb/tt29623480"
            ),
            MovieItem(
                id = "mov_alien_romulus",
                title = "Alien: Romulus",
                pageUrl = "https://autoembed.co/movie/imdb/tt18412256",
                posterUrl = "https://image.tmdb.org/t/p/w500/b33nnKl1GSFbao8l3xQo09GvU05.jpg",
                year = "2024",
                quality = "4K UHD / 1080p",
                audio = "Dual Áudio 5.1",
                imdb = "7.3",
                category = "Terror / Ficção Científica",
                duration = "1h 59min",
                synopsis = "Enquanto vasculham as profundezas de uma estação espacial abandonada, um grupo de jovens colonizadores espaciais fica cara a cara com a forma de vida mais aterrorizante do universo.",
                streamUrl = "https://autoembed.co/movie/imdb/tt18412256",
                embedUrl = "https://autoembed.co/movie/imdb/tt18412256"
            ),
            MovieItem(
                id = "mov_bad_boys_4",
                title = "Bad Boys: Até o Fim",
                pageUrl = "https://autoembed.co/movie/imdb/tt4919268",
                posterUrl = "https://image.tmdb.org/t/p/w500/nP6RliHjxsz4irTKsxe8FRhKZYl.jpg",
                year = "2024",
                quality = "1080p Full HD",
                audio = "Dual Áudio 5.1",
                imdb = "6.7",
                category = "Ação / Comédia",
                duration = "1h 55min",
                synopsis = "Os policiais mais famosos do mundo estão de volta com sua mistura icônica de ação de tirar o fôlego e comédia escandalosa.",
                streamUrl = "https://autoembed.co/movie/imdb/tt4919268",
                embedUrl = "https://autoembed.co/movie/imdb/tt4919268"
            ),
            // SÉRIES DE SUCESSO
            MovieItem(
                id = "mov_casa_dragao",
                title = "A Casa do Dragão",
                pageUrl = "https://streamtape.com/e/tt11198330-1-1",
                posterUrl = "https://image.tmdb.org/t/p/w500/1X4h40fcB4WWUmIBK0auT4zRBAV.jpg",
                year = "2024",
                quality = "1080p Full HD",
                audio = "Dual Áudio 5.1",
                imdb = "8.5",
                category = "Série / Drama / Ação",
                duration = "2 Temporadas",
                synopsis = "A guerra civil entre os apoiadores de Rhaenyra e Aegon II incendeia Westeros na batalha pelo Trono de Ferro.",
                streamUrl = "https://streamtape.com/e/tt11198330-1-1",
                embedUrl = "https://streamtape.com/e/tt11198330-1-1"
            ),
            MovieItem(
                id = "mov_the_boys",
                title = "The Boys",
                pageUrl = "https://streamtape.com/e/tt1190634-1-1",
                posterUrl = "https://image.tmdb.org/t/p/w500/2zmTngn1tYC1AvfnNDBpQI7VTe7.jpg",
                year = "2024",
                quality = "1080p Full HD",
                audio = "Dual Áudio 5.1",
                imdb = "8.7",
                category = "Série / Ação / Ficção",
                duration = "4 Temporadas",
                synopsis = "O mundo está à beira do abismo enquanto Capitão Pátria consolida seu poder sobre a Vought e os Estados Unidos.",
                streamUrl = "https://streamtape.com/e/tt1190634-1-1",
                embedUrl = "https://streamtape.com/e/tt1190634-1-1"
            ),
            MovieItem(
                id = "mov_fallout",
                title = "Fallout",
                pageUrl = "https://streamtape.com/e/tt12637874-1-1",
                posterUrl = "https://image.tmdb.org/t/p/w500/AnsZu440fE2m6aP9d5E1X3D2R9O.jpg",
                year = "2024",
                quality = "4K UHD / 1080p",
                audio = "Dual Áudio",
                imdb = "8.4",
                category = "Série / Ficção Científica",
                duration = "1 Temporada",
                synopsis = "Em um futuro pós-apocalíptico de Los Angeles, os cidadãos precisam viver em bunkers subterrâneos para se protegerem de radiação e mutantes.",
                streamUrl = "https://streamtape.com/e/tt12637874-1-1",
                embedUrl = "https://streamtape.com/e/tt12637874-1-1"
            ),
            MovieItem(
                id = "mov_stranger_things",
                title = "Stranger Things",
                pageUrl = "https://streamtape.com/e/tt4574334-1-1",
                posterUrl = "https://image.tmdb.org/t/p/w500/49WJfeN0moxb9IPfGn8AIqMGskD.jpg",
                year = "2024",
                quality = "4K UHD / 1080p",
                audio = "Dublado 5.1",
                imdb = "8.7",
                category = "Série / Ficção / Suspense",
                duration = "4 Temporadas",
                synopsis = "Quando um garoto desaparece na pacata cidade de Hawkins, amigos e família descobrem um mistério envolvendo experimentos secretos, forças sobrenaturais e uma garota estranha.",
                streamUrl = "https://streamtape.com/e/tt4574334-1-1",
                embedUrl = "https://streamtape.com/e/tt4574334-1-1"
            ),
            MovieItem(
                id = "mov_last_of_us",
                title = "The Last of Us",
                pageUrl = "https://streamtape.com/e/tt3581920-1-1",
                posterUrl = "https://image.tmdb.org/t/p/w500/uKvVjK19u7RpTNxZdKxPbhA9vg1.jpg",
                year = "2023",
                quality = "4K UHD",
                audio = "Dual Áudio 5.1",
                imdb = "8.8",
                category = "Série / Drama / Ficção",
                duration = "1 Temporada",
                synopsis = "Vinte anos após uma pandemia de fungos destruir a civilização, Joel é contratado para contrabandear Ellie, uma jovem de 14 anos imune, para fora de uma zona de quarentena opressiva.",
                streamUrl = "https://streamtape.com/e/tt3581920-1-1",
                embedUrl = "https://streamtape.com/e/tt3581920-1-1"
            ),
            MovieItem(
                id = "mov_shogun",
                title = "Xógum: A Gloriosa Saga do Japão",
                pageUrl = "https://streamtape.com/e/tt2788316-1-1",
                posterUrl = "https://image.tmdb.org/t/p/w500/7O4iVfOMQmdCSxhOg1WnzG1AgYT.jpg",
                year = "2024",
                quality = "4K UHD / 1080p",
                audio = "Dual Áudio 5.1",
                imdb = "8.7",
                category = "Série / Drama / Aventura",
                duration = "1 Temporada",
                synopsis = "No Japão de 1600, Lorde Toranaga luta por sua vida contra seus inimigos no Conselho de Regentes quando um navio europeu misterioso encalha em uma vila de pescadores.",
                streamUrl = "https://streamtape.com/e/tt2788316-1-1",
                embedUrl = "https://streamtape.com/e/tt2788316-1-1"
            ),
            MovieItem(
                id = "mov_oppenheimer",
                title = "Oppenheimer",
                pageUrl = "https://autoembed.co/movie/imdb/tt15398776",
                posterUrl = "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg",
                year = "2023",
                quality = "4K UHD / IMAX",
                audio = "Dual Áudio 5.1",
                imdb = "8.9",
                category = "Drama / Suspense",
                duration = "3h 00min",
                synopsis = "A história do físico americano J. Robert Oppenheimer, seu papel no Projeto Manhattan e o desenvolvimento da bomba atômica.",
                streamUrl = "https://autoembed.co/movie/imdb/tt15398776",
                embedUrl = "https://autoembed.co/movie/imdb/tt15398776"
            ),
            MovieItem(
                id = "mov_interestelar",
                title = "Interestelar",
                pageUrl = "https://autoembed.co/movie/imdb/tt0816692",
                posterUrl = "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
                year = "2014",
                quality = "4K UHD IMAX",
                audio = "Dual Áudio 5.1",
                imdb = "8.7",
                category = "Ficção Científica / Drama",
                duration = "2h 49min",
                synopsis = "As reservas naturais da Terra estão chegando ao fim e um grupo de astronautas recebe a missão de verificar possíveis planetas para receberem a população mundial.",
                streamUrl = "https://autoembed.co/movie/imdb/tt0816692",
                embedUrl = "https://autoembed.co/movie/imdb/tt0816692"
            )
        )

        // Query search filter
        if (!query.isNullOrBlank()) {
            return allItems.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.category.contains(query, ignoreCase = true) ||
                (it.synopsis?.contains(query, ignoreCase = true) == true)
            }
        }

        // Category filter
        return filterMoviesByCategoryOrGenre(allItems, categoryName, categoryUrl)
    }
}


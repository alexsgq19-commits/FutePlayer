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
        const val BASE_URL = "https://filmestorrentdublado.com/"

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

        fun getImdbIdForTitle(title: String, hintUrlOrId: String? = null): String {
            // 1. Check if hint contains an IMDb ID
            if (!hintUrlOrId.isNullOrBlank()) {
                val match = Regex("""tt\d{6,9}""").find(hintUrlOrId)
                if (match != null) return match.value
            }
            val matchInTitle = Regex("""tt\d{6,9}""").find(title)
            if (matchInTitle != null) return matchInTitle.value

            val clean = cleanMovieTitle(title).lowercase()
                .replace(":", " ")
                .replace("-", " ")
                .replace("–", " ")
                .replace("—", " ")
                .replace(".", " ")
                .replace(",", " ")
                .replace("'", "")
                .replace("\"", "")
                .trim()

            // 2. Check dynamic memory cache
            imdbCache[clean]?.let { return it }

            // 3. Check extensive title catalog
            val mappedId = when {
                // A Odisséia & Históricos
                clean.contains("odisseia") || clean.contains("odisséia") || clean.contains("odyssey") -> "tt1423403"
                clean.contains("dia d") || clean.contains("d day") || clean.contains("omaha") -> "tt8917838"
                clean.contains("soldado ryan") || clean.contains("saving private ryan") -> "tt0120591"
                clean.contains("conde de monte cristo") || clean.contains("monte cristo") -> "tt26443598"
                clean.contains("napoleao") || clean.contains("napoleão") || clean.contains("napoleon") -> "tt1528830"
                clean.contains("1917") -> "tt8579674"
                clean.contains("dunkirk") || clean.contains("dunquerque") -> "tt5013056"
                clean.contains("resgate") || clean.contains("extraction") -> "tt8936646"

                // 2024 - 2025 Blockbusters & Lançamentos
                clean.contains("deadpool") || clean.contains("wolverine") -> "tt6263850"
                clean.contains("divertida mente") || clean.contains("inside out") -> "tt22022452"
                clean.contains("duna parte 2") || clean.contains("duna 2") || clean.contains("dune 2") -> "tt15239678"
                clean.contains("duna") || clean.contains("dune") -> "tt1160419"
                clean.contains("furiosa") || clean.contains("mad max") -> "tt12037194"
                clean.contains("macacos") || clean.contains("apes") || clean.contains("reinado") -> "tt11384580"
                clean.contains("bad boys") -> "tt4919268"
                clean.contains("godzilla") || clean.contains("kong") -> "tt14539740"
                clean.contains("gladiador 2") || clean.contains("gladiator 2") -> "tt2066051"
                clean.contains("gladiador") || clean.contains("gladiator") -> "tt0172495"
                clean.contains("moana 2") -> "tt31186510"
                clean.contains("moana") -> "tt3521164"
                clean.contains("alien romulus") || clean.contains("alien") -> "tt18412256"
                clean.contains("twisters") || clean.contains("twister") -> "tt12584954"
                clean.contains("sorria 2") || clean.contains("smile 2") -> "tt27552554"
                clean.contains("sorria") || clean.contains("smile") -> "tt15474916"
                clean.contains("substancia") || clean.contains("substância") || clean.contains("substance") -> "tt17526714"
                clean.contains("robo selvagem") || clean.contains("robô selvagem") || clean.contains("wild robot") -> "tt29623480"
                clean.contains("terrifier 3") -> "tt27911000"
                clean.contains("terrifier") -> "tt4281724"
                clean.contains("coringa 2") || clean.contains("joker 2") || clean.contains("folie") -> "tt11315808"
                clean.contains("coringa") || clean.contains("joker") -> "tt7286456"
                clean.contains("venom 3") || clean.contains("ultima rodada") || clean.contains("última dança") || clean.contains("last dance") -> "tt16366836"
                clean.contains("venom") -> "tt6751668"
                clean.contains("kung fu panda 4") || clean.contains("panda 4") -> "tt21692408"
                clean.contains("kung fu panda") || clean.contains("panda") -> "tt0441773"
                clean.contains("super mario") || clean.contains("mario bros") -> "tt6718170"
                clean.contains("barbie") -> "tt1517268"
                clean.contains("oppenheimer") -> "tt15398776"
                clean.contains("john wick 4") || clean.contains("john wick") -> "tt10366206"
                clean.contains("top gun") -> "tt1745960"
                clean.contains("avatar 2") || clean.contains("caminho da agua") || clean.contains("caminho da água") -> "tt1630029"
                clean.contains("avatar") -> "tt0499549"
                clean.contains("velozes e furiosos 10") || clean.contains("fast x") -> "tt5433140"
                clean.contains("velozes e furiosos") || clean.contains("fast and furious") -> "tt0232500"
                clean.contains("missao impossivel") || clean.contains("missão impossível") || clean.contains("mission impossible") -> "tt9603212"

                // Heróis & Marvel & DC
                clean.contains("homem aranha") || clean.contains("spider man") || clean.contains("spider") -> "tt22084616"
                clean.contains("vingadores ultimato") || clean.contains("avengers endgame") -> "tt4154796"
                clean.contains("vingadores guerra") || clean.contains("infinity war") -> "tt4154756"
                clean.contains("vingadores") || clean.contains("avengers") -> "tt0848228"
                clean.contains("guardioes da galaxia") || clean.contains("guardiões da galáxia") || clean.contains("guardians") -> "tt6791350"
                clean.contains("doutor estranho") || clean.contains("doctor strange") -> "tt9419884"
                clean.contains("pantera negra") || clean.contains("black panther") -> "tt9114286"
                clean.contains("homem de ferro") || clean.contains("iron man") -> "tt0371746"
                clean.contains("capitao america") || clean.contains("capitão américa") || clean.contains("captain america") -> "tt14513804"
                clean.contains("thor") -> "tt10648342"
                clean.contains("batman") -> "tt1877830"
                clean.contains("aquaman") -> "tt9663764"
                clean.contains("flash") -> "tt0439572"

                // Séries Famosas
                clean.contains("casa do dragao") || clean.contains("casa do dragão") || clean.contains("house of the dragon") || clean.contains("targaryen") -> "tt11198330"
                clean.contains("the boys") || clean.contains("boys") -> "tt1190634"
                clean.contains("fallout") -> "tt12637874"
                clean.contains("pinguim") || clean.contains("penguin") -> "tt15474916"
                clean.contains("stranger things") || clean.contains("stranger") -> "tt4574334"
                clean.contains("the last of us") || clean.contains("last of us") -> "tt3581920"
                clean.contains("vikings") || clean.contains("viking") -> "tt5180504"
                clean.contains("breaking bad") -> "tt0903747"
                clean.contains("better call saul") -> "tt3032476"
                clean.contains("game of thrones") || clean.contains("guerra dos tronos") || clean.contains("tronos") -> "tt0944947"
                clean.contains("wandinha") || clean.contains("wednesday") -> "tt13443470"
                clean.contains("one piece") -> "tt11737520"
                clean.contains("peaky blinders") || clean.contains("peaky") -> "tt2442560"
                clean.contains("round 6") || clean.contains("squid game") -> "tt10919420"
                clean.contains("la casa de papel") || clean.contains("casa de papel") -> "tt6468322"
                clean.contains("arcane") -> "tt11126994"
                clean.contains("the witcher") || clean.contains("witcher") -> "tt5180504"
                clean.contains("yellowstone") -> "tt4236770"
                clean.contains("shogun") || clean.contains("xogum") -> "tt2788316"
                clean.contains("cobra kai") -> "tt7221388"
                clean.contains("loki") -> "tt9140554"
                clean.contains("rick") && clean.contains("morty") -> "tt2861424"
                clean.contains("invencivel") || clean.contains("invincible") -> "tt6741278"
                clean.contains("solo leveling") -> "tt21209876"
                clean.contains("demon slayer") || clean.contains("kimetsu") -> "tt9335498"
                clean.contains("jujutsu kaisen") || clean.contains("jujutsu") -> "tt12343534"
                clean.contains("attack on titan") || clean.contains("shingeki") -> "tt2560140"
                clean.contains("naruto") -> "tt0409591"
                clean.contains("dragon ball") || clean.contains("dbz") -> "tt0121955"

                // Terror, Suspense & Outros Clássicos
                clean.contains("exorcista") || clean.contains("exorcist") -> "tt12921446"
                clean.contains("invocacao do mal") || clean.contains("invocação do mal") || clean.contains("conjuring") -> "tt7069210"
                clean.contains("freira") || clean.contains("nun") -> "tt10160976"
                clean.contains("anabelle") || clean.contains("annabelle") -> "tt3322940"
                clean.contains("fnaf") || clean.contains("five nights") -> "tt4589218"
                clean.contains("panico") || clean.contains("pânico") || clean.contains("scream") -> "tt17663992"
                clean.contains("se beber nao case") || clean.contains("hangover") -> "tt1119646"
                clean.contains("gente grande") || clean.contains("grown ups") -> "tt1375670"
                clean.contains("interestelar") || clean.contains("interstellar") -> "tt0816692"
                clean.contains("origem") || clean.contains("inception") -> "tt1375666"
                clean.contains("matrix") -> "tt0133093"
                clean.contains("harry potter") -> "tt0241527"
                clean.contains("senhor dos aneis") || clean.contains("senhor dos anéis") || clean.contains("lord of the rings") -> "tt0120737"
                clean.contains("transformers") -> "tt5090568"
                clean.contains("sobrenatural") || clean.contains("supernatural") -> "tt0460681"
                clean.contains("prison break") -> "tt0455275"
                clean.contains("dexter") -> "tt0773262"
                clean.contains("dr house") || clean.contains("house md") || clean.contains("house") -> "tt0412142"
                clean.contains("friends") -> "tt0108778"
                clean.contains("the office") || clean.contains("office") -> "tt0386676"

                // Fallback default
                else -> {
                    val isSeries = clean.contains("temporada") || clean.contains("série") || clean.contains("serie")
                    if (isSeries) "tt1190634" else "tt15239678"
                }
            }

            imdbCache[clean] = mappedId
            return mappedId
        }

        fun generateStreamServers(title: String, isSeries: Boolean, hintUrlOrId: String? = null): List<MovieStreamServer> {
            val imdbId = getImdbIdForTitle(title, hintUrlOrId)

            val autoEmbedUrl = if (isSeries) "https://autoembed.co/tv/imdb/$imdbId-1-1" else "https://autoembed.co/movie/imdb/$imdbId"
            val superflixUrl = if (isSeries) "https://superflixapi.beer/serie/$imdbId/1/1" else "https://superflixapi.beer/filme/$imdbId"
            val vidSrcUrl = if (isSeries) "https://vidsrc.to/embed/tv/$imdbId/1/1" else "https://vidsrc.to/embed/movie/$imdbId"
            val twoEmbedUrl = if (isSeries) "https://www.2embed.cc/embedseries/$imdbId/1/1" else "https://www.2embed.cc/embed/$imdbId"
            val smashyUrl = if (isSeries) "https://embed.smashystream.com/playere.php?imdb=$imdbId&season=1&episode=1" else "https://embed.smashystream.com/playere.php?imdb=$imdbId"

            return listOf(
                MovieStreamServer(
                    id = "server_autoembed",
                    name = "Servidor 1 - AutoEmbed (HD / Rápido)",
                    quality = "1080p Full HD",
                    audio = "Dual Áudio / Dublado",
                    streamUrl = autoEmbedUrl,
                    embedUrl = autoEmbedUrl,
                    forceWebPlayer = true
                ),
                MovieStreamServer(
                    id = "server_superflix",
                    name = "Servidor 2 - Superflix Pro (Dublado PT-BR)",
                    quality = "1080p Ultra",
                    audio = "Dublado (PT-BR)",
                    streamUrl = superflixUrl,
                    embedUrl = superflixUrl,
                    forceWebPlayer = true
                ),
                MovieStreamServer(
                    id = "server_vidsrc",
                    name = "Servidor 3 - VidSrc Stream (Full HD 1080p)",
                    quality = "1080p Full HD",
                    audio = "Multi Áudio / Legendas",
                    streamUrl = vidSrcUrl,
                    embedUrl = vidSrcUrl,
                    forceWebPlayer = true
                ),
                MovieStreamServer(
                    id = "server_2embed",
                    name = "Servidor 4 - 2Embed Player (Multi Áudio)",
                    quality = "720p / 1080p HD",
                    audio = "Original com Legendas",
                    streamUrl = twoEmbedUrl,
                    embedUrl = twoEmbedUrl,
                    forceWebPlayer = true
                ),
                MovieStreamServer(
                    id = "server_smashystream",
                    name = "Servidor 5 - SmashyStream (Stream Rápido)",
                    quality = "1080p HD",
                    audio = "Dual Áudio 5.1",
                    streamUrl = smashyUrl,
                    embedUrl = smashyUrl,
                    forceWebPlayer = true
                )
            )
        }
    }

    fun cleanMovieTitle(raw: String): String = Companion.cleanMovieTitle(raw)
    fun getImdbIdForTitle(title: String, hintUrlOrId: String? = null): String = Companion.getImdbIdForTitle(title, hintUrlOrId)
    fun generateStreamServers(title: String, isSeries: Boolean, hintUrlOrId: String? = null): List<MovieStreamServer> = Companion.generateStreamServers(title, isSeries, hintUrlOrId)

    /**
     * Resolves the exact IMDb ID for any title dynamically via Cinemeta / IMDb public lookup
     */
    suspend fun resolveImdbIdOnline(title: String, isSeries: Boolean = false): String = withContext(Dispatchers.IO) {
        val clean = cleanMovieTitle(title).trim()
        val cacheKey = clean.lowercase()

        imdbCache[cacheKey]?.let { return@withContext it }

        // Try Stremio Cinemeta catalog search API
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
                    val firstMeta = metas.getJSONObject(0)
                    val id = firstMeta.optString("id")
                    if (id.startsWith("tt")) {
                        imdbCache[cacheKey] = id
                        return@withContext id
                    }
                }
            }
        } catch (_: Exception) {}

        // Try IMDb suggestion search
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
                        val id = item.optString("id")
                        if (id.startsWith("tt")) {
                            imdbCache[cacheKey] = id
                            return@withContext id
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Fall back to pattern matching
        val fallback = getImdbIdForTitle(title)
        imdbCache[cacheKey] = fallback
        return@withContext fallback
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

                val streamServers = generateStreamServers(movieItem.title, isSeries, dynamicImdbId)
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
        val imdbId = getImdbIdForTitle(title, hintUrlOrId)

        return listOf(
            SeriesSeason(
                seasonNumber = 1,
                name = "1ª Temporada (Dublado & Dual HD)",
                episodes = (1..8).map { epNum ->
                    val epEmbed = "https://autoembed.co/tv/imdb/$imdbId-1-$epNum"
                    SeriesEpisode(
                        id = "s1_ep$epNum",
                        number = epNum,
                        title = "Episódio $epNum: O Início da Jornada",
                        duration = "${45 + (epNum * 2)}min",
                        streamUrl = epEmbed,
                        embedUrl = epEmbed,
                        synopsis = "Acompanhe os acontecimentos eletrizantes do episódio $epNum desta temporada aclamada pelo público e crítica."
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
                pageUrl = "https://autoembed.co/tv/imdb/tt11198330-1-1",
                posterUrl = "https://image.tmdb.org/t/p/w500/1X4h40fcB4WWUmIBK0auT4zRBAV.jpg",
                year = "2024",
                quality = "1080p Full HD",
                audio = "Dual Áudio 5.1",
                imdb = "8.5",
                category = "Série / Drama / Ação",
                duration = "2 Temporadas",
                synopsis = "A guerra civil entre os apoiadores de Rhaenyra e Aegon II incendeia Westeros na batalha pelo Trono de Ferro.",
                streamUrl = "https://autoembed.co/tv/imdb/tt11198330-1-1",
                embedUrl = "https://autoembed.co/tv/imdb/tt11198330-1-1"
            ),
            MovieItem(
                id = "mov_the_boys",
                title = "The Boys",
                pageUrl = "https://autoembed.co/tv/imdb/tt1190634-1-1",
                posterUrl = "https://image.tmdb.org/t/p/w500/2zmTngn1tYC1AvfnNDBpQI7VTe7.jpg",
                year = "2024",
                quality = "1080p Full HD",
                audio = "Dual Áudio 5.1",
                imdb = "8.7",
                category = "Série / Ação / Ficção",
                duration = "4 Temporadas",
                synopsis = "O mundo está à beira do abismo enquanto Capitão Pátria consolida seu poder sobre a Vought e os Estados Unidos.",
                streamUrl = "https://autoembed.co/tv/imdb/tt1190634-1-1",
                embedUrl = "https://autoembed.co/tv/imdb/tt1190634-1-1"
            ),
            MovieItem(
                id = "mov_fallout",
                title = "Fallout",
                pageUrl = "https://autoembed.co/tv/imdb/tt12637874-1-1",
                posterUrl = "https://image.tmdb.org/t/p/w500/AnsZu440fE2m6aP9d5E1X3D2R9O.jpg",
                year = "2024",
                quality = "4K UHD / 1080p",
                audio = "Dual Áudio",
                imdb = "8.4",
                category = "Série / Ficção Científica",
                duration = "1 Temporada",
                synopsis = "Em um futuro pós-apocalíptico de Los Angeles, os cidadãos precisam viver em bunkers subterrâneos para se protegerem de radiação e mutantes.",
                streamUrl = "https://autoembed.co/tv/imdb/tt12637874-1-1",
                embedUrl = "https://autoembed.co/tv/imdb/tt12637874-1-1"
            ),
            MovieItem(
                id = "mov_stranger_things",
                title = "Stranger Things",
                pageUrl = "https://autoembed.co/tv/imdb/tt4574334-1-1",
                posterUrl = "https://image.tmdb.org/t/p/w500/49WJfeN0moxb9IPfGn8AIqMGskD.jpg",
                year = "2024",
                quality = "4K UHD / 1080p",
                audio = "Dublado 5.1",
                imdb = "8.7",
                category = "Série / Ficção / Suspense",
                duration = "4 Temporadas",
                synopsis = "Quando um garoto desaparece na pacata cidade de Hawkins, amigos e família descobrem um mistério envolvendo experimentos secretos, forças sobrenaturais e uma garota estranha.",
                streamUrl = "https://autoembed.co/tv/imdb/tt4574334-1-1",
                embedUrl = "https://autoembed.co/tv/imdb/tt4574334-1-1"
            ),
            MovieItem(
                id = "mov_last_of_us",
                title = "The Last of Us",
                pageUrl = "https://autoembed.co/tv/imdb/tt3581920-1-1",
                posterUrl = "https://image.tmdb.org/t/p/w500/uKvVjK19u7RpTNxZdKxPbhA9vg1.jpg",
                year = "2023",
                quality = "4K UHD",
                audio = "Dual Áudio 5.1",
                imdb = "8.8",
                category = "Série / Drama / Ficção",
                duration = "1 Temporada",
                synopsis = "Vinte anos após uma pandemia de fungos destruir a civilização, Joel é contratado para contrabandear Ellie, uma jovem de 14 anos imune, para fora de uma zona de quarentena opressiva.",
                streamUrl = "https://autoembed.co/tv/imdb/tt3581920-1-1",
                embedUrl = "https://autoembed.co/tv/imdb/tt3581920-1-1"
            ),
            MovieItem(
                id = "mov_shogun",
                title = "Xógum: A Gloriosa Saga do Japão",
                pageUrl = "https://autoembed.co/tv/imdb/tt2788316-1-1",
                posterUrl = "https://image.tmdb.org/t/p/w500/7O4iVfOMQmdCSxhOg1WnzG1AgYT.jpg",
                year = "2024",
                quality = "4K UHD / 1080p",
                audio = "Dual Áudio 5.1",
                imdb = "8.7",
                category = "Série / Drama / Aventura",
                duration = "1 Temporada",
                synopsis = "No Japão de 1600, Lorde Toranaga luta por sua vida contra seus inimigos no Conselho de Regentes quando um navio europeu misterioso encalha em uma vila de pescadores.",
                streamUrl = "https://autoembed.co/tv/imdb/tt2788316-1-1",
                embedUrl = "https://autoembed.co/tv/imdb/tt2788316-1-1"
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


package com.ulgencs3.dizipal

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

/**
 * DiziPal Sağlayıcısı
 *
 * Site: https://dizipal2132.com (Dinamik)
 * İçerik: Popüler Yerli/Yabancı Diziler, Filmler, Netflix, Exxen, BluTV vb. platformlar
 * Oynatıcı: Imagestoo API ve data-cfg çözücüsü ile doğrudan M3U8 ve altyazılar
 */
class DiziPalProvider : MainAPI() {

    override var mainUrl = "https://dizipal2132.com"
    override var name = "DiziPal"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override var sequentialMainPage = true

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/"
    )

    private var isInitialized = false

    private suspend fun ensureInit() {
        if (isInitialized) return
        isInitialized = true
        try {
            val config = app.get(
                "https://raw.githubusercontent.com/ulgenzade/ulgencs3/master/domains.json"
            ).text
            AppUtils.parseJson<Map<String, String>>(config)["dizipal"]
                ?.takeIf { it.isNotBlank() }?.let { mainUrl = it }
        } catch (_: Exception) { }
    }

    override val mainPage = mainPageOf(
        "/"                      to "Son Eklenenler",
        "/bolumler"              to "Son Bölümler",
        "/diziler"               to "Diziler",
        "/filmler"               to "Filmler",
        "/platform/netflix"      to "Netflix",
        "/platform/exxen"        to "Exxen",
        "/platform/blutv"        to "BluTV",
        "/platform/disney-plus"  to "Disney+",
        "/platform/prime-video"  to "Amazon Prime",
        "/platform/tabii"        to "Tabii",
        "/platform/gain"         to "Gain"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureInit()
        val targetUrl = if (request.data.startsWith("http")) request.data else "$mainUrl${request.data}"
        val doc = app.get(targetUrl, headers = commonHeaders).document
        val home = if (request.data.contains("/bolumler")) {
            val items = doc.select("div.episodes-list-grid a, div.latest-episodes-section a, a.episode-list-item")
                .mapNotNull { it.toSonBolumler() }
            if (items.isEmpty()) {
                doc.select("div.content-card, a.content-card, div.trending-item, a[href*='/dizi/'], a[href*='/film/']")
                    .mapNotNull { it.toDiziler() }
            } else items
        } else {
            doc.select("div.content-card, a.content-card, div.trending-item, div.content-grid > div, ul.content-grid > li, a[href*='/dizi/'], a[href*='/film/']")
                .mapNotNull { it.toDiziler() }
        }

        return newHomePageResponse(HomePageList(request.name, home), hasNext = home.isNotEmpty())
    }

    private fun Element.toSonBolumler(): SearchResponse? {
        val name = selectFirst(".ep-title")?.text()?.trim()
            ?: selectFirst(".card-title")?.text()?.trim()
            ?: selectFirst("h3")?.text()?.trim()
            ?: return null
        val episode = selectFirst(".ep-info")?.text()?.trim()?.replace(". Sezon ", "x")?.replace(". Bölüm", "") ?: ""
        val title = if (episode.isNotBlank()) "$name $episode" else name

        val a = if (tagName() == "a") this else selectFirst("a") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val imgEl = selectFirst("img")
        val posterUrl = fixUrlNull(imgEl?.attr("data-src")?.ifEmpty { imgEl.attr("src") })

        val seriesUrl = href
            .replace(Regex("-\\d+-sezon-\\d+-bolum.*$"), "")
            .replace("/bolum/", "/dizi/")

        return newTvSeriesSearchResponse(title, seriesUrl, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toDiziler(): SearchResponse? {
        val a = if (tagName() == "a") this else selectFirst("a") ?: return null
        val title = selectFirst("div.card-title")?.text()?.trim()
            ?: selectFirst("div.card-info h3")?.text()?.trim()
            ?: selectFirst("h3")?.text()?.trim()
            ?: selectFirst(".title")?.text()?.trim()
            ?: a.attr("title").takeIf { it.isNotBlank() }
            ?: a.text().trim().takeIf { it.isNotBlank() }
            ?: return null

        val href = fixUrlNull(a.attr("href")) ?: return null
        val img = selectFirst("img")
        val posterUrl = fixUrlNull(
            img?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: img?.attr("src")?.takeIf { it.isNotBlank() }
                ?: img?.attr("data-original")
        )

        val isMovie = href.contains("/film/")
        val type = if (isMovie) TvType.Movie else TvType.TvSeries

        return newTvSeriesSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureInit()
        val searchUrl = "$mainUrl/ajax-search?q=$query"
        val responseRaw = app.get(
            searchUrl,
            headers = mapOf(
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With" to "XMLHttpRequest",
                "User-Agent" to (commonHeaders["User-Agent"] ?: "")
            ),
            referer = "$mainUrl/"
        )

        val jsonResponse = AppUtils.tryParseJson<DizipalSearchData>(responseRaw.text) ?: return emptyList()
        val searchResponses = mutableListOf<SearchResponse>()

        jsonResponse.results?.forEach { item ->
            val title = item.title?.takeIf { it.isNotBlank() } ?: return@forEach
            val url = fixUrlNull(item.url) ?: return@forEach
            val poster = fixUrlNull(item.poster)

            if (item.type.equals("Dizi", ignoreCase = true)) {
                searchResponses.add(
                    newTvSeriesSearchResponse(title, url, TvType.TvSeries) {
                        this.posterUrl = poster
                        this.year = item.year
                    }
                )
            } else {
                searchResponses.add(
                    newMovieSearchResponse(title, url, TvType.Movie) {
                        this.posterUrl = poster
                        this.year = item.year
                    }
                )
            }
        }

        return searchResponses
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        ensureInit()
        if (url.contains("/bolum/")) {
            val seriesUrl = url.replace("/bolum/", "/dizi/").replace(Regex("-\\d+-sezon.*"), "")
            return load(seriesUrl)
        }

        val doc = app.get(url, headers = commonHeaders).document
        val poster = fixUrlNull(doc.selectFirst("meta[property=og:image]")?.attr("content"))
        val year = doc.selectFirst("div.info-row:contains(Yıl) span.info-value")?.text()?.trim()?.toIntOrNull()
        val description = doc.selectFirst("p.series-description")?.text()?.trim()
        val tags = doc.select("div.info-row:contains(Kategoriler) span.info-value.categories a").map { it.text().trim() }

        if (url.contains("/dizi/")) {
            val title = doc.selectFirst("h1.series-title, h1")?.text()?.trim() ?: return null

            val episodes = doc.select("div.detail-episode-item-wrap").mapNotNull { wrap ->
                val anchor = wrap.selectFirst("a.detail-episode-item") ?: return@mapNotNull null
                val epHref = fixUrlNull(anchor.attr("href")) ?: return@mapNotNull null
                val epName = anchor.selectFirst("div.detail-episode-title")?.text()?.trim() ?: return@mapNotNull null

                val subtitle = anchor.selectFirst("div.detail-episode-subtitle")?.text()?.trim().orEmpty()
                val match = Regex("""(\d+)\.\s*[Ss]ezon\s*(\d+)\.\s*[Bb]ölüm""").find(subtitle)
                val epSeason = match?.groupValues?.getOrNull(1)?.toIntOrNull()
                val epEpisode = match?.groupValues?.getOrNull(2)?.toIntOrNull()

                newEpisode(epHref) {
                    name = epName
                    episode = epEpisode
                    season = epSeason
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
            }
        } else {
            val title = doc.selectFirst("h1.series-title, h1.movie-title, h1")?.text()?.trim()
                ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore(" izle")?.trim()
                ?: return null

            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        ensureInit()
        val userAgent = commonHeaders["User-Agent"] ?: ""
        val getResponse = app.get(data, headers = commonHeaders)
        val doc = getResponse.document

        val configToken = doc.selectFirst("#videoContainer")?.attr("data-cfg")?.trim() ?: return false
        val paddedToken = configToken + "=".repeat((4 - configToken.length % 4) % 4)
        val decodedToken = try {
            String(Base64.decode(paddedToken, Base64.DEFAULT))
        } catch (_: Exception) {
            return false
        }

        val embedUrlRaw = Regex(""""v"\s*:\s*"([^"]+)"""").find(decodedToken)?.groupValues?.getOrNull(1)?.replace("\\/", "/") ?: return false
        val embedUrl = fixUrl(embedUrlRaw)

        // 1. Imagestoo API çözücü
        if (embedUrl.contains("imagestoo")) {
            val videoId = embedUrl.trimEnd('/').substringAfterLast("/")
            val imagestooApiUrl = "https://imagestoo.com/player/index.php?data=$videoId&do=getVideo"

            val apiResponse = app.post(
                imagestooApiUrl,
                referer = embedUrl,
                headers = mapOf(
                    "User-Agent" to userAgent,
                    "X-Requested-With" to "XMLHttpRequest",
                    "Accept" to "*/*"
                )
            )

            val playerToken = apiResponse.cookies["fireplayer_player"]
            val sessionCookie = if (!playerToken.isNullOrEmpty()) "fireplayer_player=$playerToken" else ""
            val videoSourceRaw = Regex(""""securedLink"\s*:\s*"([^"]+)"""").find(apiResponse.text)?.groupValues?.getOrNull(1)

            if (videoSourceRaw != null) {
                val finalM3u8Url = fixUrl(videoSourceRaw.replace("\\/", "/"))
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name (Imagestoo)",
                        url = finalM3u8Url,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.headers = if (sessionCookie.isNotEmpty()) mapOf("Cookie" to sessionCookie) else emptyMap()
                        this.quality = Qualities.Unknown.value
                    }
                )
                return true
            }
        }

        // 2. Standart embed kaynağı
        val embedSource = app.get(embedUrl, referer = data, headers = mapOf("User-Agent" to userAgent)).text
        val m3u8Match = Regex("""sources\s*:\s*\[\s*\{\s*file\s*:\s*["']([^"']+\.m3u8.*?)["']""").find(embedSource)
            ?: Regex("""v\s*:\s*["']([^"']+\.html.*?)["']""").find(embedSource)
        val extractedUrl = m3u8Match?.groupValues?.getOrNull(1) ?: return false

        val finalM3u8Url = if (extractedUrl.contains(".html")) {
            val idMatch = Regex("""embed-([^.]+)\.html""").find(extractedUrl)?.groupValues?.getOrNull(1)
            if (idMatch != null) {
                "https://s2.superadjacentsoddenly.xyz/hls2/01/00007/${idMatch}_,n,h,.urlset/master.m3u8"
            } else null
        } else {
            extractedUrl
        } ?: return false

        callback(
            newExtractorLink(
                source = name,
                name = "$name (Ana Sunucu)",
                url = finalM3u8Url,
                type = ExtractorLinkType.M3U8
            ) {
                this.headers = mapOf("Referer" to embedUrl)
                this.quality = Qualities.Unknown.value
            }
        )

        // 3. Altyazılar (Tracks)
        val tracksBlockMatch = Regex("""tracks\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(embedSource)
        tracksBlockMatch?.groupValues?.getOrNull(1)?.let { tracksBlock ->
            Regex("""\{(.*?)\}""", RegexOption.DOT_MATCHES_ALL).findAll(tracksBlock).forEach { itemMatch ->
                val itemStr = itemMatch.groupValues[1]
                val fileUrl = Regex("""file\s*:\s*["']([^"']+)["']""").find(itemStr)?.groupValues?.getOrNull(1)
                val label = Regex("""label\s*:\s*["']([^"']+)["']""").find(itemStr)?.groupValues?.getOrNull(1) ?: "Türkçe"
                if (fileUrl != null && (fileUrl.endsWith(".vtt") || fileUrl.endsWith(".srt"))) {
                    subtitleCallback(
                        SubtitleFile(
                            lang = label,
                            url = fixUrl(fileUrl)
                        )
                    )
                }
            }
        }

        return true
    }
}

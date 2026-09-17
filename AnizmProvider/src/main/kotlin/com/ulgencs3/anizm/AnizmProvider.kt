package com.ulgencs3.anizm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.*
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.nodes.Element

/**
 * Anizm Sağlayıcısı
 *
 * Site: https://anizm.net
 * Korumalar: CloudflareKiller ile otomatik aşma
 * Fandom & Oynatıcılar: Filemoon, Vidmoly, Sibnet, Streamtape, Doodstream, Mp4upload vb.
 */
class AnizmProvider : MainAPI() {

    override var mainUrl = "https://anizm.net"
    override var name = "Anizm"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val cfInterceptor by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)
            if (response.code == 403 || response.code == 503) {
                return cloudflareKiller.intercept(chain)
            }
            return response
        }
    }

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/",
        "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.8"
    )

    private var isInitialized = false

    private suspend fun ensureInit() {
        if (isInitialized) return
        isInitialized = true
        try {
            val config = app.get(
                "https://raw.githubusercontent.com/ulgenzade/ulgencs3/master/domains.json"
            ).text
            org.json.JSONObject(config).optString("anizm")
                ?.takeIf { it.isNotBlank() }?.let { mainUrl = it }
        } catch (_: Exception) { }
    }

    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")

    // -------------------------------------------------------------------------
    // Ana Sayfa
    // -------------------------------------------------------------------------

    override val mainPage = mainPageOf(
        "/anime-listesi/?filtre=yeni-eklenenler&sayfa=" to "Yeni Eklenenler",
        "/anime-listesi/?filtre=puan&sayfa="           to "En Yüksek Puanlılar",
        "/anime-listesi/?filtre=izlenme&sayfa="        to "En Çok İzlenenler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureInit()
        val targetUrl = if (request.data.startsWith("http")) "${request.data}$page" else "$mainUrl${request.data}$page"
        val doc = app.get(targetUrl, headers = commonHeaders, interceptor = cfInterceptor).document
        val items = doc.select("div.animeCard, li.listItem, div.poster-item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(HomePageList(request.name, items), hasNext = items.isNotEmpty())
    }

    // -------------------------------------------------------------------------
    // Arama
    // -------------------------------------------------------------------------

    override suspend fun search(query: String): List<SearchResponse> {
        ensureInit()
        val doc = app.get(
            "$mainUrl/arama/?q=${query.encodeUrl()}",
            headers = commonHeaders,
            interceptor = cfInterceptor
        ).document
        return doc.select("div.animeCard, li.listItem, div.poster-item").mapNotNull { it.toSearchResult() }
    }

    // -------------------------------------------------------------------------
    // Detay & Bölüm Listesi
    // -------------------------------------------------------------------------

    override suspend fun load(url: String): LoadResponse {
        ensureInit()
        val doc = app.get(url, headers = commonHeaders, interceptor = cfInterceptor).document

        val title = doc.selectFirst("h1.animeTitle, h2.animeName, h1.title")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content") ?: "Bilinmeyen Anime"
        val poster = fixUrlNull(
            doc.selectFirst("div.animePoster img, img.animeCover, div.poster img")?.attr("data-src")
                ?: doc.selectFirst("div.animePoster img, img.animeCover, div.poster img")?.attr("src")
                ?: doc.selectFirst("meta[property=og:image]")?.attr("content")
        )
        val description = doc.selectFirst("p.animeDesc, div.animeDescription, div.summary")?.text()?.trim()
        val tags = doc.select("div.animeGenres a, span.genre, a[href*='tur']").map { it.text().trim() }

        val rawEpisodes = doc.select("ul.episodeList li a, div.episodeItem a, a[href*='-bolum'], div.bolumler a, ul.bolum-listesi li a")
            .mapNotNull { el ->
                val epUrl = fixUrlNull(el.attr("href")) ?: return@mapNotNull null
                val epText = el.text().trim()
                val epNum = Regex("""(\d+)""").find(epText)?.groupValues?.get(1)?.toIntOrNull()
                val cleanText = epText.replace(Regex("""^\s*\d+\.\s*Bölüm\s*[-–:]*\s*"""), "").trim()
                val finalName = cleanText.takeIf { it.isNotBlank() && !it.equals("Bölüm", ignoreCase = true) }
                newEpisode(epUrl) {
                    name = finalName
                    episode = epNum
                    season = 1
                    posterUrl = poster
                }
            }.distinctBy { it.data }

        val episodes = if (rawEpisodes.any { (it.episode ?: 0) > 0 }) {
            rawEpisodes.sortedBy { it.episode ?: 0 }
        } else {
            rawEpisodes.reversed()
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    // -------------------------------------------------------------------------
    // Video Linkleri & Oynatıcı Çıkarıcı
    // -------------------------------------------------------------------------

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        ensureInit()
        val doc = app.get(data, headers = commonHeaders, interceptor = cfInterceptor).document
        val extractedUrls = mutableSetOf<String>()

        // 1. Alternatif sekmeler ve butonlar (Fansub & Medya Oynatıcıları)
        doc.select("div.fansub-item, ul.nav-tabs li a, button[data-embed], a[data-frame], div[data-src], a[data-url]").forEach { el ->
            val src = fixUrlNull(
                el.attr("data-embed").takeIf { it.isNotBlank() }
                    ?: el.attr("data-frame").takeIf { it.isNotBlank() }
                    ?: el.attr("data-src").takeIf { it.isNotBlank() }
                    ?: el.attr("data-url")
            ) ?: return@forEach
            if (extractedUrls.add(src)) {
                loadExtractor(src, mainUrl, subtitleCallback, callback)
            }
        }

        // 2. iframe embed'leri
        doc.select("iframe[src], iframe[data-src]").forEach { iframe ->
            val src = fixUrlNull(
                iframe.attr("src").takeIf { it.isNotBlank() } ?: iframe.attr("data-src")
            ) ?: return@forEach
            if (!src.contains("a-ads.com") && extractedUrls.add(src)) {
                loadExtractor(src, mainUrl, subtitleCallback, callback)
            }
        }

        // 3. Doğrudan video bağlantıları & m3u8
        doc.select("source[src]").forEach { source ->
            val src = fixUrlNull(source.attr("src")) ?: return@forEach
            if (extractedUrls.add(src)) {
                val type = source.attr("type")
                callback(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = src,
                        type = if (type.contains("mpegurl") || src.contains("m3u8"))
                            ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    )
                )
            }
        }

        // 4. Script içindeki linkler
        val scriptContent = doc.select("script").joinToString("\n") { it.data() }
        Regex("""(?:file|source|src)\s*:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""")
            .findAll(scriptContent)
            .forEach { match ->
                val videoUrl = match.groupValues[1]
                if (extractedUrls.add(videoUrl)) {
                    callback(
                        newExtractorLink(
                            source = name,
                            name = name,
                            url = videoUrl,
                            type = if (videoUrl.contains("m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        )
                    )
                }
            }

        return true
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = selectFirst("a") ?: return null
        val title = selectFirst("div.animeName, span.title, h3, div.title")?.text()?.trim()
            ?: a.attr("title").takeIf { it.isNotBlank() }
            ?: return null
        val url = fixUrlNull(a.attr("href")) ?: return null
        val poster = fixUrlNull(
            selectFirst("img")?.attr("data-src")?.takeIf { !it.contains("base64") }
                ?: selectFirst("img")?.attr("src")
        )
        return newAnimeSearchResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
        }
    }
}

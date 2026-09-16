package com.ulgencs3.tranimeizle

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

/**
 * TrAnimeIzle Sağlayıcısı
 *
 * Site: https://www.tranimeizle.io
 * Yapı: PHP tabanlı HTML sitesi
 * Not: Bot kontrol sayfası var — User-Agent + cookie jar ile geçilebilir
 */
class TrAnimeIzleProvider : MainAPI() {

    override var mainUrl = "https://www.tranimeizle.io"
    override var name = "TrAnimeİzle"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to mainUrl,
        "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.8",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
    )

    private var isInitialized = false

    private suspend fun ensureInit() {
        if (isInitialized) return
        isInitialized = true
        try {
            val config = app.get(
                "https://raw.githubusercontent.com/ulgenzade/ulgencs3/master/domains.json"
            ).text
            AppUtils.parseJson<Map<String, String>>(config)["tranimeizle"]
                ?.takeIf { it.isNotBlank() }?.let { mainUrl = it }

            // Bot kontrol cookie'sini al — CloudStream'in cookie jar'ına kaydeder
            app.get(mainUrl, headers = commonHeaders)
        } catch (e: Exception) { }
    }

    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")

    override val mainPage = mainPageOf(
        "$mainUrl/anime-listesi?sayfa=" to "Tüm Animeler",
        "$mainUrl/?filtre=yeni&sayfa="  to "Yeni Eklenenler",
        "$mainUrl/?filtre=popular&sayfa=" to "Popüler Animeler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureInit()
        val doc = app.get("${request.data}$page", headers = commonHeaders).document
        val items = doc.select("div.anime-card, li.anime-item, div.movie-item")
            .mapNotNull { it.toSearchResult() }
        return newHomePageResponse(HomePageList(request.name, items), hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureInit()
        val doc = app.get(
            "$mainUrl/arama?q=${query.encodeUrl()}",
            headers = commonHeaders
        ).document
        return doc.select("div.anime-card, li.anime-item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        ensureInit()
        val doc = app.get(url, headers = commonHeaders).document

        val title = doc.selectFirst("h1.anime-title, div.baslik h1, h1.entry-title")
            ?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content") ?: "Bilinmeyen"

        val poster = fixUrlNull(
            doc.selectFirst("div.anime-poster img, img.cover")?.attr("src")
                ?: doc.selectFirst("meta[property=og:image]")?.attr("content")
        )

        val description = doc.selectFirst("div.anime-desc, p.description, div.ozet")
            ?.text()?.trim()

        val tags = doc.select("a[href*='tur'], a[href*='genre'], div.genres a").map { it.text().trim() }

        val episodes = doc.select("div.episode-list a, ul.bolumler li a, div.bolum-list a")
            .mapNotNull { el ->
                val epUrl = fixUrlNull(el.attr("href")) ?: return@mapNotNull null
                val epText = el.text().trim()
                val epNum = Regex("""(\d+)""").find(epText)?.groupValues?.get(1)?.toIntOrNull()
                newEpisode(epUrl) {
                    name = epText.ifBlank { "Bölüm $epNum" }
                    episode = epNum
                }
            }.reversed()

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        ensureInit()
        val doc = app.get(data, headers = commonHeaders).document

        // iframe embed'ler
        doc.select("iframe[src], iframe[data-src], div[data-video]").forEach { el ->
            val src = fixUrlNull(
                el.attr("src").takeIf { it.isNotBlank() }
                    ?: el.attr("data-src").takeIf { it.isNotBlank() }
                    ?: el.attr("data-video")
            ) ?: return@forEach
            loadExtractor(src, mainUrl, subtitleCallback, callback)
        }

        // Script içindeki video URL'leri
        val scriptContent = doc.select("script").joinToString("\n") { it.data() }
        Regex("""(?:file|url|source)\s*[=:]\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""")
            .findAll(scriptContent)
            .forEach { match ->
                val videoUrl = match.groupValues[1].trim()
                callback(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = videoUrl,
                        type = if (videoUrl.contains("m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    )
                )
            }

        return true
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = selectFirst("a") ?: return null
        val title = selectFirst("div.title, h3, span.isim, div.name")?.text()?.trim()
            ?: a.attr("title").takeIf { it.isNotBlank() } ?: return null
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

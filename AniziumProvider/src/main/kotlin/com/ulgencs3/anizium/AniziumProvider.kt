package com.ulgencs3.anizium

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

/**
 * Anizium Sağlayıcısı
 *
 * Site: https://anizium.co
 * Yapı: Modern PHP/Next.js hibrit platform
 * Özellik: 4K (2160p) video desteği
 * Kazıma: API + HTML hibrit
 */
class AniziumProvider : MainAPI() {

    override var mainUrl = "https://anizium.co"
    override var name = "Anizium"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasSearch = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to mainUrl,
        "Accept" to "application/json, text/html, */*"
    )

    override suspend fun init() {
        try {
            val config = app.get(
                "https://raw.githubusercontent.com/ulgenzade/ulgencs3/master/domains.json"
            ).text
            AppUtils.parseJson<Map<String, String>>(config)["anizium"]
                ?.takeIf { it.isNotBlank() }?.let { mainUrl = it }
        } catch (e: Exception) { }
    }

    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")

    override val mainPage = mainPageOf(
        "content_type=anime&sort=last_episode" to "Son Bölümler",
        "content_type=anime&sort=popular"      to "Popüler Animeler",
        "content_type=anime&sort=rating"       to "En Yüksek Puanlı",
        "content_type=anime&quality=4k"        to "4K Animeler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Önce API dene
        val apiResp = runCatching {
            app.get(
                "$mainUrl/api/contents?${request.data}&page=$page",
                headers = commonHeaders
            ).parsedSafe<AniziumApiResp>()
        }.getOrNull()

        val items = if (apiResp?.data != null) {
            apiResp.data.mapNotNull { it.toSearchResponse() }
        } else {
            // Fallback: HTML kazıma
            val doc = app.get(
                "$mainUrl/anime-listesi?${request.data}&sayfa=$page",
                headers = commonHeaders
            ).document
            doc.select("div.anime-card, article.content-item").mapNotNull { it.toSearchResult() }
        }

        return newHomePageResponse(HomePageList(request.name, items), hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val apiResp = runCatching {
            app.get(
                "$mainUrl/api/search?q=${query.encodeUrl()}&content_type=anime",
                headers = commonHeaders
            ).parsedSafe<AniziumApiResp>()
        }.getOrNull()

        if (apiResp?.data != null) return apiResp.data.mapNotNull { it.toSearchResponse() }

        val doc = app.get("$mainUrl/arama?q=${query.encodeUrl()}", headers = commonHeaders).document
        return doc.select("div.anime-card, article.content-item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = commonHeaders).document

        val title = doc.selectFirst("h1.content-title, h2.anime-title")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content") ?: "Bilinmeyen"
        val poster = fixUrlNull(
            doc.selectFirst("div.content-poster img, img.anime-poster")?.attr("src")
                ?: doc.selectFirst("meta[property=og:image]")?.attr("content")
        )
        val description = doc.selectFirst("div.content-desc, p.anime-desc")?.text()?.trim()
        val tags = doc.select("a.genre-tag, span.tag").map { it.text().trim() }

        // 4K badge kontrolü
        val has4K = doc.selectFirst("span.quality-badge, div.quality")
            ?.text()?.contains("4K", ignoreCase = true) == true

        val episodes = doc.select("div.episode-list a, ul.bolumler li a").mapNotNull { el ->
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
            this.tags = tags + if (has4K) listOf("4K") else emptyList()
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, headers = commonHeaders).document

        // 4K linkleri önce ara
        doc.select("source[src]").forEach { source ->
            val src = fixUrlNull(source.attr("src")) ?: return@forEach
            val label = source.attr("label").uppercase()
            val quality = when {
                label.contains("4K") || label.contains("2160") -> Qualities.UHD_4K.value
                label.contains("1080") -> Qualities.P1080.value
                label.contains("720")  -> Qualities.P720.value
                label.contains("480")  -> Qualities.P480.value
                else                   -> Qualities.Unknown.value
            }
            callback(
                newExtractorLink(
                    source = name,
                    name = "$name [$label]",
                    url = src,
                    type = if (src.contains("m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) { this.quality = quality }
            )
        }

        // iframe embed'ler
        doc.select("iframe[src], iframe[data-src]").forEach { iframe ->
            val src = fixUrlNull(
                iframe.attr("src").takeIf { it.isNotBlank() } ?: iframe.attr("data-src")
            ) ?: return@forEach
            loadExtractor(src, mainUrl, subtitleCallback, callback)
        }

        return true
    }

    // -------------------------------------------------------------------------
    // Veri Modelleri
    // -------------------------------------------------------------------------

    data class AniziumApiResp(val data: List<AniziumItem>? = null)
    data class AniziumItem(
        val id: Int? = null,
        val title: String? = null,
        val name: String? = null,
        val poster: String? = null,
        val slug: String? = null
    ) {
        fun toSearchResponse(): SearchResponse? {
            val t = title ?: name ?: return null
            val url = "https://anizium.co/anime/${slug ?: id ?: return null}"
            return newAnimeSearchResponse(t, url, TvType.Anime) {
                this.posterUrl = poster
            }
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = selectFirst("a") ?: return null
        val title = selectFirst("div.title, h3, span.anime-title")?.text()?.trim()
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

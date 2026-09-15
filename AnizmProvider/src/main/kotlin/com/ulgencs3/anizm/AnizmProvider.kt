package com.ulgencs3.anizm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

/**
 * Anizm Sağlayıcısı
 *
 * Site: https://anizm.net
 * Yapı: PHP tabanlı HTML sitesi
 * Kazıma: Jsoup DOM kazıma
 * Not: Cloudflare koruması var — User-Agent ile geçilebilir
 */
class AnizmProvider : MainAPI() {

    override var mainUrl = "https://anizm.net"
    override var name = "Anizm"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasSearch = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to mainUrl,
        "Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.8"
    )

    override suspend fun init() {
        try {
            val config = app.get(
                "https://raw.githubusercontent.com/ulgenzade/ulgencs3/master/domains.json"
            ).text
            AppUtils.parseJson<Map<String, String>>(config)["anizm"]
                ?.takeIf { it.isNotBlank() }?.let { mainUrl = it }
        } catch (e: Exception) { }
    }

    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")

    override val mainPage = mainPageOf(
        "$mainUrl/anime-listesi/?filtre=yeni-eklenenler&sayfa=" to "Yeni Eklenenler",
        "$mainUrl/anime-listesi/?filtre=puan&sayfa="           to "En Yüksek Puanlılar",
        "$mainUrl/anime-listesi/?filtre=izlenme&sayfa="        to "En Çok İzlenenler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get("${request.data}$page", headers = commonHeaders).document
        val items = doc.select("div.animeCard, li.listItem").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(HomePageList(request.name, items), hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get(
            "$mainUrl/arama/?q=${query.encodeUrl()}",
            headers = commonHeaders
        ).document
        return doc.select("div.animeCard, li.listItem").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = commonHeaders).document

        val title = doc.selectFirst("h1.animeTitle, h2.animeName")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content") ?: "Bilinmeyen"
        val poster = fixUrlNull(
            doc.selectFirst("div.animePoster img, img.animeCover")?.attr("src")
                ?: doc.selectFirst("meta[property=og:image]")?.attr("content")
        )
        val description = doc.selectFirst("p.animeDesc, div.animeDescription")?.text()?.trim()
        val tags = doc.select("div.animeGenres a, span.genre").map { it.text().trim() }

        val episodes = doc.select("ul.episodeList li a, div.episodeItem a").mapNotNull { el ->
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
        val doc = app.get(data, headers = commonHeaders).document

        // iframe embed'leri
        doc.select("iframe[src], iframe[data-src]").forEach { iframe ->
            val src = fixUrlNull(
                iframe.attr("src").takeIf { it.isNotBlank() } ?: iframe.attr("data-src")
            ) ?: return@forEach
            loadExtractor(src, mainUrl, subtitleCallback, callback)
        }

        // Doğrudan video bağlantıları
        doc.select("source[src]").forEach { source ->
            val src = fixUrlNull(source.attr("src")) ?: return@forEach
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

        return true
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = selectFirst("a") ?: return null
        val title = selectFirst("div.animeName, span.title, h3")?.text()?.trim()
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

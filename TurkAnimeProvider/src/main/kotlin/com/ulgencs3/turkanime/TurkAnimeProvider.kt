package com.ulgencs3.turkanime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

import org.jsoup.nodes.Element

/**
 * TürkAnime TV Sağlayıcısı
 *
 * Site: https://www.turkanime.tv
 * Yapı: PHP + Bootstrap/jQuery tabanlı klasik HTML sitesi
 * Kazıma: Jsoup ile DOM kazıma, AJAX endpoint'lerden içerik çekme
 * Video: Çok sayıda farklı embed desteklenir (Streamtape, Vidmoly, vb.)
 */
class TurkAnimeProvider : MainAPI() {

    // domains.json'dan dinamik çekme — fallback olarak statik URL
    override var mainUrl = "https://www.turkanime.tv"
    override var name = "Türk Anime TV"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasSearch = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    // -------------------------------------------------------------------------
    // Ortak yardımcılar
    // -------------------------------------------------------------------------

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Safari/537.36",
        "Referer" to mainUrl
    )

    /** domains.json'dan güncel domaini çek, başarısızsa fallback kullan */
    override suspend fun init() {
        try {
            val configText = app.get(
                "https://raw.githubusercontent.com/ulgenzade/ulgencs3/master/domains.json"
            ).text
            val dynamic = AppUtils.parseJson<Map<String, String>>(configText)
            dynamic["turkanime"]?.takeIf { it.isNotBlank() }?.let {
                mainUrl = it
            }
        } catch (e: Exception) {
            // Ağ hatası → fallback URL devrede
        }
    }

    // -------------------------------------------------------------------------
    // Ana Sayfa
    // -------------------------------------------------------------------------

    override val mainPage = mainPageOf(
        "$mainUrl/ajax/sezonlukanime"         to "2026 Yaz Sezonu",
        "$mainUrl/ajax/yenieklenenanime"       to "Yeni Bölümler",
        "$mainUrl/ajax/yenieklenenseriler"     to "Yeni Eklenen Animeler",
        "$mainUrl/ajax/rankagore"              to "Popüler Animeler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Bu endpoint'ler sayfalama desteklemiyor; sadece ilk sayfada çek
        if (page > 1) return newHomePageResponse(emptyList())

        val doc = app.get(request.data, headers = commonHeaders).document
        val items = doc.select("div.panel-visible, div.sezonluk-item, a.top-airing-item")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(request.name, items, isHorizontalImages = true),
            hasNext = false
        )
    }

    // -------------------------------------------------------------------------
    // Arama
    // -------------------------------------------------------------------------

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.post(
            "$mainUrl/arama",
            headers = commonHeaders,
            data = mapOf("arama" to query)
        ).document

        return doc.select("div.panel-visible, div.col-md-2").mapNotNull { el ->
            el.toSearchResult()
        }
    }

    // -------------------------------------------------------------------------
    // Detay Sayfası (Sezon / Bölüm listesi)
    // -------------------------------------------------------------------------

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = commonHeaders).document

        val title = doc.selectFirst("h2.panel-title, div.anime-baslik")
            ?.text()?.trim() ?: "Bilinmeyen Anime"

        val poster = fixUrlNull(
            doc.selectFirst("img.anime-afis, div.anime-poster img")?.attr("src")
                ?: doc.selectFirst("meta[property=og:image]")?.attr("content")
        )

        val description = doc.selectFirst("div.anime-ozet, p.ozet")?.text()?.trim()

        val tags = doc.select("a[href*='/tur/']").map { it.text().trim() }

        // Bölüm listesi
        val episodes = doc.select("div.panel-visible a[href*='/video/']").mapNotNull { el ->
            val epUrl = fixUrlNull(el.attr("href")) ?: return@mapNotNull null
            val epTitle = el.selectFirst("div.panel-title")?.text()?.trim()
                ?: el.text().trim()

            // Bölüm numarasını başlıktan çıkarmaya çalış
            val epNum = Regex("""(\d+)\.\s*[Bb]ölüm""").find(epTitle)
                ?.groupValues?.get(1)?.toIntOrNull()

            newEpisode(epUrl) {
                name = epTitle
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

    // -------------------------------------------------------------------------
    // Video Linkleri
    // -------------------------------------------------------------------------

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, headers = commonHeaders).document

        // Sayfadaki tüm iframe / video embed kaynaklarını bul
        val iframeSources = doc.select("iframe[src], iframe[data-src]").mapNotNull { iframe ->
            fixUrlNull(
                iframe.attr("src").takeIf { it.isNotBlank() }
                    ?: iframe.attr("data-src")
            )
        }

        // Gömülü video bağlantıları — data-* attribute'larından
        val dataSources = doc.select("[data-video], [data-src*='player'], [data-src*='embed']")
            .mapNotNull { fixUrlNull(it.attr("data-video").takeIf { v -> v.isNotBlank() } ?: it.attr("data-src")) }

        val allSources = (iframeSources + dataSources).distinct()

        if (allSources.isEmpty()) {
            // Fallback: doğrudan script içindeki URL'leri ara
            val scriptContent = doc.select("script").joinToString("\n") { it.data() }
            Regex("""(?:file|src)\s*:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""")
                .findAll(scriptContent)
                .forEach { match ->
                    val videoUrl = match.groupValues[1]
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

        // Her kaynağı mevcut extractor sistemiyle çöz
        allSources.forEach { src ->
            loadExtractor(src, mainUrl, subtitleCallback, callback)
        }

        return true
    }

    // -------------------------------------------------------------------------
    // Yardımcı dönüştürücüler
    // -------------------------------------------------------------------------

    private fun Element.toSearchResult(): SearchResponse? {
        // top-airing-item (ana sayfa slider)
        val topAiring = this.hasClass("top-airing-item")
        if (topAiring) {
            val title = attr("data-title").takeIf { it.isNotBlank() }
                ?: selectFirst("span.top-airing-title")?.text()?.trim()
                ?: return null
            val url = fixUrlNull(attr("href")) ?: return null
            val poster = fixUrlNull(
                selectFirst("img")?.attr("src")?.takeIf { !it.contains("base64") }
                    ?: selectFirst("img")?.attr("data-src")
            )
            return newAnimeSearchResponse(title, url, TvType.Anime) {
                this.posterUrl = poster
            }
        }

        // Panel kartları
        val titleEl = selectFirst("div.panel-title a, h3 a, a.anime-adi")
            ?: selectFirst("a") ?: return null
        val title = titleEl.text().trim().takeIf { it.isNotBlank() } ?: return null
        val url = fixUrlNull(titleEl.attr("href") ?: attr("href")) ?: return null
        val poster = fixUrlNull(
            selectFirst("img")?.attr("data-src")?.takeIf { !it.contains("base64") }
                ?: selectFirst("img")?.attr("src")
        )

        return newAnimeSearchResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
        }
    }
}

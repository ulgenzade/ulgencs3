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
 * Korumalar: CloudflareKiller ile otomatik bypass
 * Özellikler: Tam kapasite 26 kategori, AJAX & Canlı Arama, Zengin Video Oynatıcılar
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
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
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
    // Ana Sayfa - Zenginleştirilmiş Kategoriler (26 Kategori)
    // -------------------------------------------------------------------------

    override val mainPage = mainPageOf(
        "/anime-listesi/?filtre=yeni-eklenenler&sayfa=" to "Son Eklenen Bölümler",
        "/anime-listesi/?filtre=izlenme&sayfa="        to "Popüler / En Çok İzlenenler",
        "/anime-listesi/?filtre=puan&sayfa="           to "En Yüksek Puanlılar",
        "/anime-listesi/?durum=devam-ediyor&sayfa="    to "Devam Eden Animeler",
        "/anime-listesi/?durum=tamamlandi&sayfa="      to "Tamamlanan Animeler",
        "/anime-listesi/?tur=film&sayfa="              to "Anime Filmleri",
        "/kategoriler/34?sayfa="                       to "Shounen",
        "/kategoriler/2?sayfa="                        to "Aksiyon",
        "/kategoriler/1?sayfa="                        to "Macera",
        "/kategoriler/13?sayfa="                       to "Fantastik",
        "/kategoriler/15?sayfa="                       to "Büyü",
        "/kategoriler/4?sayfa="                        to "Komedi",
        "/kategoriler/51?sayfa="                       to "Dram",
        "/kategoriler/23?sayfa="                       to "Romantizm",
        "/kategoriler/25?sayfa="                       to "Bilim Kurgu",
        "/kategoriler/27?sayfa="                       to "Gizem & Dedektif",
        "/kategoriler/29?sayfa="                       to "Psikolojik",
        "/kategoriler/30?sayfa="                       to "Dövüş Sanatları",
        "/kategoriler/35?sayfa="                       to "Seinen",
        "/kategoriler/50?sayfa="                       to "Isekai",
        "/kategoriler/37?sayfa="                       to "Doğaüstü Güçler",
        "/kategoriler/40?sayfa="                       to "Yaşamdan Kesitler",
        "/kategoriler/42?sayfa="                       to "Okul",
        "/kategoriler/43?sayfa="                       to "Korku & Gerilim",
        "/kategoriler/7?sayfa="                        to "Askeri",
        "/kategoriler/45?sayfa="                       to "Ecchi"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureInit()
        val targetUrl = if (request.data.startsWith("http")) "${request.data}$page" else "$mainUrl${request.data}$page"
        val doc = app.get(targetUrl, headers = commonHeaders, interceptor = cfInterceptor).document

        val items = doc.select(
            "div.animeCard, li.listItem, div.poster-item, div.anizmCard, div.animeListCard, div.media-item, article.animeItem"
        ).mapNotNull { it.toSearchResult() }.distinctBy { it.url }

        return newHomePageResponse(HomePageList(request.name, items), hasNext = items.isNotEmpty())
    }

    // -------------------------------------------------------------------------
    // Arama - Gelişmiş Çok Aşamalı Fallback
    // -------------------------------------------------------------------------

    override suspend fun search(query: String): List<SearchResponse> {
        ensureInit()
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        val results = mutableListOf<SearchResponse>()

        // 1. Resmi AJAX Arama İsteği (POST /request)
        runCatching {
            val response = app.post(
                "$mainUrl/request",
                headers = commonHeaders + mapOf(
                    "X-Requested-With" to "XMLHttpRequest",
                    "Content-Type" to "application/x-www-form-urlencoded"
                ),
                data = mapOf("action" to "search", "value" to trimmed),
                interceptor = cfInterceptor
            )
            val doc = response.document
            val found = doc.select(
                "a.animeTitleLink, div.anizmLinkWrapper, div.animeCard, a[href*='/anime/'], div.searchResultItem, li.listItem"
            ).mapNotNull { it.toSearchResult() }
            results.addAll(found)
        }

        if (results.isNotEmpty()) {
            return results.distinctBy { it.url }
        }

        // 2. /page/search Endpoint Fallback
        runCatching {
            val doc = app.get(
                "$mainUrl/page/search?value=${trimmed.encodeUrl()}&page=1",
                headers = commonHeaders + mapOf("X-Requested-With" to "XMLHttpRequest"),
                interceptor = cfInterceptor
            ).document
            val found = doc.select(
                "a.animeTitleLink, div.anizmLinkWrapper, div.animeCard, a[href*='/anime/'], div.searchResultItem, li.listItem"
            ).mapNotNull { it.toSearchResult() }
            results.addAll(found)
        }

        if (results.isNotEmpty()) {
            return results.distinctBy { it.url }
        }

        // 3. Liste İçi Filtreleme Fallback (/anime-listesi/?ara=...)
        runCatching {
            val doc = app.get(
                "$mainUrl/anime-listesi/?ara=${trimmed.encodeUrl()}",
                headers = commonHeaders,
                interceptor = cfInterceptor
            ).document
            val found = doc.select(
                "div.animeCard, li.listItem, div.poster-item, div.anizmCard, div.animeListCard, article.animeItem"
            ).mapNotNull { it.toSearchResult() }
            results.addAll(found)
        }

        return results.distinctBy { it.url }
    }

    // -------------------------------------------------------------------------
    // Detay & Bölüm Listesi
    // -------------------------------------------------------------------------

    override suspend fun load(url: String): LoadResponse {
        ensureInit()
        val doc = app.get(url, headers = commonHeaders, interceptor = cfInterceptor).document

        val title = doc.selectFirst("h1.animeTitle, h2.animeName, h1.title, div.animeInfo h1, h1")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: "Bilinmeyen Anime"

        val poster = fixUrlNull(
            doc.selectFirst("div.animePoster img, img.animeCover, div.poster img, div.animeImage img, img.cover")?.attr("data-src")
                ?: doc.selectFirst("div.animePoster img, img.animeCover, div.poster img, div.animeImage img, img.cover")?.attr("src")
                ?: doc.selectFirst("meta[property=og:image]")?.attr("content")
        )

        val description = doc.selectFirst(
            "p.animeDesc, div.animeDescription, div.summary, div.animeInfo p, div.ozet, p.description"
        )?.text()?.trim()

        val tags = doc.select(
            "div.animeGenres a, span.genre, a[href*='kategoriler'], a[href*='tur'], div.tags a"
        ).map { it.text().trim() }.filter { it.isNotBlank() }

        // Bölüm Listesi Seçicileri (Detay sayfası butonları ve ızgarası)
        val rawEpisodes = doc.select(
            "div.episodeListTabContent a[href*='-bolum-izle'], div.animeEpisodesSquareListDetay a[href*='-bolum-izle'], " +
            "ul.episodeList li a, div.episodeItem a, a[href*='-bolum-izle'], a[href*='-bolum'], div.bolumler a, ul.bolum-listesi li a"
        ).mapNotNull { el ->
            val epUrl = fixUrlNull(el.attr("href")) ?: return@mapNotNull null
            val epText = el.text().trim().takeIf { it.isNotBlank() } ?: el.attr("title").trim()
            val epNum = Regex("""(\d+)""").find(epText)?.groupValues?.get(1)?.toIntOrNull()
            val cleanText = epText.replace(Regex("""^\s*\d+\.\s*Bölüm\s*[-–:]*\s*"""), "").trim()
            val finalName = cleanText.takeIf { it.isNotBlank() && !it.equals("Bölüm", ignoreCase = true) }

            newEpisode(epUrl) {
                this.name = finalName
                this.episode = epNum
                this.season = 1
                this.posterUrl = poster
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
    // Video Linkleri & Oynatıcı Çıkarıcı (Aincrad, Beta, Vidmoly, UQload, Sistenn...)
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

        // 1. Sayfadaki iframe embed'leri
        doc.select("iframe[src], iframe[data-src]").forEach { iframe ->
            val src = fixUrlNull(
                iframe.attr("src").takeIf { it.isNotBlank() } ?: iframe.attr("data-src")
            ) ?: return@forEach
            if (!src.contains("a-ads.com") && !src.contains("adservice") && extractedUrls.add(src)) {
                loadExtractor(src, mainUrl, subtitleCallback, callback)
            }
        }

        // 2. Alternatif Oynatıcı Butonları (Player Buttons)
        doc.select(
            "a.videoPlayerButtons, button.videoPlayerButtons, div.fansub-item, ul.nav-tabs li a, " +
            "button[data-embed], a[data-frame], div[data-src], a[data-url], a[data-id], button[data-id]"
        ).forEach { el ->
            val directSrc = fixUrlNull(
                el.attr("data-embed").takeIf { it.isNotBlank() }
                    ?: el.attr("data-frame").takeIf { it.isNotBlank() }
                    ?: el.attr("data-src").takeIf { it.isNotBlank() }
                    ?: el.attr("data-url").takeIf { it.isNotBlank() }
            )

            if (directSrc != null && extractedUrls.add(directSrc)) {
                loadExtractor(directSrc, mainUrl, subtitleCallback, callback)
            }

            // data-id ve data-type ile AJAX player isteği (/ajax/player)
            val videoId = el.attr("data-id").takeIf { it.isNotBlank() }
            val playerType = el.attr("data-type").takeIf { it.isNotBlank() }
            if (videoId != null && playerType != null) {
                runCatching {
                    val resp = app.post(
                        "$mainUrl/ajax/player",
                        headers = commonHeaders + mapOf("X-Requested-With" to "XMLHttpRequest"),
                        data = mapOf("id" to videoId, "type" to playerType),
                        interceptor = cfInterceptor
                    ).text
                    Regex("""src=["'](https?://[^"']+)["']""").findAll(resp).forEach { match ->
                        val embedUrl = match.groupValues[1]
                        if (extractedUrls.add(embedUrl)) {
                            loadExtractor(embedUrl, mainUrl, subtitleCallback, callback)
                        }
                    }
                }
            }
        }

        // 3. Doğrudan video bağlantıları & m3u8 kaynakları
        doc.select("source[src]").forEach { source ->
            val src = fixUrlNull(source.attr("src")) ?: return@forEach
            if (extractedUrls.add(src)) {
                val type = source.attr("type")
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name [Direct]",
                        url = src,
                        type = if (type.contains("mpegurl") || src.contains("m3u8"))
                            ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    )
                )
            }
        }

        // 4. Script içindeki video linkleri ve gömülü embed URL'leri
        val scriptContent = doc.select("script").joinToString("\n") { it.data() }

        // Popüler sağlayıcıların embed URL'leri (Vidmoly, Sibnet, Dood, UQload, Voe, Abyss vb.)
        val embedDomains = listOf(
            "vidmoly", "sibnet", "dood", "streamtape", "uqload", "voe", "yourupload",
            "ok.ru", "odnoklassniki", "myvi", "abyss", "mp4upload", "fembed", "mixdrop",
            "drive.google", "aincrad"
        )
        val domainPattern = embedDomains.joinToString("|")
        Regex("""https?://[^\s"'<>]*(?:$domainPattern)[^\s"'<>]*""").findAll(scriptContent).forEach { match ->
            val matchedUrl = match.value
            if (extractedUrls.add(matchedUrl)) {
                loadExtractor(matchedUrl, mainUrl, subtitleCallback, callback)
            }
        }

        // Script içi doğrudan m3u8 veya mp4 dosyaları
        Regex("""(?:file|source|src|videoUrl)\s*:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""")
            .findAll(scriptContent)
            .forEach { match ->
                val videoUrl = match.groupValues[1]
                if (extractedUrls.add(videoUrl)) {
                    callback(
                        newExtractorLink(
                            source = name,
                            name = "$name [Stream]",
                            url = videoUrl,
                            type = if (videoUrl.contains("m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        )
                    )
                }
            }

        return true
    }

    // -------------------------------------------------------------------------
    // Arama & Liste Elemanı Ayrıştırıcı
    // -------------------------------------------------------------------------

    private fun Element.toSearchResult(): SearchResponse? {
        val a = if (tagName() == "a") this else selectFirst("a") ?: return null
        val title = selectFirst("div.animeName, span.title, h3, div.title, .anizmEpisodeButton, p.animeTitle")?.text()?.trim()
            ?: a.attr("title").takeIf { it.isNotBlank() }
            ?: a.text().trim().takeIf { it.isNotBlank() && it.length > 2 }
            ?: return null

        val rawHref = a.attr("href")
        val url = fixUrlNull(rawHref) ?: return null

        // Bölüm izleme linki geldiyse ana anime detay linkine dönüştür
        val finalUrl = if (url.contains("-bolum-izle")) {
            url.replace(Regex("""-\d+-bolum-izle.*$"""), "")
        } else {
            url
        }

        val poster = fixUrlNull(
            selectFirst("img")?.attr("data-src")?.takeIf { !it.contains("base64") && it.isNotBlank() }
                ?: selectFirst("img")?.attr("src")?.takeIf { !it.contains("base64") && it.isNotBlank() }
        )

        return newAnimeSearchResponse(title, finalUrl, TvType.Anime) {
            this.posterUrl = poster
        }
    }
}

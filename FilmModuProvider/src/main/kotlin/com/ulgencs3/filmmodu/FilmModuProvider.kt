package com.ulgencs3.filmmodu

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

/**
 * FilmModu Sağlayıcısı
 *
 * Site: https://www.filmmodu.one
 * İçerik: Türkçe Dublaj ve Altyazılı Filmler, 4K filmler, Yerli filmler
 * Oynatıcı: Doğrudan M3U8 akışları ve Türkçe altyazı desteği
 */
class FilmModuProvider : MainAPI() {

    override var mainUrl = "https://www.filmmodu.one"
    override var name = "FilmModu"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie)

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
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
            AppUtils.parseJson<Map<String, String>>(config)["filmmodu"]
                ?.takeIf { it.isNotBlank() }?.let { mainUrl = it }
        } catch (_: Exception) { }
    }

    override val mainPage = mainPageOf(
        "/hd-film-kategori/4k-film-izle"         to "4K",
        "/hd-film-kategori/aile-filmleri"        to "Aile",
        "/hd-film-kategori/aksiyon"              to "Aksiyon",
        "/hd-film-kategori/animasyon"            to "Animasyon",
        "/hd-film-kategori/bilim-kurgu-filmleri" to "Bilim-Kurgu",
        "/hd-film-kategori/dram-filmleri"        to "Dram",
        "/hd-film-kategori/fantastik-filmler"    to "Fantastik",
        "/hd-film-kategori/gerilim"              to "Gerilim",
        "/hd-film-kategori/gizem-filmleri"       to "Gizem",
        "/hd-film-kategori/hd-komedi-filmleri"   to "Komedi",
        "/hd-film-kategori/korku-filmleri"       to "Korku",
        "/hd-film-kategori/kult-filmler-izle"    to "Kült Filmler",
        "/hd-film-kategori/macera-filmleri"      to "Macera",
        "/hd-film-kategori/odullu-filmler-izle"  to "Oscar Ödüllü",
        "/hd-film-kategori/romantik-filmler"     to "Romantik",
        "/hd-film-kategori/suc-filmleri"         to "Suç",
        "/hd-film-kategori/tavsiye-filmler"      to "Tavsiye Filmler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureInit()
        val targetUrl = if (request.data.startsWith("http")) request.data else "$mainUrl${request.data}"
        val doc = app.get("$targetUrl?page=$page", headers = commonHeaders).document
        val home = doc.select("div.movie").mapNotNull { it.toMainPageResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val a = selectFirst("a") ?: return null
        val title = a.text().trim().takeIf { it.isNotBlank() } ?: a.attr("title").takeIf { it.isNotBlank() } ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val posterUrl = fixUrlNull(
            selectFirst("picture img")?.attr("data-src")
                ?: selectFirst("img")?.attr("data-src")
                ?: selectFirst("img")?.attr("src")
        )
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureInit()
        val doc = app.get("$mainUrl/film-ara?term=$query", headers = commonHeaders).document
        return doc.select("div.movie").mapNotNull { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        ensureInit()
        val doc = app.get(url, headers = commonHeaders).document

        val orgTitle = doc.selectFirst("div.titles h1")?.text()?.trim() ?: return null
        val altTitle = doc.selectFirst("div.titles h2")?.text()?.trim().orEmpty()
        val title = if (altTitle.isNotEmpty()) "$orgTitle - $altTitle" else orgTitle
        val poster = fixUrlNull(doc.selectFirst("img.img-responsive")?.attr("src"))
        val description = doc.selectFirst("p[itemprop='description']")?.text()?.trim()
        val year = doc.selectFirst("span[itemprop='dateCreated']")?.text()?.trim()?.toIntOrNull()
        val tags = doc.select("div.description a[href*='-kategori/']").map { it.text().trim() }
        val actors = doc.select("div.description a[href*='-oyuncu-']").mapNotNull { el ->
            val name = el.selectFirst("span")?.text()?.trim() ?: el.text().trim()
            if (name.isNotBlank()) Actor(name) else null
        }
        val trailer = doc.selectFirst("div.container iframe")?.attr("src")

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
            addActors(actors)
            addTrailer(trailer)
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

        doc.select("div.alternates a").forEach { alternate ->
            val altLink = fixUrlNull(alternate.attr("href")) ?: return@forEach
            val altName = alternate.text().trim()
            if (altName.equals("Fragman", ignoreCase = true)) return@forEach

            val altReq = app.get(altLink, headers = commonHeaders)
            val vidId = Regex("""var videoId = '(.*)'""").find(altReq.text)?.groupValues?.get(1) ?: return@forEach
            val vidType = Regex("""var videoType = '(.*)'""").find(altReq.text)?.groupValues?.get(1) ?: return@forEach

            val vidReq = app.get(
                "$mainUrl/get-source?movie_id=$vidId&type=$vidType",
                headers = commonHeaders
            ).parsedSafe<GetSource>() ?: return@forEach

            if (vidReq.subtitle != null) {
                subtitleCallback(
                    SubtitleFile(
                        lang = "Türkçe",
                        url = fixUrl(vidReq.subtitle)
                    )
                )
            }

            vidReq.sources?.forEach { source ->
                callback(
                    newExtractorLink(
                        source = "$name - $altName",
                        name = "$name - $altName",
                        url = fixUrl(source.src),
                        type = ExtractorLinkType.M3U8
                    ) {
                        headers = mapOf("Referer" to "$mainUrl/")
                        quality = getQualityFromName(source.label)
                    }
                )
            }
        }

        return true
    }
}

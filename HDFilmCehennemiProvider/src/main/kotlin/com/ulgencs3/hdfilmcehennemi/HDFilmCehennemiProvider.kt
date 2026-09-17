package com.ulgencs3.hdfilmcehennemi

import android.util.Base64
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.*
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * HDFilmCehennemi Sağlayıcısı
 *
 * Site: https://www.hdfilmcehennemi.nl
 * İçerik: Film ve Diziler, IMDB 7+ arşivi
 * Koruma: CloudflareInterceptor ve yerel deobfuscation çözücü
 */
class HDFilmCehennemiProvider : MainAPI() {

    override var mainUrl = "https://www.hdfilmcehennemi.nl"
    override var name = "HDFilmCehennemi"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override var sequentialMainPage = true
    override var sequentialMainPageDelay = 150L
    override var sequentialMainPageScrollDelay = 150L

    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)
            val doc = Jsoup.parse(response.peekBody(1024 * 1024).string())
            if (doc.html().contains("Just a moment") || response.code == 403 || response.code == 503) {
                return cloudflareKiller.intercept(chain)
            }
            return response
        }
    }

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "*/*",
        "X-Requested-With" to "fetch"
    )

    private var isInitialized = false

    private suspend fun ensureInit() {
        if (isInitialized) return
        isInitialized = true
        try {
            val config = app.get(
                "https://raw.githubusercontent.com/ulgenzade/ulgencs3/master/domains.json"
            ).text
            AppUtils.parseJson<Map<String, String>>(config)["hdfilmcehennemi"]
                ?.takeIf { it.isNotBlank() }?.let { mainUrl = it }
        } catch (_: Exception) { }
    }

    override val mainPage = mainPageOf(
        "/load/page/sayfano/home/"                      to "Yeni Eklenen Filmler",
        "/load/page/sayfano/home-series/"               to "Yeni Eklenen Diziler",
        "/load/page/sayfano/categories/tavsiye-filmler-izle3/" to "Tavsiye Filmler",
        "/load/page/sayfano/imdb7/"                     to "IMDB 7+ Filmler",
        "/load/page/sayfano/mostCommented/"             to "En Çok Yorumlananlar",
        "/load/page/sayfano/mostLiked/"                 to "En Çok Beğenilenler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureInit()
        val path = request.data.replace("sayfano", page.toString())
        val targetUrl = if (path.startsWith("http")) path else "$mainUrl$path"
        val resp = app.get(targetUrl, headers = commonHeaders, referer = "$mainUrl/", interceptor = interceptor)
        val text = resp.text

        if (!text.contains("Sayfa Bulunamadı")) {
            val hdfc = AppUtils.tryParseJson<HDFC>(text)
            val html = hdfc?.html ?: text
            val document = Jsoup.parse(html, mainUrl)
            val home = document.select("a").mapNotNull { it.toSearchResult() }
            return newHomePageResponse(request.name, home)
        }
        return newHomePageResponse(request.name, emptyList())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.attr("title").takeIf { it.isNotBlank() } ?: selectFirst("h4")?.text()?.trim() ?: return null
        val href = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(
            selectFirst("img")?.attr("data-src")
                ?: selectFirst("img")?.attr("src")
        )

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        ensureInit()
        val response = app.get(
            "$mainUrl/search?q=$query",
            headers = mapOf("X-Requested-With" to "fetch"),
            interceptor = interceptor
        ).parsedSafe<Results>() ?: return emptyList()

        val searchResults = mutableListOf<SearchResponse>()
        response.results.forEach { resultHtml ->
            val document = Jsoup.parse(resultHtml, mainUrl)
            val title = document.selectFirst("h4.title, h4")?.text()?.trim() ?: return@forEach
            val href = fixUrlNull(document.selectFirst("a")?.attr("href")) ?: return@forEach
            val posterUrl = fixUrlNull(
                document.selectFirst("img")?.attr("src")
                    ?: document.selectFirst("img")?.attr("data-src")
            )

            searchResults.add(
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = posterUrl?.replace("/thumb/", "/list/")
                }
            )
        }

        return searchResults
    }

    override suspend fun load(url: String): LoadResponse? {
        ensureInit()
        val document = app.get(url, interceptor = interceptor).document

        val title = document.selectFirst("h1.section-title")?.text()?.substringBefore(" izle")?.trim() ?: return null
        val poster = fixUrlNull(document.select("aside.post-info-poster img.lazyload, aside.post-info-poster img").lastOrNull()?.attr("data-src"))
        val tags = document.select("div.post-info-genres a").map { it.text().trim() }
        val year = document.selectFirst("div.post-info-year-country a")?.text()?.trim()?.toIntOrNull()
        val isSeries = document.select("div.seasons, div.seasons-tab-content").isNotEmpty()
        val description = document.selectFirst("article.post-info-content > p, div.post-info-content")?.text()?.trim()
        val actors = document.select("div.post-info-cast a").mapNotNull { el ->
            val name = el.selectFirst("strong")?.text()?.trim() ?: el.text().trim()
            if (name.isNotBlank()) Actor(name, fixUrlNull(el.selectFirst("img")?.attr("data-src"))) else null
        }

        val recommendations = document.select("div.section-slider-container div.slider-slide").mapNotNull {
            val recName = it.selectFirst("a")?.attr("title")?.trim() ?: return@mapNotNull null
            val recHref = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
            val recPoster = fixUrlNull(it.selectFirst("img")?.attr("data-src") ?: it.selectFirst("img")?.attr("src"))
            newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                this.posterUrl = recPoster
            }
        }

        val trailer = document.selectFirst("div.post-info-trailer button")?.attr("data-modal")
            ?.substringAfter("trailer/", "")
            ?.takeIf { it.isNotBlank() }?.let { "https://www.youtube.com/watch?v=$it" }

        return if (isSeries) {
            val episodes = document.select("div.seasons-tab-content a").mapNotNull {
                val epName = it.selectFirst("h4")?.text()?.trim() ?: return@mapNotNull null
                val epHref = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
                val epEpisode = Regex("""(\d+)\.\s*Bölüm""").find(epName)?.groupValues?.get(1)?.toIntOrNull()
                val epSeason = Regex("""(\d+)\.\s*Sezon""").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: 1

                newEpisode(epHref) {
                    name = epName
                    season = epSeason
                    episode = epEpisode
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        }
    }

    private data class DecOp(val name: String, val rotShift: Int = 0)

    private fun decryptLocalUrl(unpackedScript: String): String? {
        try {
            val partsMatch = """\(\[\s*((?:['"][^'"]+['"]\s*,?\s*)+)\]\)""".toRegex().find(unpackedScript)
            val parts = partsMatch?.groupValues?.get(1)?.split(",")?.map {
                it.trim().trim('\'', '"').replace("\\/", "/")
            } ?: return null

            val moduloMatch = """(\d+)\s*%\s*\(i\s*\+\s*(\d+)\)""".toRegex().find(unpackedScript)
            val magicNum = moduloMatch?.groupValues?.get(1)?.toLongOrNull() ?: 399756995L
            val magicOffset = moduloMatch?.groupValues?.get(2)?.toIntOrNull() ?: 5

            val funcBody = unpackedScript.substringAfter("function dc_").substringBefore("function d1x")
            val operations = mutableListOf<Pair<Int, DecOp>>()

            var index = funcBody.indexOf("atob(")
            while (index >= 0) {
                operations.add(Pair(index, DecOp("atob")))
                index = funcBody.indexOf("atob(", index + 1)
            }

            index = funcBody.indexOf("reverse")
            while (index >= 0) {
                operations.add(Pair(index, DecOp("reverse")))
                index = funcBody.indexOf("reverse", index + 1)
            }

            index = funcBody.indexOf("replace")
            while (index >= 0) {
                val block = funcBody.substring(index, minOf(index + 300, funcBody.length))
                var shift = 13
                val rotShiftMatch = """charCodeAt\(0\)\s*\+\s*(\d+)""".toRegex().find(block)
                if (rotShiftMatch != null) {
                    shift = rotShiftMatch.groupValues[1].toInt()
                } else {
                    val rotShiftMatch2 = """o\s*-\s*base\s*([+-])\s*(\d+)""".toRegex().find(block)
                    if (rotShiftMatch2 != null) {
                        val sign = rotShiftMatch2.groupValues[1]
                        val num = rotShiftMatch2.groupValues[2].toInt()
                        shift = if (sign == "-") (26 - num) % 26 else num
                    }
                }
                operations.add(Pair(index, DecOp("rot", shift)))
                index = funcBody.indexOf("replace", index + 1)
            }

            operations.sortBy { it.first }
            var result = parts.joinToString("")

            for (op in operations) {
                when (op.second.name) {
                    "reverse" -> result = result.reversed()
                    "atob" -> {
                        var paddedResult = result
                        while (paddedResult.length % 4 != 0) paddedResult += "="
                        result = String(Base64.decode(paddedResult, Base64.NO_WRAP), Charsets.ISO_8859_1)
                    }
                    "rot" -> {
                        val rotShift = op.second.rotShift
                        val rot = StringBuilder()
                        for (c in result) {
                            if (c in 'a'..'z') {
                                val shifted = c.code + rotShift
                                rot.append(if (shifted > 'z'.code) (shifted - 26).toChar() else shifted.toChar())
                            } else if (c in 'A'..'Z') {
                                val shifted = c.code + rotShift
                                rot.append(if (shifted > 'Z'.code) (shifted - 26).toChar() else shifted.toChar())
                            } else {
                                rot.append(c)
                            }
                        }
                        result = rot.toString()
                    }
                }
            }

            val unmix = StringBuilder()
            for (i in result.indices) {
                val charCode = result[i].code.toLong()
                val decryptedCode = (charCode - (magicNum % (i + magicOffset)) + 256) % 256
                unmix.append(decryptedCode.toInt().toChar())
            }

            return unmix.toString()
        } catch (_: Exception) {
            return null
        }
    }

    private suspend fun invokeLocalSource(
        source: String,
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val script = app.get(url, referer = "$mainUrl/", interceptor = interceptor)
            .document.select("script").find { it.data().contains("sources:") }?.data() ?: return
        val unpackedScript = getAndUnpack(script)
        val decryptedUrl = decryptLocalUrl(unpackedScript) ?: return
        val lastUrl = decryptedUrl.substringAfter("https").let { "https$it" }

        val subData = script.substringAfter("tracks: [").substringBefore("]")
        AppUtils.tryParseJson<List<SubSource>>("[$subData]")?.filter { it.kind == "captions" }?.forEach {
            val subtitleUrl = "$mainUrl${it.file}/"
            subtitleCallback(newSubtitleFile(it.language.toString(), subtitleUrl))
        }

        callback(
            newExtractorLink(
                source = source,
                name = source,
                url = lastUrl,
                type = ExtractorLinkType.M3U8
            ) {
                headers = mapOf("Referer" to "$mainUrl/")
                quality = Qualities.Unknown.value
            }
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        ensureInit()
        val document = app.get(data, interceptor = interceptor).document

        document.select("div.alternative-links").forEach { element ->
            val langCode = element.attr("data-lang").uppercase()
            element.select("button.alternative-link").forEach { button ->
                val source = button.text().replace("(HDrip Xbet)", "").trim() + " $langCode"
                val videoID = button.attr("data-video")
                val apiGet = app.get(
                    "$mainUrl/video/$videoID/",
                    interceptor = interceptor,
                    headers = mapOf("Content-Type" to "application/json", "X-Requested-With" to "fetch"),
                    referer = data
                ).text

                val rawIframe = Regex("""data-src=\\"([^"]+)""").find(apiGet)?.groupValues?.get(1)?.replace("\\", "")
                var iframe = rawIframe ?: return@forEach
                if (iframe.contains("rapidrame")) {
                    iframe = "$mainUrl/rplayer/" + iframe.substringAfter("?rapidrame_id=")
                } else if (iframe.contains("mobi")) {
                    val iframeDoc = Jsoup.parse(apiGet, mainUrl)
                    iframe = fixUrlNull(iframeDoc.selectFirst("iframe")?.attr("data-src")) ?: return@forEach
                }

                invokeLocalSource(source, iframe, subtitleCallback, callback)
            }
        }
        return true
    }

    private data class SubSource(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("kind") val kind: String? = null
    )

    data class Results(
        @JsonProperty("results") val results: List<String> = emptyList()
    )

    data class HDFC(
        @JsonProperty("html") val html: String = "",
    )
}

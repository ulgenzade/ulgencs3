package com.ulgencs3.openanime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.nodes.Element

/**
 * OpenAnime Sağlayıcısı
 *
 * Site: https://openani.me
 * Yapı: Next.js tabanlı SPA
 * Veri Yöntemi: __NEXT_DATA__ JSON parse — DOM kazımadan çok daha stabil
 */
class OpenAnimeProvider : MainAPI() {

    override var mainUrl = "https://openani.me"
    override var name = "OpenAnime"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to mainUrl
    )

    // Next.js build ID — sayfa yüklenince güncellenir
    private var nextBuildId: String? = null
    private var isInitialized = false

    private suspend fun ensureInit() {
        if (isInitialized) return
        isInitialized = true
        try {
            val config = app.get(
                "https://raw.githubusercontent.com/ulgenzade/ulgencs3/master/domains.json"
            ).text
            AppUtils.parseJson<Map<String, String>>(config)["openanime"]
                ?.takeIf { it.isNotBlank() }?.let { mainUrl = it }

            // __NEXT_DATA__'dan buildId çek
            val doc = app.get(mainUrl, headers = commonHeaders).document
            val nextData = doc.selectFirst("script#__NEXT_DATA__")?.data()
            if (nextData != null) {
                nextBuildId = JSONObject(nextData).optString("buildId").takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) { }
    }

    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")

    /** Next.js API URL oluştur */
    private fun nextApiUrl(path: String): String {
        val bid = nextBuildId ?: return "$mainUrl/api$path"
        return "$mainUrl/_next/data/$bid$path.json"
    }

    override val mainPage = mainPageOf(
        "/latest"  to "Son Bölümler",
        "/popular" to "Popüler Animeler",
        "/all"     to "Tüm Animeler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureInit()
        val items = mutableListOf<SearchResponse>()

        try {
            // Next.js data API yolu
            val url = "${nextApiUrl(request.data)}?page=$page"
            val resp = app.get(url, headers = commonHeaders).text
            val json = JSONObject(resp)
            val pageProps = json.optJSONObject("pageProps")
            val animeArray = pageProps?.optJSONArray("animes")
                ?: pageProps?.optJSONArray("items")

            if (animeArray != null) {
                for (i in 0 until animeArray.length()) {
                    val item = animeArray.getJSONObject(i)
                    val title = item.optString("title").takeIf { it.isNotBlank() }
                        ?: item.optString("name") ?: continue
                    val slug = item.optString("slug").takeIf { it.isNotBlank() }
                        ?: item.optInt("id").toString()
                    val poster = fixUrlNull(item.optString("coverImage").takeIf { it.isNotBlank() }
                        ?: item.optString("image"))
                    items.add(newAnimeSearchResponse(title, "$mainUrl/anime/$slug", TvType.Anime) {
                        this.posterUrl = poster
                    })
                }
            }
        } catch (e: Exception) {
            // Fallback: HTML kazıma
            val doc = app.get("$mainUrl${request.data}?page=$page", headers = commonHeaders).document
            items.addAll(doc.select("div.anime-card, article").mapNotNull { it.toSearchResult() })
        }

        return newHomePageResponse(HomePageList(request.name, items), hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val resp = app.get(
                "$mainUrl/api/search?q=${query.encodeUrl()}",
                headers = commonHeaders
            ).text
            val json = JSONObject(resp)
            val results = json.optJSONArray("results") ?: json.optJSONArray("data")
            val items = mutableListOf<SearchResponse>()
            if (results != null) {
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    val title = item.optString("title").takeIf { it.isNotBlank() }
                        ?: item.optString("name") ?: continue
                    val slug = item.optString("slug").takeIf { it.isNotBlank() }
                        ?: item.optInt("id").toString()
                    val poster = fixUrlNull(item.optString("image").takeIf { it.isNotBlank() })
                    items.add(newAnimeSearchResponse(title, "$mainUrl/anime/$slug", TvType.Anime) {
                        this.posterUrl = poster
                    })
                }
            }
            items
        } catch (e: Exception) {
            val doc = app.get("$mainUrl/search?q=${query.encodeUrl()}", headers = commonHeaders).document
            doc.select("div.anime-card, article").mapNotNull { it.toSearchResult() }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val slug = url.substringAfterLast("/")
        val title: String
        val poster: String?
        val description: String?
        val tags: List<String>
        val episodes = mutableListOf<Episode>()

        try {
            val apiUrl = nextApiUrl("/anime/$slug")
            val resp = app.get(apiUrl, headers = commonHeaders).text
            val json = JSONObject(resp)
            val props = json.optJSONObject("pageProps")
            val anime = props?.optJSONObject("anime") ?: props

            title = anime?.optString("title")?.takeIf { it.isNotBlank() }
                ?: anime?.optString("name") ?: "Bilinmeyen"
            poster = fixUrlNull(
                anime?.optString("coverImage")?.takeIf { it.isNotBlank() }
                    ?: anime?.optString("image")
            )
            description = anime?.optString("description")?.takeIf { it.isNotBlank() }
            tags = buildList {
                val genres = anime?.optJSONArray("genres")
                if (genres != null) {
                    for (i in 0 until genres.length()) add(genres.getString(i))
                }
            }

            // Bölümler
            val epsArray = anime?.optJSONArray("episodes")
            if (epsArray != null) {
                for (i in 0 until epsArray.length()) {
                    val ep = epsArray.getJSONObject(i)
                    val epNum = ep.optInt("number", i + 1)
                    val epTitle = ep.optString("title").takeIf { it.isNotBlank() } ?: "Bölüm $epNum"
                    val epSlug = ep.optString("slug").takeIf { it.isNotBlank() } ?: epNum.toString()
                    episodes.add(newEpisode("$mainUrl/anime/$slug/$epSlug") {
                        name = epTitle
                        episode = epNum
                        posterUrl = fixUrlNull(ep.optString("thumbnail"))
                    })
                }
            }
        } catch (e: Exception) {
            // HTML fallback
            val doc = app.get(url, headers = commonHeaders).document
            val t = doc.selectFirst("h1, h2.anime-title")?.text()?.trim() ?: "Bilinmeyen"
            return newAnimeLoadResponse(t, url, TvType.Anime) {
                this.posterUrl = fixUrlNull(doc.selectFirst("img.cover, div.poster img")?.attr("src"))
                this.plot = doc.selectFirst("div.desc, p.description")?.text()?.trim()
            }
        }

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

        // __NEXT_DATA__ içinden video bağlantısı bul
        val nextDataText = doc.selectFirst("script#__NEXT_DATA__")?.data()
        if (nextDataText != null) {
            try {
                val json = JSONObject(nextDataText)
                val props = json.optJSONObject("props")?.optJSONObject("pageProps")
                val videoSources = props?.optJSONArray("sources") ?: props?.optJSONArray("videos")
                if (videoSources != null) {
                    for (i in 0 until videoSources.length()) {
                        val src = videoSources.getJSONObject(i)
                        val srcUrl = src.optString("url").takeIf { it.isNotBlank() } ?: continue
                        val label = src.optString("label", "")
                        val quality = when {
                            label.contains("1080") -> Qualities.P1080.value
                            label.contains("720")  -> Qualities.P720.value
                            label.contains("480")  -> Qualities.P480.value
                            else                   -> Qualities.Unknown.value
                        }
                        callback(
                            newExtractorLink(
                                source = name,
                                name = "$name [$label]",
                                url = srcUrl,
                                type = if (srcUrl.contains("m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            ) { this.quality = quality }
                        )
                    }
                    return true
                }
            } catch (e: Exception) { }
        }

        // Fallback: iframe'ler
        doc.select("iframe[src], iframe[data-src]").forEach { iframe ->
            val src = fixUrlNull(
                iframe.attr("src").takeIf { it.isNotBlank() } ?: iframe.attr("data-src")
            ) ?: return@forEach
            loadExtractor(src, mainUrl, subtitleCallback, callback)
        }

        return true
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = selectFirst("a") ?: return null
        val title = selectFirst("h3, div.title, span.name")?.text()?.trim()
            ?: a.attr("title").takeIf { it.isNotBlank() } ?: return null
        val url = fixUrlNull(a.attr("href")) ?: return null
        val poster = fixUrlNull(
            selectFirst("img")?.attr("src")?.takeIf { !it.contains("base64") }
        )
        return newAnimeSearchResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
        }
    }
}

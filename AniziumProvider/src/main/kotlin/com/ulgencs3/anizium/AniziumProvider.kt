package com.ulgencs3.anizium

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Anizium Sağlayıcısı
 *
 * Site: https://anizium.co
 * Özellik: 4K (2160p), Resmi REST API, Çoklu Altyazı Dilleri ve TR Dublaj desteği
 */
class AniziumProvider : MainAPI() {

    override var mainUrl = "https://anizium.co"
    override var name = "Anizium"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    private val apiHost = "https://api.anizium.co"
    private val tokenKey = "hlxjl1c2w281ax473rt1ofgrvhyjvi"

    private var isInitialized = false

    private suspend fun ensureInit() {
        if (isInitialized) return
        isInitialized = true
        try {
            val config = app.get(
                "https://raw.githubusercontent.com/ulgenzade/ulgencs3/master/domains.json",
                timeout = 5
            ).text
            org.json.JSONObject(config).optString("anizium")
                ?.takeIf { it.isNotBlank() }?.let { mainUrl = it }
        } catch (_: Exception) { }
    }

    private fun getCfControl(): String {
        return try {
            val sdf = SimpleDateFormat("EEEE", Locale.ENGLISH)
            sdf.timeZone = TimeZone.getTimeZone("Europe/Istanbul")
            val weekday = sdf.format(Date()).lowercase()
            val key = "${tokenKey}_$weekday".toByteArray(Charsets.UTF_8)

            val rnd = (1..6).map { ('a'..'z').random() }.joinToString("")
            val payload = "{\"$rnd\":${System.currentTimeMillis()}}".toByteArray(Charsets.UTF_8)

            val res = ByteArray(payload.size)
            for (i in payload.indices) {
                res[i] = (payload[i].toInt() xor key[i % key.size].toInt()).toByte()
            }
            res.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    private fun getApiHeaders(): Map<String, String> {
        return mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Origin" to mainUrl,
            "Referer" to "$mainUrl/",
            "Cf-Control" to getCfControl(),
            "device" to "browser",
            "language" to "tr",
            "site" to "main",
            "Accept" to "application/json, text/plain, */*"
        )
    }

    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")

    // -------------------------------------------------------------------------
    // Ana Sayfa
    // -------------------------------------------------------------------------

    override val mainPage = mainPageOf(
        "last-added" to "Son Eklenen Bölümler",
        "4k"         to "4K Ultra HD Animeler",
        "dub"        to "Türkçe Dublaj Animeler",
        "popular"    to "Popüler Animeler",
        "action"     to "Aksiyon Animeleri",
        "comedy"     to "Komedi Animeleri",
        "drama"      to "Dram Animeleri",
        "romance"    to "Romantizm Animeleri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureInit()
        val pageUrl = if (request.data.startsWith("http")) request.data else "$mainUrl/${request.data}"
        val items = mutableListOf<SearchResponse>()

        if (request.data == "last-added") {
            val res = runCatching {
                app.get("$apiHost/page/last-added-episodes?page=$page", headers = getApiHeaders())
                    .parsedSafe<AniziumLastAddedResp>()
            }.getOrNull()

            res?.page?.data?.forEach { item ->
                val id = item.id ?: return@forEach
                val title = item.name ?: "Anime"
                val poster = item.poster ?: item.banner
                val epNum = item.episode ?: 1
                items.add(newAnimeSearchResponse(title, "$mainUrl/anime/$id", TvType.Anime) {
                    this.posterUrl = fixUrlNull(poster)
                    addDubStatus(DubStatus.Subbed, epNum)
                })
            }
        } else {
            val res = runCatching {
                app.get("$apiHost/page/home", headers = getApiHeaders()).parsedSafe<AniziumHomeResp>()
            }.getOrNull()

            val allPool = mutableListOf<AniziumItem>()
            res?.settlementTop?.let { allPool.addAll(it) }
            res?.settlementMiddle?.let { allPool.addAll(it) }
            res?.settlementLower?.let { allPool.addAll(it) }

            val list = when (request.data) {
                "4k" -> allPool.filter { it.quality?.contains("4k", ignoreCase = true) == true }
                "dub" -> allPool.filter {
                    it.genre?.any { g -> g.name?.contains("Dublaj", ignoreCase = true) == true } == true ||
                    it.soundGroup?.any { s -> s.value?.contains("dub", ignoreCase = true) == true } == true
                }
                "popular" -> res?.settlementTop ?: allPool
                "action" -> allPool.filter { it.genre?.any { g -> g.name?.contains("Aksiyon", ignoreCase = true) == true } == true }
                "comedy" -> allPool.filter { it.genre?.any { g -> g.name?.contains("Komedi", ignoreCase = true) == true } == true }
                "drama" -> allPool.filter { it.genre?.any { g -> g.name?.contains("Dram", ignoreCase = true) == true } == true }
                "romance" -> allPool.filter { it.genre?.any { g -> g.name?.contains("Romantizm", ignoreCase = true) == true } == true }
                else -> allPool
            }.distinctBy { it.id }

            list.forEach { item ->
                val id = item.id ?: return@forEach
                val title = item.name ?: return@forEach
                val poster = item.poster ?: item.banner
                items.add(newAnimeSearchResponse(title, "$mainUrl/anime/$id", TvType.Anime) {
                    this.posterUrl = fixUrlNull(poster)
                })
            }
        }

        // API boş dönerse DOM fallback
        if (items.isEmpty()) {
            val doc = runCatching {
                app.get("$mainUrl/anime-listesi?sayfa=$page", headers = getApiHeaders()).document
            }.getOrNull()

            doc?.select("div.anime-card, article.content-item, div.item")?.mapNotNull { it.toSearchResult() }?.let {
                items.addAll(it)
            }
        }

        return newHomePageResponse(HomePageList(request.name, items), hasNext = items.isNotEmpty())
    }

    // -------------------------------------------------------------------------
    // Arama
    // -------------------------------------------------------------------------

    override suspend fun search(query: String): List<SearchResponse> {
        ensureInit()
        val res = runCatching {
            app.get("$apiHost/page/search?q=${query.encodeUrl()}", headers = getApiHeaders())
                .parsedSafe<AniziumSearchResp>()
        }.getOrNull()

        if (res?.data != null && res.data.isNotEmpty()) {
            return res.data.mapNotNull { item ->
                val id = item.id ?: return@mapNotNull null
                val title = item.name ?: return@mapNotNull null
                newAnimeSearchResponse(title, "$mainUrl/anime/$id", TvType.Anime) {
                    this.posterUrl = fixUrlNull(item.poster ?: item.banner)
                }
            }
        }

        val doc = runCatching {
            app.get("$mainUrl/arama?q=${query.encodeUrl()}", headers = getApiHeaders()).document
        }.getOrNull()

        return doc?.select("div.anime-card, article.content-item, div.item")?.mapNotNull { it.toSearchResult() }
            ?: emptyList()
    }

    // -------------------------------------------------------------------------
    // Detay & Bölümler
    // -------------------------------------------------------------------------

    override suspend fun load(url: String): LoadResponse? {
        ensureInit()
        val animeId = Regex("""(?:/anime/|id=)(\d+)""").find(url)?.groupValues?.get(1)

        if (!animeId.isNullOrEmpty()) {
            val res = runCatching {
                app.get("$apiHost/anime/get?id=$animeId", headers = getApiHeaders()).parsedSafe<AniziumAnimeDetailResp>()
            }.getOrNull()

            val anime = res?.data
            if (anime != null) {
                val title = anime.name ?: "Anime"
                val poster = anime.poster ?: anime.banner
                val overview = anime.overview
                val genres = anime.genres?.mapNotNull { it.name } ?: emptyList()

                val episodes = mutableListOf<Episode>()
                anime.seasons?.forEach { season ->
                    val sNum = season.number ?: 1
                    season.episodes?.forEach { ep ->
                        val epNum = ep.number ?: 1
                        val epName = ep.name?.takeIf { it.isNotBlank() && !it.equals("Bölüm $epNum", ignoreCase = true) }
                        val epUrl = "$mainUrl/watch/$animeId?season=$sNum&episode=$epNum&epId=${ep.id ?: ""}"

                        episodes.add(newEpisode(epUrl) {
                            this.name = epName
                            this.season = sNum
                            this.episode = epNum
                            this.description = ep.overview?.takeIf { it.isNotBlank() }
                            this.posterUrl = fixUrlNull(ep.bannerLink)
                        })
                    }
                }

                return newAnimeLoadResponse(title, url, TvType.Anime) {
                    this.posterUrl = fixUrlNull(poster)
                    this.plot = overview
                    this.tags = genres
                    addEpisodes(DubStatus.Subbed, episodes)
                }
            }
        }

        // DOM Fallback
        val doc = app.get(url, headers = getApiHeaders()).document
        val title = doc.selectFirst("h1.content-title, h2.anime-title, h1")?.text()?.trim() ?: "Anime"
        val poster = fixUrlNull(doc.selectFirst("div.content-poster img, img.anime-poster")?.attr("src"))
        val description = doc.selectFirst("div.content-desc, p.anime-desc")?.text()?.trim()

        val rawEpisodes = doc.select("div.episode-list a, ul.bolumler li a, a[href*='bolum']").mapNotNull { el ->
            val epUrl = fixUrlNull(el.attr("href")) ?: return@mapNotNull null
            val epText = el.text().trim()
            val epNum = Regex("""(\d+)""").find(epText)?.groupValues?.get(1)?.toIntOrNull()
            newEpisode(epUrl) {
                this.name = epText.replace(Regex("""^\d+\.\s*Bölüm\s*[-–:]*\s*"""), "").takeIf { it.isNotBlank() }
                this.episode = epNum
                this.season = 1
            }
        }.distinctBy { it.data }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = description
            addEpisodes(DubStatus.Subbed, rawEpisodes)
        }
    }

    // -------------------------------------------------------------------------
    // Medya Oynatıcıları, Çoklu Altyazı & Dublaj
    // -------------------------------------------------------------------------

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        ensureInit()
        val animeId = Regex("""(?:/watch/|/anime/|id=)(\d+)""").find(data)?.groupValues?.get(1)
        val season = Regex("""season=(\d+)""").find(data)?.groupValues?.get(1) ?: "1"
        val episode = Regex("""episode=(\d+)""").find(data)?.groupValues?.get(1) ?: "1"

        if (!animeId.isNullOrEmpty()) {
            val sourceUrl = "$apiHost/anime/source?id=$animeId&site=main&plan=free&season=$season&episode=$episode&server=1"
            val res = runCatching {
                app.get(sourceUrl, headers = getApiHeaders()).parsedSafe<AniziumSourceResp>()
            }.getOrNull()

            if (res != null && res.success == true) {
                // 1. Çoklu Altyazı Dosyalarını Ekle (Türkçe, İngilizce, Almanca vb.)
                res.subtitles?.forEach { sub ->
                    val file = sub.link?.takeIf { it.isNotBlank() } ?: return@forEach
                    val label = sub.name ?: sub.group ?: "Altyazı"
                    subtitleCallback(newSubtitleFile(label, file))
                }

                // 2. Orijinal ve Türkçe Dublaj Video Akışlarını Ekle (4K, 1080p, 720p)
                res.groups?.forEach { grp ->
                    val isDub = grp.group?.contains("dub", ignoreCase = true) == true ||
                            grp.name?.contains("dublaj", ignoreCase = true) == true
                    val grpName = grp.name ?: if (isDub) "Türkçe Dublaj" else "Japonca"

                    grp.items?.forEach { item ->
                        val link = item.link?.takeIf { it.isNotBlank() } ?: return@forEach
                        val q = item.quality ?: 1080
                        val qualValue = when (q) {
                            2160 -> Qualities.P2160.value
                            1080 -> Qualities.P1080.value
                            720  -> Qualities.P720.value
                            480  -> Qualities.P480.value
                            else -> Qualities.Unknown.value
                        }
                        val qLabel = if (q >= 2160) "4K (2160p)" else "${q}p"

                        callback(
                            newExtractorLink(
                                source = name,
                                name = "$name [$grpName - $qLabel]",
                                url = link,
                                type = if (link.contains("m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            ) {
                                this.quality = qualValue
                                this.headers = mapOf(
                                    "Referer" to "$mainUrl/",
                                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                                )
                            }
                        )
                    }
                }
                return true
            }
        }

        // DOM Fallback
        val doc = app.get(data, headers = getApiHeaders()).document
        doc.select("source[src]").forEach { source ->
            val src = fixUrlNull(source.attr("src")) ?: return@forEach
            callback(
                newExtractorLink(
                    source = name,
                    name = "$name [Direct]",
                    url = src,
                    type = if (src.contains("m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                )
            )
        }

        doc.select("iframe[src]").forEach { iframe ->
            val src = fixUrlNull(iframe.attr("src")) ?: return@forEach
            loadExtractor(src, mainUrl, subtitleCallback, callback)
        }

        return true
    }

    // -------------------------------------------------------------------------
    // Veri Modelleri
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AniziumLastAddedResp(val page: AniziumPageData? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AniziumPageData(val data: List<AniziumItem>? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AniziumHomeResp(
        @JsonProperty("settlement_top") val settlementTop: List<AniziumItem>? = null,
        @JsonProperty("settlement_middle") val settlementMiddle: List<AniziumItem>? = null,
        @JsonProperty("settlement_lower") val settlementLower: List<AniziumItem>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AniziumSearchResp(val data: List<AniziumItem>? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AniziumItem(
        @JsonProperty("ID") val id: String? = null,
        val name: String? = null,
        val poster: String? = null,
        val banner: String? = null,
        val quality: String? = null,
        val episode: Int? = null,
        val overview: String? = null,
        val genre: List<AniziumGenre>? = null,
        @JsonProperty("sound_group") val soundGroup: List<AniziumSoundGroupItem>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AniziumSoundGroupItem(
        val name: String? = null,
        val value: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AniziumAnimeDetailResp(val data: AniziumDetailData? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AniziumDetailData(
        @JsonProperty("ID") val id: String? = null,
        val name: String? = null,
        val poster: String? = null,
        val banner: String? = null,
        val overview: String? = null,
        val genres: List<AniziumGenre>? = null,
        val seasons: List<AniziumSeason>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AniziumGenre(val name: String? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AniziumSeason(
        val number: Int? = null,
        val episodes: List<AniziumEpisodeItem>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AniziumEpisodeItem(
        @JsonProperty("ID") val id: String? = null,
        val name: String? = null,
        val number: Int? = null,
        val overview: String? = null,
        @JsonProperty("banner_link") val bannerLink: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AniziumSourceResp(
        val success: Boolean? = null,
        val subtitles: List<AniziumSubtitle>? = null,
        val groups: List<AniziumSourceGroup>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AniziumSubtitle(
        val group: String? = null,
        val name: String? = null,
        val link: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AniziumSourceGroup(
        val group: String? = null,
        val name: String? = null,
        val items: List<AniziumSourceItem>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AniziumSourceItem(
        val quality: Int? = null,
        val link: String? = null,
        val type: String? = null
    )

    private fun Element.toSearchResult(): SearchResponse? {
        val a = selectFirst("a") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val title = selectFirst("h3, h4, div.title, span.title")?.text()?.trim() ?: a.text().trim()
        val img = selectFirst("img")?.attr("data-src") ?: selectFirst("img")?.attr("src")
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = fixUrlNull(img)
        }
    }
}

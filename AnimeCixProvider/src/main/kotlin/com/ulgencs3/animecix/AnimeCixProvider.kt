package com.ulgencs3.animecix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

/**
 * AnimeCiX Sağlayıcısı
 *
 * Site: https://animecix.tv
 * Yapı: Angular SPA — REST API tabanlı
 * API Base: /api/v1/
 * Auth: İlk yüklemede cookie'den XSRF-TOKEN al
 */
class AnimeCixProvider : MainAPI() {

    override var mainUrl = "https://animecix.tv"
    override var name = "AnimeCiX"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasSearch = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    private val apiUrl = "$mainUrl/api/v1"

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to mainUrl,
        "Accept" to "application/json, text/plain, */*",
        "X-Requested-With" to "XMLHttpRequest"
    )

    // XSRF token — ilk istekten cookie'den alınır
    private var xsrfToken: String? = null

    override suspend fun init() {
        try {
            // domains.json'dan güncel domain çek
            val config = app.get(
                "https://raw.githubusercontent.com/ulgenzade/ulgencs3/master/domains.json"
            ).text
            AppUtils.parseJson<Map<String, String>>(config)["animecix"]
                ?.takeIf { it.isNotBlank() }?.let { mainUrl = it }

            // XSRF token çek
            val resp = app.get(mainUrl, headers = commonHeaders)
            xsrfToken = resp.cookies["XSRF-TOKEN"]
        } catch (e: Exception) { }
    }

    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")

    private fun authHeaders(): Map<String, String> {
        return if (xsrfToken != null) {
            commonHeaders + mapOf("X-XSRF-TOKEN" to (xsrfToken ?: ""))
        } else commonHeaders
    }

    // -------------------------------------------------------------------------
    // Ana Sayfa
    // -------------------------------------------------------------------------

    override val mainPage = mainPageOf(
        "anime&sort=release_date" to "Son Eklenenler",
        "anime&sort=popularity"   to "En Popüler",
        "anime&status=ongoing"    to "Devam Edenler",
        "anime&status=completed"  to "Tamamlananlar"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val resp = app.get(
            "$apiUrl/titles?type=${request.data}&page=$page&perPage=20",
            headers = authHeaders()
        )
        val json = resp.parsedSafe<ApiResponse>() ?: return newHomePageResponse(emptyList())
        val items = json.data?.mapNotNull { it.toSearchResponse() } ?: emptyList()
        return newHomePageResponse(
            HomePageList(request.name, items),
            hasNext = json.meta?.currentPage != json.meta?.lastPage
        )
    }

    // -------------------------------------------------------------------------
    // Arama
    // -------------------------------------------------------------------------

    override suspend fun search(query: String): List<SearchResponse> {
        val resp = app.get(
            "$apiUrl/search?query=${query.encodeUrl()}&type=anime",
            headers = authHeaders()
        )
        return resp.parsedSafe<ApiResponse>()?.data
            ?.mapNotNull { it.toSearchResponse() }
            ?: emptyList()
    }

    // -------------------------------------------------------------------------
    // Detay
    // -------------------------------------------------------------------------

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        val resp = app.get("$apiUrl/titles/$id", headers = authHeaders())
        val item = resp.parsedSafe<ApiItem>()
            ?: return newAnimeLoadResponse(url, url, TvType.Anime) { }

        val title = item.name ?: item.title ?: "Bilinmeyen"
        val poster = item.poster ?: item.image
        val description = item.description ?: item.overview

        // Sezon ve bölüm listesi
        val episodes = mutableListOf<Episode>()
        item.seasons?.forEach { season ->
            season.episodes?.forEach { ep ->
                episodes.add(newEpisode("$mainUrl/titles/$id/s${season.number}/e${ep.number}") {
                    name = ep.name ?: "Bölüm ${ep.number}"
                    episode = ep.number
                    this.season = season.number
                    posterUrl = ep.poster
                })
            }
        }

        // Sezon yoksa tek bölüm filmi gibi davran
        if (episodes.isEmpty()) {
            episodes.add(newEpisode("$mainUrl/titles/$id") {
                name = title
            })
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = description
            this.tags = item.genres?.map { it.displayName ?: it.name ?: "" }
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
        // URL'den id, season, episode bilgisi çıkar
        val regex = Regex("""/titles/(\d+)(?:/s(\d+)/e(\d+))?""")
        val match = regex.find(data) ?: return false

        val titleId = match.groupValues[1]
        val seasonNum = match.groupValues[2].toIntOrNull() ?: 1
        val episodeNum = match.groupValues[3].toIntOrNull() ?: 1

        val streamsUrl = "$apiUrl/titles/$titleId/seasons/$seasonNum/episodes/$episodeNum/streams"
        val resp = app.get(streamsUrl, headers = authHeaders())
        val streams = resp.parsedSafe<StreamsResponse>() ?: return false

        streams.streams?.forEach { stream ->
            val url = stream.url ?: return@forEach
            val quality = when {
                url.contains("2160") || url.contains("4k", ignoreCase = true) -> Qualities.UHD_4K.value
                url.contains("1080") -> Qualities.P1080.value
                url.contains("720") -> Qualities.P720.value
                url.contains("480") -> Qualities.P480.value
                else -> Qualities.Unknown.value
            }
            callback(
                newExtractorLink(
                    source = name,
                    name = "${stream.label ?: name} [${stream.type ?: ""}]",
                    url = url,
                    type = if (url.contains("m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) { this.quality = quality }
            )
        }

        // Altyazılar
        streams.subtitles?.forEach { sub ->
            subtitleCallback(SubtitleFile(sub.label ?: "TR", sub.url ?: return@forEach))
        }

        return true
    }

    // -------------------------------------------------------------------------
    // Veri modelleri
    // -------------------------------------------------------------------------

    data class ApiResponse(
        val data: List<ApiItem>? = null,
        val meta: Meta? = null
    )

    data class Meta(
        val currentPage: Int? = null,
        val lastPage: Int? = null
    )

    data class ApiItem(
        val id: Int? = null,
        val name: String? = null,
        val title: String? = null,
        val poster: String? = null,
        val image: String? = null,
        val description: String? = null,
        val overview: String? = null,
        val genres: List<Genre>? = null,
        val seasons: List<Season>? = null,
        val type: String? = null
    ) {
        fun toSearchResponse(): SearchResponse? {
            val t = name ?: title ?: return null
            val url = "https://animecix.tv/titles/${id ?: return null}"
            return newAnimeSearchResponse(t, url, TvType.Anime) {
                this.posterUrl = poster ?: image
            }
        }
    }

    data class Genre(val name: String? = null, val displayName: String? = null)
    data class Season(val number: Int? = null, val episodes: List<EpisodeItem>? = null)
    data class EpisodeItem(
        val number: Int? = null,
        val name: String? = null,
        val poster: String? = null
    )
    data class StreamsResponse(
        val streams: List<Stream>? = null,
        val subtitles: List<Subtitle>? = null
    )
    data class Stream(val url: String? = null, val label: String? = null, val type: String? = null)
    data class Subtitle(val url: String? = null, val label: String? = null)
}

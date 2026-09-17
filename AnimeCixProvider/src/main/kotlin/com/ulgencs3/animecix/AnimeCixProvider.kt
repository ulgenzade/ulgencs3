package com.ulgencs3.animecix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*

/**
 * AnimeCiX Sağlayıcısı
 *
 * Site: https://animecix.tv
 * API Base: /secure/
 * Oynatıcılar: TauVideo (tau-video.xyz), Best-Video yönlendirmeleri, harici gömülü oynatıcılar.
 */
class AnimeCixProvider : MainAPI() {

    override var mainUrl = "https://animecix.tv"
    override var name = "AnimeCiX"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    override var sequentialMainPage = true
    override var sequentialMainPageDelay = 200L
    override var sequentialMainPageScrollDelay = 200L

    private val authHeaders = mapOf(
        "x-e-h" to "7Y2ozlO+QysR5w9Q6Tupmtvl9jJp7ThFH8SB+Lo7NvZjgjqRSqOgcT2v4ISM9sP10LmnlYI8WQ==.xrlyOBFS5BHjQ2Lk",
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
            AppUtils.parseJson<Map<String, String>>(config)["animecix"]
                ?.takeIf { it.isNotBlank() }?.let { mainUrl = it }
        } catch (_: Exception) { }
    }

    // -------------------------------------------------------------------------
    // Ana Sayfa
    // -------------------------------------------------------------------------

    override val mainPage = mainPageOf(
        "/secure/last-episodes"                          to "Son Eklenen Bölümler",
        "/secure/titles?type=series&onlyStreamable=true" to "Seriler",
        "/secure/titles?type=movie&onlyStreamable=true"  to "Filmler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureInit()
        val targetPath = if (request.data.startsWith("http")) request.data else "$mainUrl${request.data}"
        return if (request.data.contains("/last-episodes")) {
            val response = app.get(
                "$targetPath?page=$page&perPage=10",
                headers = authHeaders
            ).parsedSafe<LastEpisodesResponse>()?.data ?: emptyList()

            val home = response.map {
                val formattedTitle = "S${it.seasonNumber}B${it.episodeNumber} - ${it.titleName}"
                newAnimeSearchResponse(
                    formattedTitle,
                    "$mainUrl/secure/titles/${it.titleId}?titleId=${it.titleId}",
                    TvType.Anime
                ) {
                    this.posterUrl = fixUrlNull(it.titlePoster)
                }
            }

            newHomePageResponse(request.name, home)
        } else {
            val sep = if (targetPath.contains("?")) "&" else "?"
            val response = app.get(
                "$targetPath${sep}page=$page&perPage=16",
                headers = authHeaders
            ).parsedSafe<Category>()

            val home = response?.pagination?.data?.map { anime ->
                newAnimeSearchResponse(
                    anime.title,
                    "$mainUrl/secure/titles/${anime.id}?titleId=${anime.id}",
                    TvType.Anime
                ) {
                    this.posterUrl = fixUrlNull(anime.poster)
                }
            } ?: emptyList()

            newHomePageResponse(request.name, home)
        }
    }

    // -------------------------------------------------------------------------
    // Arama
    // -------------------------------------------------------------------------

    override suspend fun search(query: String): List<SearchResponse> {
        ensureInit()
        val response = app.get(
            "$mainUrl/secure/search/$query?limit=20",
            headers = authHeaders
        ).parsedSafe<Search>() ?: return emptyList()

        return response.results.map { anime ->
            newAnimeSearchResponse(
                anime.title,
                "$mainUrl/secure/titles/${anime.id}?titleId=${anime.id}",
                TvType.Anime
            ) {
                this.posterUrl = fixUrlNull(anime.poster)
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    // -------------------------------------------------------------------------
    // Detay & Bölümler
    // -------------------------------------------------------------------------

    override suspend fun load(url: String): LoadResponse? {
        ensureInit()
        val response = app.get(url, headers = authHeaders).parsedSafe<Title>() ?: return null
        val episodes = mutableListOf<Episode>()
        val titleId = url.substringAfter("?titleId=")

        if (response.title?.titleType == "anime" || response.title?.seasons?.isNotEmpty() == true) {
            for (sezon in response.title.seasons) {
                val sezonResponse = app.get(
                    "$mainUrl/secure/related-videos?episode=1&season=${sezon.number}&videoId=0&titleId=$titleId",
                    headers = authHeaders
                ).parsedSafe<TitleVideos>()

                sezonResponse?.videos?.forEach { video ->
                    episodes.add(newEpisode(video.url) {
                        this.name = "${video.seasonNum ?: sezon.number}. Sezon ${video.episodeNum ?: 1}. Bölüm"
                        this.season = video.seasonNum ?: sezon.number
                        this.episode = video.episodeNum ?: 1
                    })
                }
            }
        } else {
            if (response.title?.videos?.isNotEmpty() == true) {
                episodes.add(newEpisode(response.title.videos.first().url) {
                    this.name = "Filmi İzle"
                    this.season = 1
                    this.episode = 1
                })
            }
        }

        val anime = response.title ?: return null
        return newTvSeriesLoadResponse(
            anime.title,
            "$mainUrl/secure/titles/${anime.id}?titleId=${anime.id}",
            TvType.Anime,
            episodes
        ) {
            this.posterUrl = fixUrlNull(anime.poster)
            this.year = anime.year
            this.plot = anime.description
            this.tags = anime.tags.mapNotNull { it.name }
            addActors(anime.actors.map { Actor(it.name, fixUrlNull(it.poster)) })
            addTrailer(anime.trailer)
        }
    }

    // -------------------------------------------------------------------------
    // Video Oynatıcı Bağlantıları
    // -------------------------------------------------------------------------

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        ensureInit()
        val pageUrl = if (data.startsWith("http")) data else "$mainUrl/$data"
        val response = app.get(pageUrl, referer = "$mainUrl/")
        var iframeLink = response.url

        // Çift URL düzeltmesi
        val doubleUrlRegex = Regex("https://animecix.tv/(https://animecix.tv/secure/\\S+)")
        val match = doubleUrlRegex.find(iframeLink)
        if (match != null) {
            iframeLink = match.groupValues[1]
        }

        if (iframeLink.contains("/secure/best-video")) {
            val redirectResponse = app.get(iframeLink, referer = "$mainUrl/")
            val redirectedUrl = redirectResponse.url
            if (redirectedUrl.contains("tau-video")) {
                loadExtractor(redirectedUrl, "$mainUrl/", subtitleCallback, callback)
            } else {
                loadExtractor(redirectedUrl, "$mainUrl/", subtitleCallback, callback)
            }
        } else {
            loadExtractor(iframeLink, "$mainUrl/", subtitleCallback, callback)
        }

        return true
    }
}

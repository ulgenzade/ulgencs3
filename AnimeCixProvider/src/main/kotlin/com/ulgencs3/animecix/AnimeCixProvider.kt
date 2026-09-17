package com.ulgencs3.animecix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.*
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject

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

    private val modernUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private val xehToken = "7Y2ozlO+QysR5w9Q6Tupmtvl9jJp7ThFH8SB+Lo7NvZjgjqRSqOgcT2v4ISM9sP10LmnlYI8WQ==.xrlyOBFS5BHjQ2Lk"

    private fun getAuthHeaders(): Map<String, String> = mapOf(
        "User-Agent" to modernUserAgent,
        "x-e-h" to xehToken,
        "Referer" to "$mainUrl/",
        "Accept" to "application/json, text/plain, */*",
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
            org.json.JSONObject(config).optString("animecix")
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

        if (request.data.contains("/last-episodes")) {
            val response = runCatching {
                app.get(
                    "$targetPath?page=$page&perPage=16",
                    headers = getAuthHeaders(),
                    interceptor = cfInterceptor
                ).parsedSafe<LastEpisodesResponse>()?.data
            }.getOrNull() ?: emptyList()

            val home = response.mapNotNull {
                val animeId = it.titleId ?: return@mapNotNull null
                val titleName = it.titleName ?: "Bölüm"
                val formattedTitle = "S${it.seasonNumber ?: 1}B${it.episodeNumber ?: 1} - $titleName"
                newAnimeSearchResponse(
                    formattedTitle,
                    "$mainUrl/secure/titles/$animeId?titleId=$animeId",
                    TvType.Anime
                ) {
                    this.posterUrl = fixUrlNull(it.titlePoster)
                }
            }

            return newHomePageResponse(HomePageList(request.name, home), hasNext = home.isNotEmpty())
        } else {
            val sep = if (targetPath.contains("?")) "&" else "?"
            val response = runCatching {
                app.get(
                    "$targetPath${sep}page=$page&perPage=16",
                    headers = getAuthHeaders(),
                    interceptor = cfInterceptor
                ).parsedSafe<Category>()
            }.getOrNull()

            var dataList = response?.pagination?.data

            // Fallback: JSON nesnesini doğrudan ayrıştır
            if (dataList.isNullOrEmpty()) {
                runCatching {
                    val rawText = app.get(
                        "$targetPath${sep}page=$page&perPage=16",
                        headers = getAuthHeaders(),
                        interceptor = cfInterceptor
                    ).text
                    val json = JSONObject(rawText)
                    val pag = json.optJSONObject("pagination")
                    val items = pag?.optJSONArray("data")
                    if (items != null) {
                        val fallbackList = mutableListOf<AnimeSearch>()
                        for (i in 0 until items.length()) {
                            val obj = items.getJSONObject(i)
                            fallbackList.add(
                                AnimeSearch(
                                    id = obj.optInt("id"),
                                    titleType = obj.optString("title_type"),
                                    title = obj.optString("name").takeIf { it.isNotBlank() } ?: obj.optString("title"),
                                    nameEnglish = obj.optString("name_english"),
                                    poster = obj.optString("poster")
                                )
                            )
                        }
                        dataList = fallbackList
                    }
                }
            }

            val home = dataList?.mapNotNull { anime ->
                val animeId = anime.id ?: return@mapNotNull null
                val titleName = anime.title ?: anime.nameEnglish ?: anime.nameRomanji ?: return@mapNotNull null
                val type = if (anime.titleType == "movie") TvType.AnimeMovie else TvType.Anime
                newAnimeSearchResponse(
                    titleName,
                    "$mainUrl/secure/titles/$animeId?titleId=$animeId",
                    type
                ) {
                    this.posterUrl = fixUrlNull(anime.poster)
                }
            } ?: emptyList()

            return newHomePageResponse(HomePageList(request.name, home), hasNext = home.isNotEmpty())
        }
    }

    // -------------------------------------------------------------------------
    // Arama
    // -------------------------------------------------------------------------

    override suspend fun search(query: String): List<SearchResponse> {
        ensureInit()
        val response = runCatching {
            app.get(
                "$mainUrl/secure/search/${query.trim()}?limit=20",
                headers = getAuthHeaders(),
                interceptor = cfInterceptor
            ).parsedSafe<Search>()
        }.getOrNull()

        var results = response?.results

        if (results.isNullOrEmpty()) {
            runCatching {
                val rawText = app.get(
                    "$mainUrl/secure/search/${query.trim()}?limit=20",
                    headers = getAuthHeaders(),
                    interceptor = cfInterceptor
                ).text
                val json = JSONObject(rawText)
                val arr = json.optJSONArray("results")
                if (arr != null) {
                    val list = mutableListOf<AnimeSearch>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(
                            AnimeSearch(
                                id = obj.optInt("id"),
                                titleType = obj.optString("title_type"),
                                title = obj.optString("name").takeIf { it.isNotBlank() } ?: obj.optString("title"),
                                nameEnglish = obj.optString("name_english"),
                                poster = obj.optString("poster")
                            )
                        )
                    }
                    results = list
                }
            }
        }

        return results?.mapNotNull { anime ->
            val animeId = anime.id ?: return@mapNotNull null
            val titleName = anime.title ?: anime.nameEnglish ?: anime.nameRomanji ?: return@mapNotNull null
            val type = if (anime.titleType == "movie") TvType.AnimeMovie else TvType.Anime
            newAnimeSearchResponse(
                titleName,
                "$mainUrl/secure/titles/$animeId?titleId=$animeId",
                type
            ) {
                this.posterUrl = fixUrlNull(anime.poster)
            }
        } ?: emptyList()
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    // -------------------------------------------------------------------------
    // Detay & Bölümler
    // -------------------------------------------------------------------------

    override suspend fun load(url: String): LoadResponse? {
        ensureInit()
        val response = runCatching {
            app.get(url, headers = getAuthHeaders(), interceptor = cfInterceptor).parsedSafe<Title>()
        }.getOrNull() ?: return null

        val episodes = mutableListOf<Episode>()
        val titleId = url.substringAfter("?titleId=")

        val animeData = response.title ?: return null
        val seasons = animeData.seasons

        if (animeData.titleType == "anime" || seasons.isNotEmpty()) {
            for (sezon in seasons) {
                val sezonResponse = runCatching {
                    app.get(
                        "$mainUrl/secure/related-videos?episode=1&season=${sezon.number}&videoId=0&titleId=$titleId",
                        headers = getAuthHeaders(),
                        interceptor = cfInterceptor
                    ).parsedSafe<TitleVideos>()
                }.getOrNull()

                sezonResponse?.videos?.forEach { video ->
                    val epUrl = video.url?.takeIf { it.isNotBlank() } ?: return@forEach

                    // API'de video.name = "1. Bölüm", video.description = gerçek bölüm adı
                    // Önce description'ı başlık olarak al; yoksa name'i temizle
                    val descTitle = video.description?.trim()?.takeIf { it.isNotBlank() }
                    val cleanName = video.name?.replace(Regex("""^\s*\d+\.?\s*Bölüm\s*[-–:]*\s*""", RegexOption.IGNORE_CASE), "")?.trim()
                        ?.takeIf { it.isNotBlank() && !it.equals("Bölüm", ignoreCase = true) }
                    val finalEpTitle = descTitle ?: cleanName

                    episodes.add(newEpisode(epUrl) {
                        this.name = finalEpTitle
                        this.season = video.seasonNum ?: sezon.number
                        this.episode = video.episodeNum ?: 1
                        this.posterUrl = fixUrlNull(video.thumbnail)
                    })
                }
            }
        } else {
            if (animeData.videos.isNotEmpty()) {
                val epUrl = animeData.videos.first().url?.takeIf { it.isNotBlank() }
                if (epUrl != null) {
                    episodes.add(newEpisode(epUrl) {
                        this.name = "Filmi İzle"
                        this.season = 1
                        this.episode = 1
                    })
                }
            }
        }

        val anime = animeData
        val titleName = anime.title ?: anime.nameEnglish ?: anime.altTitle ?: "İsimsiz Anime"

        return newTvSeriesLoadResponse(
            titleName,
            "$mainUrl/secure/titles/${anime.id ?: titleId}?titleId=${anime.id ?: titleId}",
            TvType.Anime,
            episodes
        ) {
            this.posterUrl = fixUrlNull(anime.poster)
            this.year = anime.year
            this.plot = anime.description
            this.tags = anime.tags.mapNotNull { it.name ?: it.fallbackName }
            addActors(anime.actors.mapNotNull {
                val actorName = it.name ?: return@mapNotNull null
                Actor(actorName, fixUrlNull(it.poster))
            })
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
        val response = runCatching {
            app.get(pageUrl, referer = "$mainUrl/", interceptor = cfInterceptor)
        }.getOrNull() ?: return false

        var iframeLink = response.url

        // Çift URL düzeltmesi
        val doubleUrlRegex = Regex("https://animecix.tv/(https://animecix.tv/secure/\\S+)")
        val match = doubleUrlRegex.find(iframeLink)
        if (match != null) {
            iframeLink = match.groupValues[1]
        }

        if (iframeLink.contains("/secure/best-video")) {
            val redirectResponse = runCatching {
                app.get(iframeLink, referer = "$mainUrl/", interceptor = cfInterceptor)
            }.getOrNull()
            val redirectedUrl = redirectResponse?.url ?: iframeLink
            loadExtractor(redirectedUrl, "$mainUrl/", subtitleCallback, callback)
        } else {
            loadExtractor(iframeLink, "$mainUrl/", subtitleCallback, callback)
        }

        return true
    }
}

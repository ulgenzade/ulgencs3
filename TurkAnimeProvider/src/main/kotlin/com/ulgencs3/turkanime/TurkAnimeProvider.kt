package com.ulgencs3.turkanime

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Türk Anime TV Sağlayıcısı
 *
 * Site: https://www.turkanime.tv
 * Fandom & Çeviri Grupları: AniSekai, Benihime, PuzzleSubs, YuushaSubs, Eski Çeviri vb.
 * Medya Oynatıcılar: Sibnet, OK.ru, Mail.ru, Doodstream, Mp4upload, Sendvid, VOE, Vudea, ArtPlayer M3U8 vb.
 */
class TurkAnimeProvider : MainAPI() {

    override var mainUrl = "https://www.turkanime.tv"
    override var name = "Türk Anime TV"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

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
            val configText = app.get(
                "https://raw.githubusercontent.com/ulgenzade/ulgencs3/master/domains.json",
                timeout = 5
            ).text
            org.json.JSONObject(configText).optString("turkanime")
                .takeIf { it.isNotBlank() }?.let { mainUrl = it }
        } catch (_: Exception) { }
    }

    // -------------------------------------------------------------------------
    // Ana Sayfa
    // -------------------------------------------------------------------------

    override val mainPage = mainPageOf(
        "/"                              to "Son Eklenenler",
        "/anime-turu/1/Aksiyon"          to "Aksiyon",
        "/anime-turu/2/Macera"           to "Macera",
        "/anime-turu/4/Komedi"           to "Komedi",
        "/anime-turu/8/Dram"             to "Dram",
        "/anime-turu/10/Fantastik"       to "Fantastik",
        "/anime-turu/14/Korku"           to "Korku",
        "/anime-turu/22/Romantizm"       to "Romantizm",
        "/anime-turu/24/Bilim_Kurgu"     to "Bilim Kurgu",
        "/anime-turu/27/Shounen"         to "Shounen",
        "/anime-turu/42/Okul"            to "Okul",
        "/anime-turu/43/Spor"            to "Spor"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureInit()
        val targetUrl = if (request.data.startsWith("http")) request.data else "$mainUrl${request.data}"
        val doc = app.get(targetUrl, headers = commonHeaders).document
        val home = doc.select("div#orta-icerik div.panel, div.panel-visible").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    // -------------------------------------------------------------------------
    // Arama
    // -------------------------------------------------------------------------

    override suspend fun search(query: String): List<SearchResponse> {
        ensureInit()
        val doc = app.post(
            "$mainUrl/arama",
            headers = commonHeaders,
            data = mapOf("arama" to query)
        ).document

        return doc.select("div#orta-icerik div.panel, div.panel-visible").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    // -------------------------------------------------------------------------
    // Detay & Bölüm Listesi
    // -------------------------------------------------------------------------

    override suspend fun load(url: String): LoadResponse? {
        ensureInit()
        val doc = app.get(url, headers = commonHeaders).document

        val title = doc.selectFirst("div#detayPaylas div.panel-title, h2.panel-title")
            ?.text()?.trim() ?: return null

        val poster = fixUrlNull(
            doc.selectFirst("div#detayPaylas div.imaj img")?.attr("data-src")
                ?: doc.selectFirst("div#detayPaylas div.imaj img, div.anime-poster img")?.attr("src")
                ?: doc.selectFirst("meta[property=og:image]")?.attr("content")
        )

        val description = doc.selectFirst("div#detayPaylas p.ozet, div.anime-ozet")?.text()?.trim()
        val year = doc.selectFirst("div#detayPaylas a[href*='yil/']")?.attr("href")
            ?.substringAfter("yil/")?.substringBefore("/")?.toIntOrNull()
        val tags = doc.select("div#animedetay a[href*='anime-turu'], a[href*='/tur/']").map { it.text().trim() }

        // Bölümler AJAX ile çekilir
        val episodes = mutableListOf<Episode>()
        val bolumlerUrl = fixUrlNull(doc.selectFirst("a[data-url*='ajax/bolumler&animeId=']")?.attr("data-url"))

        if (bolumlerUrl != null) {
            val token = doc.selectFirst("meta[name='_token']")?.attr("content").orEmpty()
            val bolumlerDoc = app.get(
                bolumlerUrl,
                headers = mapOf(
                    "User-Agent" to (commonHeaders["User-Agent"] ?: ""),
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to url,
                    "token" to token
                ),
                cookies = mapOf("yasOnay" to "1")
            ).document

            bolumlerDoc.select("div#bolum-list li").forEach { li ->
                val epLink = fixUrlNull(li.selectFirst("a[href*='/video/']")?.attr("href")) ?: return@forEach
                val epName = li.selectFirst("span.bolumAdi")?.text()?.trim()
                    ?: li.selectFirst("a[href*='/video/']")?.attr("title")?.trim() ?: "Bölüm"
                val epNum = Regex("""(\d+)\.\s*[Bb]ölüm""").find(epName)?.groupValues?.get(1)?.toIntOrNull()
                val cleanName = epName.replace(Regex("""^\s*\d+\.\s*Bölüm\s*[-–:]*\s*"""), "").trim()
                val finalName = cleanName.takeIf { it.isNotBlank() && !it.equals("Bölüm", ignoreCase = true) }

                episodes.add(newEpisode(epLink) {
                    name = finalName
                    episode = epNum
                    season = 1
                    posterUrl = poster
                })
            }
        }

        // Fallback: Doğrudan sayfadaki bölüm linkleri
        if (episodes.isEmpty()) {
            doc.select("div.panel-visible a[href*='/video/'], a[href*='/video/']").distinctBy { it.attr("href") }.forEach { el ->
                val epUrl = fixUrlNull(el.attr("href")) ?: return@forEach
                val epText = el.text().trim()
                val epNum = Regex("""(\d+)""").find(epText)?.groupValues?.get(1)?.toIntOrNull()
                val cleanText = epText.replace(Regex("""^\s*\d+\.\s*Bölüm\s*[-–:]*\s*"""), "").trim()
                val fallbackName = cleanText.takeIf { it.isNotBlank() && !it.equals("Bölüm", ignoreCase = true) }
                episodes.add(newEpisode(epUrl) {
                    name = fallbackName
                    episode = epNum
                    season = 1
                    posterUrl = poster
                })
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
        }
    }

    // -------------------------------------------------------------------------
    // AES Şifre Çözücü & Video Oynatıcı Ayrıştırma
    // -------------------------------------------------------------------------

    private suspend fun iframe2AesLink(iframe: String): String? {
        return try {
            val aesDataRaw = iframe.substringAfter("embed/#/url/").substringBefore("?status")
            val aesDataJson = String(Base64.decode(aesDataRaw, Base64.DEFAULT))
            val aesKey = "710^8A@3@>T2}#zN5xK?kR7KNKb@-A!LzYL5~M1qU0UfdWsZoBm4UUat%}ueUv6E--*hDPPbH7K2bp9^3o41hw,khL:}Kx8080@M"
            val decrypted = AesHelper.cryptoAESHandler(aesDataJson, aesKey.toByteArray(), false)
                ?.replace("\\", "")?.replace("\"", "")
            fixUrlNull(decrypted)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        ensureInit()
        val doc = app.get(data, headers = commonHeaders, cookies = mapOf("yasOnay" to "1")).document

        // 1. Önce doğrudan sayfadaki ArtPlayer M3U8 akışı kontrolü (Hızlı Oynatma)
        val mainDataUrl = doc.selectFirst("div.artplayer-app")?.attr("data-url")
        if (!mainDataUrl.isNullOrBlank()) {
            callback(
                newExtractorLink(
                    name = "$name [M3U8]",
                    source = name,
                    url = mainDataUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    quality = Qualities.P1080.value
                    headers = mapOf("Referer" to data)
                }
            )
        }

        // 2. Doğrudan sayfadaki varsayılan iframe kontrolü
        val defaultPageIframe = doc.selectFirst("iframe")?.attr("src")
        if (!defaultPageIframe.isNullOrBlank() && !defaultPageIframe.contains("a-ads.com")) {
            resolveAndLoadPlayer(defaultPageIframe, data, subtitleCallback, callback)
        }

        // Sayfadaki Fansub (Fandom) ve alternatif oynatıcı butonlarını topla
        val fansubButtons = doc.select("button[onclick*='IndexIcerik'], button[onclick*='ajax/videosec']")
        val visitedLinks = mutableSetOf<String>()

        if (fansubButtons.isNotEmpty()) {
            for (button in fansubButtons) {
                val onclick = button.attr("onclick")
                val subEndpoint = onclick.substringAfter("IndexIcerik('").substringBefore("'")
                    .takeIf { it.isNotBlank() } ?: continue
                val fullSubLink = fixUrlNull(subEndpoint) ?: continue

                if (!visitedLinks.add(fullSubLink)) continue
                val fansubName = button.ownText().trim().ifBlank { "Varsayılan" }

                try {
                    val subResp = app.get(
                        fullSubLink,
                        headers = mapOf(
                            "User-Agent" to (commonHeaders["User-Agent"] ?: ""),
                            "X-Requested-With" to "XMLHttpRequest",
                            "Referer" to data
                        ),
                        cookies = mapOf("yasOnay" to "1")
                    )
                    val subDoc = Jsoup.parse(subResp.text, fullSubLink)

                    // 1. Artplayer direct m3u8 akışı var mı?
                    val dataUrl = subDoc.selectFirst("div.artplayer-app")?.attr("data-url")
                    if (!dataUrl.isNullOrBlank()) {
                        callback(
                            newExtractorLink(
                                name = "$name [$fansubName - M3U8]",
                                source = "$name ($fansubName)",
                                url = dataUrl,
                                type = ExtractorLinkType.M3U8
                            ) {
                                quality = Qualities.P1080.value
                                headers = mapOf("Referer" to fullSubLink)
                            }
                        )
                    }

                    // 2. Default iframe
                    val defaultIframe = subDoc.selectFirst("iframe")?.attr("src")
                    if (!defaultIframe.isNullOrBlank() && !defaultIframe.contains("a-ads.com")) {
                        resolveAndLoadPlayer(defaultIframe, fullSubLink, subtitleCallback, callback)
                    }

                    // 3. Bu fansuba ait alternatif medya oynatıcı butonları (SIBNET, OK.RU, DOOD vb.)
                    val playerButtons = subDoc.select("div.btn-group button[onclick*='IndexIcerik']")
                    for (pBtn in playerButtons) {
                        val pOnclick = pBtn.attr("onclick")
                        val pEndpoint = pOnclick.substringAfter("IndexIcerik('").substringBefore("'")
                            .takeIf { it.isNotBlank() } ?: continue
                        val pFullLink = fixUrlNull(pEndpoint) ?: continue
                        if (!visitedLinks.add(pFullLink)) continue

                        try {
                            val pResp = app.get(
                                pFullLink,
                                headers = mapOf(
                                    "User-Agent" to (commonHeaders["User-Agent"] ?: ""),
                                    "X-Requested-With" to "XMLHttpRequest",
                                    "Referer" to fullSubLink
                                ),
                                cookies = mapOf("yasOnay" to "1")
                            )
                            val pDoc = Jsoup.parse(pResp.text, pFullLink)
                            val pIframe = pDoc.selectFirst("iframe")?.attr("src")
                            if (!pIframe.isNullOrBlank() && !pIframe.contains("a-ads.com")) {
                                resolveAndLoadPlayer(pIframe, pFullLink, subtitleCallback, callback)
                            }
                        } catch (_: Exception) { }
                    }
                } catch (_: Exception) { }
            }
        } else {
            // Buton bulunamazsa sayfadaki doğrudan iframe'leri dene
            for (iframe in doc.select("iframe[src]")) {
                val src = fixUrlNull(iframe.attr("src")) ?: continue
                if (!src.contains("a-ads.com")) {
                    resolveAndLoadPlayer(src, data, subtitleCallback, callback)
                }
            }
        }

        return true
    }

    private suspend fun resolveAndLoadPlayer(
        iframeSrc: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val targetUrl = if (iframeSrc.contains("embed/#/url/")) {
            iframe2AesLink(iframeSrc) ?: iframeSrc
        } else {
            fixUrlNull(iframeSrc) ?: return
        }

        loadExtractor(
            url = targetUrl,
            referer = referer,
            subtitleCallback = subtitleCallback,
            callback = callback
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleEl = selectFirst("div.panel-title a, a.top-airing-item") ?: selectFirst("a") ?: return null
        val title = titleEl.text().trim().takeIf { it.isNotBlank() }
            ?: titleEl.attr("data-title").takeIf { it.isNotBlank() }
            ?: titleEl.attr("title").takeIf { it.isNotBlank() } ?: return null
        val href = fixUrlNull(titleEl.attr("href")) ?: return null
        val poster = fixUrlNull(
            selectFirst("img")?.attr("data-src")?.takeIf { !it.contains("base64") }
                ?: selectFirst("img")?.attr("src")
        )

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = poster
        }
    }
}

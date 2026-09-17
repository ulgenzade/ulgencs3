// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.ulgencs3.sezonlukdizi

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors

class SezonlukDizi : MainAPI() {
    override var mainUrl              = "https://sezonlukdizi.cc"

    private var isInitialized = false
    private suspend fun ensureInit() {
        if (isInitialized) return
        isInitialized = true
        try {
            val config = app.get(
                "https://raw.githubusercontent.com/ulgenzade/ulgencs3/master/domains.json",
                timeout = 5
            ).text
            org.json.JSONObject(config).optString("sezonlukdizi")
                ?.takeIf { it.isNotBlank() }?.let { mainUrl = it }
        } catch (_: Exception) { }
    }

    override var name                 = "SezonlukDizi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "diziler.asp?siralama_tipi=id&s="          to "Son Eklenenler",
        "diziler.asp?siralama_tipi=id&tur=mini&s=" to "Mini Diziler",
        "diziler.asp?siralama_tipi=id&kat=2&s="    to "Yerli Diziler",
        "diziler.asp?siralama_tipi=id&kat=1&s="    to "Yabancı Diziler",
        "diziler.asp?siralama_tipi=id&kat=3&s="    to "Asya Dizileri",
        "diziler.asp?siralama_tipi=id&kat=4&s="    to "Animasyonlar",
        "diziler.asp?siralama_tipi=id&kat=5&s="    to "Animeler",
        "diziler.asp?siralama_tipi=id&kat=6&s="    to "Belgeseller",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureInit()
        val pageUrl = if (request.data.startsWith("http")) request.data else "$mainUrl/${request.data}"
        val document = app.get("${pageUrl}${page}").document
        val home     = document.select("div.afis a").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("div.description")?.text()?.trim() ?: return null
        val href      = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureInit()
        val document = app.get("$mainUrl/diziler.asp?adi=${query}").document

        return document.select("div.afis a").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        ensureInit()
        val document = app.get(url).document

        val title       = document.selectFirst("div.header")?.text()?.trim() ?: return null
        val poster      = fixUrlNull(document.selectFirst("div.image img")?.attr("data-src")) ?: return null
        val year        = document.selectFirst("div.extra span")?.text()?.trim()?.split("-")?.first()?.toIntOrNull()
        val description = document.selectFirst("span#tartismayorum-konu")?.text()?.trim()
        val tags        = document.select("div.labels a[href*='tur']").mapNotNull { it.text().trim() }
        val duration    = document.selectXpath("//span[contains(text(), 'Dk.')]").text().trim().substringBefore(" Dk.").toIntOrNull()

        val endpoint    = url.split("/").last()

        val actorsReq  = app.get("$mainUrl/oyuncular/${endpoint}").document
        val actors     = actorsReq.select("div.doubling div.ui").mapNotNull {
            val actorName = it.selectFirst("div.header")?.text()?.trim() ?: return@mapNotNull null
            Actor(actorName, fixUrlNull(it.selectFirst("img")?.attr("src")))
        }

        val episodesReq = app.get("$mainUrl/bolumler/${endpoint}").document
        val episodes    = mutableListOf<Episode>()
        for (sezon in episodesReq.select("table.unstackable")) {
            for (bolum in sezon.select("tbody tr")) {
                val rawEpName = bolum.selectFirst("td:nth-of-type(4) a")?.text()?.trim() ?: continue
                val epHref    = fixUrlNull(bolum.selectFirst("td:nth-of-type(4) a")?.attr("href")) ?: continue
                val epEpisode = bolum.selectFirst("td:nth-of-type(3)")?.text()?.substringBefore(".Bölüm")?.trim()?.toIntOrNull()
                val epSeason  = bolum.selectFirst("td:nth-of-type(2)")?.text()?.substringBefore(".Sezon")?.trim()?.toIntOrNull()

                val cleanEpName = rawEpName.replace(Regex("""^\d+\.\s*Bölüm\s*[-–:]*\s*"""), "").trim()
                val epName = cleanEpName.takeIf { it.isNotBlank() && !it.equals("Bölüm", ignoreCase = true) }

                episodes.add(newEpisode(epHref) {
                    this.name    = epName
                    this.season  = epSeason
                    this.episode = epEpisode
                })
            }
        }


        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.year      = year
            this.plot      = description
            this.tags      = tags
            this.duration  = duration
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        ensureInit()
        ensureInit()
        Log.d("SZD", "data » $data")
        val document = app.get(data).document
        val aspData = getAspData()
        val bid = document.selectFirst("div#dilsec")?.attr("data-id") ?: return false
        Log.d("SZD", "bid » $bid")

        // --- ALTYAZI KISMI ---
        val altyaziResponse = app.post(
            "ajax/dataAlternatif${aspData.alternatif}.asp",
            headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
            data = mapOf(
                "bid" to bid,
                "dil" to "1"
            )
        ).parsedSafe<Kaynak>()

        if (altyaziResponse?.status == "success" && altyaziResponse.data != null) {
            for (veri in altyaziResponse.data) {
                Log.d("SZD", "dil»1 | veri.baslik » ${veri.baslik}")

                val veriResponse = app.post(
                    "ajax/dataEmbed${aspData.embed}.asp",
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                    data = mapOf("id" to "${veri.id}")
                ).document

                val iframeSrc = veriResponse.selectFirst("iframe")?.attr("src")
                val iframe = fixUrlNull(iframeSrc) ?: continue
                Log.d("SZD", "dil»1 | iframe » $iframe")

                loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
            }
        }

        // --- DUBLAJ KISMI ---
        val dublajResponse = app.post(
            "ajax/dataAlternatif${aspData.alternatif}.asp",
            headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
            data = mapOf(
                "bid" to bid,
                "dil" to "0"
            )
        ).parsedSafe<Kaynak>()

        if (dublajResponse?.status == "success" && dublajResponse.data != null) {
            for (veri in dublajResponse.data) {
                Log.d("SZD", "dil»0 | veri.baslik » ${veri.baslik}")

                val veriResponse = app.post(
                    "ajax/dataEmbed${aspData.embed}.asp",
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                    data = mapOf("id" to "${veri.id}")
                ).document

                val iframeSrc = veriResponse.selectFirst("iframe")?.attr("src")
                val iframe = fixUrlNull(iframeSrc) ?: continue
                Log.d("SZD", "dil»0 | iframe » $iframe")

                loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
            }
        }

        return true
    }

    //Helper function for getting the number (probably some kind of version?) after the dataAlternatif and dataEmbed
    private suspend fun getAspData() : AspData{
        val websiteCustomJavascript = app.get("${this.mainUrl}/js/site.min.js")
        val dataAlternatifAsp = Regex("""dataAlternatif(.*?).asp""").find(websiteCustomJavascript.text)?.groupValues?.get(1)
            .toString()
        val dataEmbedAsp = Regex("""dataEmbed(.*?).asp""").find(websiteCustomJavascript.text)?.groupValues?.get(1)
            .toString()
        return AspData(dataAlternatifAsp,dataEmbedAsp)
    }
}

Adım Adım Eklenti Geliştirme Rehberi
Bu rehber, sıfırdan bir CloudStream eklentisi geliştirip depoya ekleme adımlarını anlatır.

Adım 1: Proje Hiyerarşisi ve Modül Kaydı
Kök dizindeki settings.gradle.kts dosyasına yeni eklenti tanıtılır:
include(":FilmModuProvider")

Dizin yapısı şu hiyerarşide oluşturulur:

FilmModuProvider/

build.gradle.kts

src/main/AndroidManifest.xml

src/main/kotlin/com/turkce/FilmModuProvider.kt

src/main/kotlin/com/turkce/FilmModuPlugin.kt

Modül build.gradle.kts dosyası cloudstream3 konfigürasyonunu içermelidir:

apply(plugin = "com.android.library")
apply(plugin = "kotlin-android")
apply(plugin = "com.lagradost.cloudstream3.gradle")

cloudstream {
    setRepoUrl("https://raw.githubusercontent.com/<KULLANICI>/<DEPO>/builds")
}

Adım 2: Eklenti Yaşam Döngüsü (FilmModuPlugin.kt)
Modülün uygulama çalışma zamanına kendini kaydetmesi için giriş noktası oluşturulur:

package com.turkce

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class FilmModuPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FilmModuProvider())
    }
}

Adım 3: Kazıma Mantığı (FilmModuProvider.kt)
MainProvider sınıfı türetilerek web sitesi ayrıştırma fonksiyonları yazılır:
package com.turkce

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class FilmModuProvider : MainProvider() {
    override var mainUrl = "https://ornekfilmkaynagi.com"
    override var name = "FilmModuTR"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get("$mainUrl/filmler/page/$page").document
        val items = doc.select("div.movie-item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            listOf(HomePageList("Popüler İçerikler", items)),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2.title")?.text()?.trim() ?: return null
        val url = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val poster = fixUrlNull(this.selectFirst("img")?.attr("data-src"))
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        return doc.select("div.movie-item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1.entry-title")?.text()?.trim() ?: "Bilinmeyen Başlık"
        val poster = fixUrlNull(doc.selectFirst("div.poster img")?.attr("src"))
        val description = doc.selectFirst("div.description")?.text()?.trim()

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        doc.select("iframe.player-frame").forEach { iframe ->
            val src = fixUrl(iframe.attr("src"))
            loadExtractor(src, subtitleCallback, callback)
        }
        return true
    }
}Adım 4: Yerel Test ve Cihaza Yükleme
Android 11 ve üzeri cihazlarda yerel test yapabilmek için terminal üzerinden CloudStream'e depolama yetkisi tanımlanır:

adb shell appops set --uid com.lagradost.cloudstream3.prerelease MANAGE_EXTERNAL_STORAGE allow

Ardından modül doğrudan derlenip cihaza gönderilir:

# Windows
.\gradlew.bat FilmModuProvider:deployWithAdb

# Linux / macOS
./gradlew FilmModuProvider:deployWithAdb

Adım 5: GitHub Actions ile Yayınlama
Kodlar ana dala gönderildiğinde .github/workflows/build.yml tetiklenir, derleme tamamlandığında eklenti builds dalında paketlenir. Kullanıcılar depoyu şu bağlantıyla ekler:
[https://raw.githubusercontent.com/](https://raw.githubusercontent.com/)<KULLANICI_ADI>/<DEPO_ADI>/master/repo.json
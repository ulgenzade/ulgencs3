import com.lagradost.cloudstream3.gradle.CloudstreamExtension

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

version = 1

cloudstream {
    authors = listOf("ulgenzade")
    language = "tr"
    description = "HDFilmCehennemi - Türkiye'nin En Büyük Film ve Dizi Platformu"
    status = 1
    tvTypes = listOf("Movie", "TvSeries")
}

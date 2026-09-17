import com.lagradost.cloudstream3.gradle.CloudstreamExtension

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

version = 1

cloudstream {
    authors = listOf("ulgenzade")
    language = "tr"
    description = "SezonlukDizi - Tüm Sezon ve Bölümler"
    status = 1
    tvTypes = listOf("TvSeries")
}

import com.lagradost.cloudstream3.gradle.CloudstreamExtension

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

version = 1

cloudstream {
    authors = listOf("ulgenzade")
    language = "tr"
    description = "DiziMom - Yabancı Dizi ve Çizgi Diziler"
    status = 1
    tvTypes = listOf("TvSeries")
}

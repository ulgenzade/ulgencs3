import com.lagradost.cloudstream3.gradle.CloudstreamExtension

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

version = 1

cloudstream {
    authors = listOf("ulgenzade")
    language = "tr"
    description = "FilmMakinesi - 1080p Film ve Dizi Platformu"
    status = 1
    tvTypes = listOf("Movie")
}

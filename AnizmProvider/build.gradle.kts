import com.lagradost.cloudstream3.gradle.CloudstreamExtension

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

version = 3

cloudstream {
    authors = listOf("ulgenzade")
    language = "tr"
    description = "Anizm - Türkçe Altyazılı Anime İzleme Platformu"
    status = 1
    tvTypes = listOf("Anime")
}
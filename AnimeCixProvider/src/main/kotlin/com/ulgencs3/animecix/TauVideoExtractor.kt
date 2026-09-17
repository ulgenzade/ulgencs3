package com.ulgencs3.animecix

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class TauVideo : ExtractorApi() {
    override val name = "TauVideo"
    override val mainUrl = "https://tau-video.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val extRef = referer ?: ""
        val videoKey = url.split("/").last()
        val videoUrl = "$mainUrl/api/video/$videoKey"

        val api = app.get(videoUrl).parsedSafe<TauVideoUrls>() ?: return

        for (video in api.urls) {
            callback(
                newExtractorLink(
                    source = name,
                    name = "$name [${video.label}]",
                    url = video.url,
                    type = INFER_TYPE
                ) {
                    headers = mapOf("Referer" to extRef)
                    quality = getQualityFromName(video.label)
                }
            )
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TauVideoUrls(
        @JsonProperty("urls") val urls: List<TauVideoData> = emptyList()
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TauVideoData(
        @JsonProperty("url") val url: String = "",
        @JsonProperty("label") val label: String = ""
    )
}

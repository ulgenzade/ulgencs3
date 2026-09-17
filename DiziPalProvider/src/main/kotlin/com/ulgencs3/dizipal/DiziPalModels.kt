package com.ulgencs3.dizipal

import com.fasterxml.jackson.annotation.JsonProperty

data class DizipalSearchData(
    @JsonProperty("success") val success: Boolean? = null,
    @JsonProperty("results") val results: List<DizipalSearchResult>? = emptyList()
)

data class DizipalSearchResult(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("year") val year: Int? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("poster") val poster: String? = null,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("rating") val rating: String? = null
)

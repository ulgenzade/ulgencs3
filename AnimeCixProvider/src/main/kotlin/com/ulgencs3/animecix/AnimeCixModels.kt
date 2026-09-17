package com.ulgencs3.animecix

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class Category(
    @JsonProperty("pagination") val pagination: Pagination? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Search(
    @JsonProperty("results") val results: List<AnimeSearch>? = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Title(
    @JsonProperty("title") val title: Anime? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Pagination(
    @JsonProperty("current_page") val currentPage: Int? = 1,
    @JsonProperty("last_page") val lastPage: Int? = 1,
    @JsonProperty("per_page") val perPage: Int? = 16,
    @JsonProperty("data") val data: List<AnimeSearch>? = emptyList(),
    @JsonProperty("total") val total: Int? = 0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AnimeSearch(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("title_type") val titleType: String? = null,
    @JsonProperty("name") val title: String? = null,
    @JsonProperty("title") val altTitle: String? = null,
    @JsonProperty("name_english") val nameEnglish: String? = null,
    @JsonProperty("name_romanji") val nameRomanji: String? = null,
    @JsonProperty("poster") val poster: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Anime(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("title_type") val titleType: String? = null,
    @JsonProperty("name") val title: String? = null,
    @JsonProperty("title") val altTitle: String? = null,
    @JsonProperty("name_english") val nameEnglish: String? = null,
    @JsonProperty("poster") val poster: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("year") val year: Int? = null,
    @JsonProperty("mal_vote_average") val rating: String? = null,
    @JsonProperty("genres") val tags: List<Genre> = emptyList(),
    @JsonProperty("trailer") val trailer: String? = null,
    @JsonProperty("credits") val actors: List<Credit> = emptyList(),
    @JsonProperty("season_count") val seasonCount: Int = 1,
    @JsonProperty("seasons") val seasons: List<Season> = emptyList(),
    @JsonProperty("videos") val videos: List<Video> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LastEpisode(
    @JsonProperty("title_id") val titleId: Int? = null,
    @JsonProperty("title_name") val titleName: String? = null,
    @JsonProperty("title_poster") val titlePoster: String? = null,
    @JsonProperty("season_number") val seasonNumber: Int? = 1,
    @JsonProperty("episode_number") val episodeNumber: Int? = 1
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LastEpisodesResponse(
    @JsonProperty("data") val data: List<LastEpisode>? = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Genre(
    @JsonProperty("display_name") val name: String? = null,
    @JsonProperty("name") val fallbackName: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Credit(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("poster") val poster: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Video(
    @JsonProperty("episode_num") val episodeNum: Int? = null,
    @JsonProperty("season_num") val seasonNum: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("thumbnail") val thumbnail: String? = null,
    @JsonProperty("url") val url: String? = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TitleVideos(
    @JsonProperty("videos") val videos: List<Video>? = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Season(
    @JsonProperty("number") val number: Int = 1
)

package com.pingucodu.us.data.network

import kotlinx.serialization.Serializable

@Serializable
data class StashItemDto(
    val id: String,
    val type: String,
    val author: String,
    val title: String,
    val body: String?,
    val url: String? = null,
    val tags: List<String>,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
)

/** One tag in use on at least one item of [type]; the list comes back most recently used first. */
@Serializable
data class StashTagDto(val type: String, val tag: String)

/** Shared shape for both creating (POST) and partially editing (PATCH) a stash item. */
@Serializable
data class StashItemRequest(
    val type: String? = null,
    val title: String? = null,
    val body: String? = null,
    /** Movies and places only; "" clears it on PATCH (null is simply left out of the JSON). */
    val url: String? = null,
    val tags: List<String>? = null,
    val status: String? = null,
)

/** Text-only preview of a movie/place link: `GET /stash/preview`. */
@Serializable
data class LinkPreviewDto(
    val url: String,
    val title: String,
    val description: String? = null,
    /** Lowercase genre names for a movie, e.g. ["heist", "science fiction"]; empty for places. */
    val genres: List<String> = emptyList(),
    val suggestedType: String,
)

/** One film matching a typed name: `GET /stash/movie-search`. [id] is its Wikidata id, passed to
 * `GET /stash/movie-preview` once picked; [description] tells same-named films apart. */
@Serializable
data class MovieSearchResultDto(
    val id: String,
    val title: String,
    val description: String,
)

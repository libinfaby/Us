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
    /** The hangout this item belongs to, if any. */
    val hangoutId: String? = null,
    /** For an activity: the saved place it's tied to, if any, and that place's title. */
    val placeId: String? = null,
    val placeTitle: String? = null,
    /** For a place: how many activities are tied to it. */
    val activityCount: Int = 0,
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
    /** Movies, books and places only; "" clears it on PATCH (null is simply left out of the JSON). */
    val url: String? = null,
    /** "" unlinks it from its hangout on PATCH, like [url]. */
    val hangoutId: String? = null,
    /** Activities only; "" unlinks it from its place on PATCH, like [url]. */
    val placeId: String? = null,
    val tags: List<String>? = null,
    val status: String? = null,
)

/** Text-only preview of a movie/book/place link: `GET /stash/preview`. */
@Serializable
data class LinkPreviewDto(
    val url: String,
    val title: String,
    val description: String? = null,
    /** Lowercase genre names for a movie or book, e.g. ["heist", "science fiction"]; empty for places. */
    val genres: List<String> = emptyList(),
    val suggestedType: String,
)

/** One film or book matching a typed name: `GET /stash/movie-search` / `GET /stash/book-search`.
 * [id] is the film's Wikidata id or the book's Google Books volume id, passed to
 * `GET /stash/movie-preview` / `GET /stash/book-preview` once picked; [description] tells
 * same-named results apart. */
@Serializable
data class SearchResultDto(
    val id: String,
    val title: String,
    val description: String,
)

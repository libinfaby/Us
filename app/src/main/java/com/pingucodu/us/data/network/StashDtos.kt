package com.pingucodu.us.data.network

import kotlinx.serialization.Serializable

@Serializable
data class StashItemDto(
    val id: String,
    val type: String,
    val author: String,
    val title: String,
    val body: String?,
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
    val tags: List<String>? = null,
    val status: String? = null,
)

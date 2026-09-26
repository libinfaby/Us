package com.pingucodu.us.data.network

import kotlinx.serialization.Serializable

@Serializable
data class NudgeDto(
    val id: String,
    val sender: String,
    val message: String,
    val createdAt: String,
)

/** Body for both sending (POST) and editing (PATCH) a note. */
@Serializable
data class NudgeMessageRequest(val message: String)

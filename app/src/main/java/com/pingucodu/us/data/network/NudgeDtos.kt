package com.pingucodu.us.data.network

import kotlinx.serialization.Serializable

@Serializable
data class NudgeDto(
    val id: String,
    val sender: String,
    val message: String,
    val createdAt: String,
)

/** [message] null = let the server pick a random cute one. */
@Serializable
data class SendNudgeRequest(val message: String? = null)

@Serializable
data class LatestNudgeResponse(val nudge: NudgeDto? = null)

package com.pingucodu.us.data.network

import kotlinx.serialization.Serializable

@Serializable
data class CycleLogDto(
    val id: String,
    val logDate: String,
    val flow: String?,
    val note: String?,
    val createdAt: String,
)

@Serializable
data class CycleStatusDto(
    val trackedUser: String?,
    val currentDay: Int?,
    val onPeriod: Boolean,
    val statusLabel: String,
    val predictedNextDate: String?,
)

@Serializable
data class CreateCycleLogRequest(val logDate: String, val flow: String, val note: String? = null)

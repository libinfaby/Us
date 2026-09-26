package com.pingucodu.us.data.network

import kotlinx.serialization.Serializable

@Serializable
data class CycleLogDto(
    val id: String,
    val logDate: String,
    val flow: String?,
    val tags: List<String> = emptyList(),
    val note: String?,
    val partnerNote: String?,
    val createdAt: String,
)

@Serializable
data class CycleLatestEntryDto(
    val logDate: String,
    val tags: List<String> = emptyList(),
    val partnerNote: String?,
)

@Serializable
data class CycleObservationDto(
    val obsDate: String,
    val tags: List<String> = emptyList(),
    val note: String?,
)

@Serializable
data class CycleStatusDto(
    val trackedUser: String?,
    val currentDay: Int?,
    val onPeriod: Boolean,
    val statusLabel: String,
    val predictedNextDate: String?,
    val phase: String?,
    val daysUntilNextPeriod: Int?,
    val avgCycleLength: Int?,
    val avgPeriodLength: Int?,
    val pmsWindowActive: Boolean = false,
    val irregularityMessage: String?,
    val latestEntry: CycleLatestEntryDto?,
    val observation: CycleObservationDto?,
)

@Serializable
data class CreateCycleLogRequest(
    val logDate: String,
    val flow: String? = null,
    val note: String? = null,
    val tags: List<String> = emptyList(),
    val partnerNote: String? = null,
)

@Serializable
data class CreateObservationRequest(
    val tags: List<String> = emptyList(),
    val note: String? = null,
    val date: String? = null,
)

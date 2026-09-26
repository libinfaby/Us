package com.pingucodu.us.data.network

import kotlinx.serialization.Serializable

/**
 * A countdown (future day we're looking forward to) or a milestone (past day we celebrate
 * yearly). The server fills in the computed fields for "today" in IST:
 * countdowns get [daysUntil]/[isPast]; milestones get [years], [nextAnniversary] and [daysUntilNext].
 */
@Serializable
data class SpecialDateDto(
    val id: String,
    val kind: String,
    val title: String,
    val emoji: String? = null,
    val date: String,
    val createdBy: String,
    val createdAt: String,
    val daysUntil: Int? = null,
    val isPast: Boolean = false,
    val years: Int? = null,
    val nextAnniversary: String? = null,
    val daysUntilNext: Int? = null,
)

/** Shared shape for both creating (POST) and editing (PATCH) a date. */
@Serializable
data class SpecialDateRequest(
    val kind: String? = null,
    val title: String? = null,
    val emoji: String? = null,
    val date: String? = null,
)

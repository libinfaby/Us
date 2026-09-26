package com.pingucodu.us.data.network

import kotlinx.serialization.Serializable

@Serializable
data class GoalContributionDto(
    val id: String,
    val goalId: String,
    val username: String,
    /** Negative = a withdrawal. */
    val amountCents: Long,
    val note: String? = null,
    val createdAt: String,
)

@Serializable
data class SavingsGoalDto(
    val id: String,
    val name: String,
    val emoji: String? = null,
    val targetCents: Long,
    val targetDate: String? = null,
    val status: String,
    val createdBy: String,
    val createdAt: String,
    val savedCents: Long = 0,
    val byUser: Map<String, Long> = emptyMap(),
    /** Newest first. */
    val contributions: List<GoalContributionDto> = emptyList(),
)

/** Shared shape for both creating (POST) and editing (PATCH) a goal. */
@Serializable
data class SavingsGoalRequest(
    val name: String? = null,
    val emoji: String? = null,
    val targetCents: Long? = null,
    val targetDate: String? = null,
    val status: String? = null,
)

@Serializable
data class GoalContributionRequest(val amountCents: Long, val note: String? = null)

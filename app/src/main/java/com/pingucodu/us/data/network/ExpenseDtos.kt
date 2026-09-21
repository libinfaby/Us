package com.pingucodu.us.data.network

import kotlinx.serialization.Serializable

@Serializable
data class ExpenseDto(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val amountCents: Long,
    val currency: String,
    val expenseDate: String,
    val location: String? = null,
    val isRecurring: Boolean = false,
    val cadence: String? = null,
    val hangoutId: String? = null,
    val category: String? = null,
    val paidBy: String,
    val splitType: String,
    val split: Map<String, Long>,
    val status: String,
    val createdAt: String,
    val updatedAt: String? = null,
)

/** Shared shape for both creating (POST) and partially editing (PATCH) an expense. */
@Serializable
data class ExpenseRequest(
    val title: String? = null,
    val amountCents: Long? = null,
    val expenseDate: String? = null,
    val subtitle: String? = null,
    val location: String? = null,
    val hangoutId: String? = null,
    val category: String? = null,
    val paidBy: String? = null,
    val splitType: String? = null,
    val split: Map<String, Long>? = null,
    val isRecurring: Boolean? = null,
    val cadence: String? = null,
)

@Serializable
data class HangoutDto(
    val id: String,
    val name: String,
    val startDate: String? = null,
    val endDate: String? = null,
    val createdAt: String,
    val totalCents: Long = 0,
    val memories: List<HangoutMemoryDto> = emptyList(),
)

@Serializable
data class HangoutMemoryDto(
    val id: String,
    val hangoutId: String,
    val author: String,
    val text: String,
    val createdAt: String,
)

@Serializable
data class CreateHangoutRequest(val name: String, val startDate: String? = null, val endDate: String? = null)

/** Shared shape for editing a hangout's name/dates (PATCH). */
@Serializable
data class UpdateHangoutRequest(val name: String? = null, val startDate: String? = null, val endDate: String? = null)

@Serializable
data class HangoutMemoryRequest(val text: String)

@Serializable
data class SettleAllRequest(val hangoutId: String? = null)

@Serializable
data class SettleAllResponse(val ok: Boolean, val settledCount: Int)

@Serializable
data class BalanceResponse(val net: Map<String, Long>)

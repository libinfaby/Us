package com.pingucodu.us.data.money

import com.pingucodu.us.data.local.TokenStore
import com.pingucodu.us.data.network.ApiService
import com.pingucodu.us.data.network.CreateHangoutRequest
import com.pingucodu.us.data.network.ErrorResponse
import com.pingucodu.us.data.network.ExpenseDto
import com.pingucodu.us.data.network.ExpenseRequest
import com.pingucodu.us.data.network.HangoutDto
import com.pingucodu.us.data.network.HangoutMemoryDto
import com.pingucodu.us.data.network.HangoutMemoryRequest
import com.pingucodu.us.data.network.SettleAllRequest
import com.pingucodu.us.data.network.UpdateHangoutRequest
import com.pingucodu.us.data.network.toUserMessage
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ExpensesResult {
    data class Success(val expenses: List<ExpenseDto>) : ExpensesResult
    data class NetworkError(val message: String) : ExpensesResult
}

sealed interface AddExpenseResult {
    data class Success(val expense: ExpenseDto) : AddExpenseResult
    data class ValidationError(val message: String) : AddExpenseResult
    data class NetworkError(val message: String) : AddExpenseResult
}

sealed interface UpdateExpenseResult {
    data class Success(val expense: ExpenseDto) : UpdateExpenseResult
    data class ValidationError(val message: String) : UpdateExpenseResult
    data class NetworkError(val message: String) : UpdateExpenseResult
}

sealed interface DeleteExpenseResult {
    data object Success : DeleteExpenseResult
    data class NetworkError(val message: String) : DeleteExpenseResult
}

sealed interface SettleExpenseResult {
    data class Success(val expense: ExpenseDto) : SettleExpenseResult
    data class NetworkError(val message: String) : SettleExpenseResult
}

sealed interface SettleAllResult {
    data class Success(val settledCount: Int) : SettleAllResult
    data class NetworkError(val message: String) : SettleAllResult
}

sealed interface BalanceResult {
    /** Net cents per username; positive means that user is owed money overall. */
    data class Success(val net: Map<String, Long>) : BalanceResult
    data class NetworkError(val message: String) : BalanceResult
}

sealed interface HangoutsResult {
    data class Success(val hangouts: List<HangoutDto>) : HangoutsResult
    data class NetworkError(val message: String) : HangoutsResult
}

sealed interface CreateHangoutResult {
    data class Success(val hangout: HangoutDto) : CreateHangoutResult
    data class NetworkError(val message: String) : CreateHangoutResult
}

sealed interface DeleteHangoutResult {
    data object Success : DeleteHangoutResult
    data class NetworkError(val message: String) : DeleteHangoutResult
}

sealed interface UpdateHangoutResult {
    data class Success(val hangout: HangoutDto) : UpdateHangoutResult
    data class ValidationError(val message: String) : UpdateHangoutResult
    data class NetworkError(val message: String) : UpdateHangoutResult
}

sealed interface AddMemoryResult {
    data class Success(val memory: HangoutMemoryDto) : AddMemoryResult
    data class ValidationError(val message: String) : AddMemoryResult
    data class NetworkError(val message: String) : AddMemoryResult
}

sealed interface UpdateMemoryResult {
    data class Success(val memory: HangoutMemoryDto) : UpdateMemoryResult
    data class ValidationError(val message: String) : UpdateMemoryResult
    data class NetworkError(val message: String) : UpdateMemoryResult
}

sealed interface DeleteMemoryResult {
    data object Success : DeleteMemoryResult
    data class NetworkError(val message: String) : DeleteMemoryResult
}

@Singleton
class ExpenseRepository @Inject constructor(
    private val api: ApiService,
    private val tokenStore: TokenStore,
    private val json: Json,
) {
    private suspend fun bearerToken(): String? = tokenStore.token.first()?.let { "Bearer $it" }

    private fun errorMessage(response: Response<*>): String {
        val raw = response.errorBody()?.string()
        val parsed = raw?.let { runCatching { json.decodeFromString<ErrorResponse>(it) }.getOrNull() }
        return parsed?.error ?: "unexpected error (${response.code()})"
    }

    suspend fun getExpenses(status: String = "open", hangoutId: String? = null): ExpensesResult {
        val token = bearerToken() ?: return ExpensesResult.NetworkError("not logged in")
        val response = try {
            api.getExpenses(token, status, hangoutId)
        } catch (e: IOException) {
            return ExpensesResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            ExpensesResult.Success(body)
        } else {
            ExpensesResult.NetworkError(errorMessage(response))
        }
    }

    suspend fun addExpense(request: ExpenseRequest): AddExpenseResult {
        val token = bearerToken() ?: return AddExpenseResult.NetworkError("not logged in")
        val response = try {
            api.createExpense(token, request)
        } catch (e: IOException) {
            return AddExpenseResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return when {
            response.isSuccessful && body != null -> AddExpenseResult.Success(body)
            response.code() == 400 -> AddExpenseResult.ValidationError(errorMessage(response))
            else -> AddExpenseResult.NetworkError(errorMessage(response))
        }
    }

    suspend fun updateExpense(id: String, request: ExpenseRequest): UpdateExpenseResult {
        val token = bearerToken() ?: return UpdateExpenseResult.NetworkError("not logged in")
        val response = try {
            api.updateExpense(token, id, request)
        } catch (e: IOException) {
            return UpdateExpenseResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return when {
            response.isSuccessful && body != null -> UpdateExpenseResult.Success(body)
            response.code() == 400 -> UpdateExpenseResult.ValidationError(errorMessage(response))
            else -> UpdateExpenseResult.NetworkError(errorMessage(response))
        }
    }

    suspend fun deleteExpense(id: String): DeleteExpenseResult {
        val token = bearerToken() ?: return DeleteExpenseResult.NetworkError("not logged in")
        val response = try {
            api.deleteExpense(token, id)
        } catch (e: IOException) {
            return DeleteExpenseResult.NetworkError(e.toUserMessage())
        }
        return if (response.isSuccessful) DeleteExpenseResult.Success else DeleteExpenseResult.NetworkError(errorMessage(response))
    }

    suspend fun settleExpense(id: String): SettleExpenseResult {
        val token = bearerToken() ?: return SettleExpenseResult.NetworkError("not logged in")
        val response = try {
            api.settleExpense(token, id)
        } catch (e: IOException) {
            return SettleExpenseResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            SettleExpenseResult.Success(body)
        } else {
            SettleExpenseResult.NetworkError(errorMessage(response))
        }
    }

    suspend fun settleAll(hangoutId: String? = null): SettleAllResult {
        val token = bearerToken() ?: return SettleAllResult.NetworkError("not logged in")
        val response = try {
            api.settleAll(token, SettleAllRequest(hangoutId))
        } catch (e: IOException) {
            return SettleAllResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            SettleAllResult.Success(body.settledCount)
        } else {
            SettleAllResult.NetworkError(errorMessage(response))
        }
    }

    suspend fun getBalance(): BalanceResult {
        val token = bearerToken() ?: return BalanceResult.NetworkError("not logged in")
        val response = try {
            api.getBalance(token)
        } catch (e: IOException) {
            return BalanceResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            BalanceResult.Success(body.net)
        } else {
            BalanceResult.NetworkError(errorMessage(response))
        }
    }

    suspend fun getHangouts(): HangoutsResult {
        val token = bearerToken() ?: return HangoutsResult.NetworkError("not logged in")
        val response = try {
            api.getHangouts(token)
        } catch (e: IOException) {
            return HangoutsResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            HangoutsResult.Success(body)
        } else {
            HangoutsResult.NetworkError(errorMessage(response))
        }
    }

    suspend fun createHangout(name: String, startDate: String? = null, endDate: String? = null): CreateHangoutResult {
        val token = bearerToken() ?: return CreateHangoutResult.NetworkError("not logged in")
        val response = try {
            api.createHangout(token, CreateHangoutRequest(name, startDate, endDate))
        } catch (e: IOException) {
            return CreateHangoutResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            CreateHangoutResult.Success(body)
        } else {
            CreateHangoutResult.NetworkError(errorMessage(response))
        }
    }

    suspend fun updateHangout(id: String, name: String, startDate: String?, endDate: String?): UpdateHangoutResult {
        val token = bearerToken() ?: return UpdateHangoutResult.NetworkError("not logged in")
        val response = try {
            api.updateHangout(token, id, UpdateHangoutRequest(name, startDate, endDate))
        } catch (e: IOException) {
            return UpdateHangoutResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return when {
            response.isSuccessful && body != null -> UpdateHangoutResult.Success(body)
            response.code() == 400 -> UpdateHangoutResult.ValidationError(errorMessage(response))
            else -> UpdateHangoutResult.NetworkError(errorMessage(response))
        }
    }

    suspend fun deleteHangout(id: String): DeleteHangoutResult {
        val token = bearerToken() ?: return DeleteHangoutResult.NetworkError("not logged in")
        val response = try {
            api.deleteHangout(token, id)
        } catch (e: IOException) {
            return DeleteHangoutResult.NetworkError(e.toUserMessage())
        }
        return if (response.isSuccessful) DeleteHangoutResult.Success else DeleteHangoutResult.NetworkError(errorMessage(response))
    }

    suspend fun addMemory(hangoutId: String, text: String): AddMemoryResult {
        val token = bearerToken() ?: return AddMemoryResult.NetworkError("not logged in")
        val response = try {
            api.addHangoutMemory(token, hangoutId, HangoutMemoryRequest(text))
        } catch (e: IOException) {
            return AddMemoryResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return when {
            response.isSuccessful && body != null -> AddMemoryResult.Success(body)
            response.code() == 400 -> AddMemoryResult.ValidationError(errorMessage(response))
            else -> AddMemoryResult.NetworkError(errorMessage(response))
        }
    }

    suspend fun updateMemory(hangoutId: String, memoryId: String, text: String): UpdateMemoryResult {
        val token = bearerToken() ?: return UpdateMemoryResult.NetworkError("not logged in")
        val response = try {
            api.updateHangoutMemory(token, hangoutId, memoryId, HangoutMemoryRequest(text))
        } catch (e: IOException) {
            return UpdateMemoryResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return when {
            response.isSuccessful && body != null -> UpdateMemoryResult.Success(body)
            response.code() == 400 -> UpdateMemoryResult.ValidationError(errorMessage(response))
            else -> UpdateMemoryResult.NetworkError(errorMessage(response))
        }
    }

    suspend fun deleteMemory(hangoutId: String, memoryId: String): DeleteMemoryResult {
        val token = bearerToken() ?: return DeleteMemoryResult.NetworkError("not logged in")
        val response = try {
            api.deleteHangoutMemory(token, hangoutId, memoryId)
        } catch (e: IOException) {
            return DeleteMemoryResult.NetworkError(e.toUserMessage())
        }
        return if (response.isSuccessful) DeleteMemoryResult.Success else DeleteMemoryResult.NetworkError(errorMessage(response))
    }
}

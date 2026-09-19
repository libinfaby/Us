package com.pingucodu.us.data.money

import com.pingucodu.us.data.local.TokenStore
import com.pingucodu.us.data.network.ApiService
import com.pingucodu.us.data.network.CreateHangoutRequest
import com.pingucodu.us.data.network.ErrorResponse
import com.pingucodu.us.data.network.ExpenseDto
import com.pingucodu.us.data.network.ExpenseRequest
import com.pingucodu.us.data.network.HangoutDto
import com.pingucodu.us.data.network.SettleAllRequest
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
            return ExpensesResult.NetworkError(e.message ?: "couldn't reach the server")
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
            return AddExpenseResult.NetworkError(e.message ?: "couldn't reach the server")
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
            return UpdateExpenseResult.NetworkError(e.message ?: "couldn't reach the server")
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
            return DeleteExpenseResult.NetworkError(e.message ?: "couldn't reach the server")
        }
        return if (response.isSuccessful) DeleteExpenseResult.Success else DeleteExpenseResult.NetworkError(errorMessage(response))
    }

    suspend fun settleExpense(id: String): SettleExpenseResult {
        val token = bearerToken() ?: return SettleExpenseResult.NetworkError("not logged in")
        val response = try {
            api.settleExpense(token, id)
        } catch (e: IOException) {
            return SettleExpenseResult.NetworkError(e.message ?: "couldn't reach the server")
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
            return SettleAllResult.NetworkError(e.message ?: "couldn't reach the server")
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
            return BalanceResult.NetworkError(e.message ?: "couldn't reach the server")
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
            return HangoutsResult.NetworkError(e.message ?: "couldn't reach the server")
        }
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            HangoutsResult.Success(body)
        } else {
            HangoutsResult.NetworkError(errorMessage(response))
        }
    }

    suspend fun createHangout(name: String): CreateHangoutResult {
        val token = bearerToken() ?: return CreateHangoutResult.NetworkError("not logged in")
        val response = try {
            api.createHangout(token, CreateHangoutRequest(name))
        } catch (e: IOException) {
            return CreateHangoutResult.NetworkError(e.message ?: "couldn't reach the server")
        }
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            CreateHangoutResult.Success(body)
        } else {
            CreateHangoutResult.NetworkError(errorMessage(response))
        }
    }

    suspend fun deleteHangout(id: String): DeleteHangoutResult {
        val token = bearerToken() ?: return DeleteHangoutResult.NetworkError("not logged in")
        val response = try {
            api.deleteHangout(token, id)
        } catch (e: IOException) {
            return DeleteHangoutResult.NetworkError(e.message ?: "couldn't reach the server")
        }
        return if (response.isSuccessful) DeleteHangoutResult.Success else DeleteHangoutResult.NetworkError(errorMessage(response))
    }
}

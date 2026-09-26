package com.pingucodu.us.data.goals

import com.pingucodu.us.data.local.TokenStore
import com.pingucodu.us.data.network.ApiService
import com.pingucodu.us.data.network.ErrorResponse
import com.pingucodu.us.data.network.GoalContributionRequest
import com.pingucodu.us.data.network.SavingsGoalDto
import com.pingucodu.us.data.network.SavingsGoalRequest
import com.pingucodu.us.data.network.toUserMessage
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface GoalsResult {
    data class Success(val goals: List<SavingsGoalDto>) : GoalsResult
    data class NetworkError(val message: String) : GoalsResult
}

/** Result of any call that returns the updated goal: create, edit, add or delete a contribution. */
sealed interface SaveGoalResult {
    data class Success(val goal: SavingsGoalDto) : SaveGoalResult
    data class ValidationError(val message: String) : SaveGoalResult
    data class NetworkError(val message: String) : SaveGoalResult
}

sealed interface DeleteGoalResult {
    data object Success : DeleteGoalResult
    data class NetworkError(val message: String) : DeleteGoalResult
}

@Singleton
class GoalsRepository @Inject constructor(
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

    /** [status] is active, reached, archived, or everything. */
    suspend fun getGoals(status: String = "active"): GoalsResult {
        val token = bearerToken() ?: return GoalsResult.NetworkError("not logged in")
        val response = try {
            api.getGoals(token, status)
        } catch (e: IOException) {
            return GoalsResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            GoalsResult.Success(body)
        } else {
            GoalsResult.NetworkError(errorMessage(response))
        }
    }

    /** Creates when [id] is null, otherwise edits. */
    suspend fun saveGoal(id: String?, request: SavingsGoalRequest): SaveGoalResult =
        saveCall { token -> if (id == null) api.createGoal(token, request) else api.updateGoal(token, id, request) }

    /** Positive [amountCents] adds money, negative takes it out. */
    suspend fun addContribution(goalId: String, amountCents: Long, note: String?): SaveGoalResult =
        saveCall { token -> api.addGoalContribution(token, goalId, GoalContributionRequest(amountCents, note)) }

    suspend fun deleteContribution(goalId: String, contributionId: String): SaveGoalResult =
        saveCall { token -> api.deleteGoalContribution(token, goalId, contributionId) }

    suspend fun deleteGoal(id: String): DeleteGoalResult {
        val token = bearerToken() ?: return DeleteGoalResult.NetworkError("not logged in")
        val response = try {
            api.deleteGoal(token, id)
        } catch (e: IOException) {
            return DeleteGoalResult.NetworkError(e.toUserMessage())
        }
        return if (response.isSuccessful) {
            DeleteGoalResult.Success
        } else {
            DeleteGoalResult.NetworkError(errorMessage(response))
        }
    }

    private suspend fun saveCall(call: suspend (token: String) -> Response<SavingsGoalDto>): SaveGoalResult {
        val token = bearerToken() ?: return SaveGoalResult.NetworkError("not logged in")
        val response = try {
            call(token)
        } catch (e: IOException) {
            return SaveGoalResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return when {
            response.isSuccessful && body != null -> SaveGoalResult.Success(body)
            response.code() == 400 || response.code() == 403 -> SaveGoalResult.ValidationError(errorMessage(response))
            else -> SaveGoalResult.NetworkError(errorMessage(response))
        }
    }
}

package com.pingucodu.us.data.nudge

import com.pingucodu.us.data.local.TokenStore
import com.pingucodu.us.data.network.ApiService
import com.pingucodu.us.data.network.ErrorResponse
import com.pingucodu.us.data.network.NudgeDto
import com.pingucodu.us.data.network.NudgeMessageRequest
import com.pingucodu.us.data.network.toUserMessage
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SendNudgeResult {
    data class Success(val nudge: NudgeDto) : SendNudgeResult
    /** Rate-limited or invalid message - [message] is the server's friendly reason. */
    data class Rejected(val message: String) : SendNudgeResult
    data class NetworkError(val message: String) : SendNudgeResult
}

sealed interface TodayNudgesResult {
    data class Success(val nudges: List<NudgeDto>) : TodayNudgesResult
    data class NetworkError(val message: String) : TodayNudgesResult
}

sealed interface DeleteNudgeResult {
    data object Success : DeleteNudgeResult
    data class NetworkError(val message: String) : DeleteNudgeResult
}

@Singleton
class NudgeRepository @Inject constructor(
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

    suspend fun sendNudge(message: String): SendNudgeResult {
        val token = bearerToken() ?: return SendNudgeResult.NetworkError("not logged in")
        val response = try {
            api.sendNudge(token, NudgeMessageRequest(message))
        } catch (e: IOException) {
            return SendNudgeResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return when {
            response.isSuccessful && body != null -> SendNudgeResult.Success(body)
            response.code() == 400 || response.code() == 429 -> SendNudgeResult.Rejected(errorMessage(response))
            else -> SendNudgeResult.NetworkError(errorMessage(response))
        }
    }

    /** Edits one of my own notes; reuses [SendNudgeResult] since the outcomes are the same. */
    suspend fun updateNudge(id: String, message: String): SendNudgeResult {
        val token = bearerToken() ?: return SendNudgeResult.NetworkError("not logged in")
        val response = try {
            api.updateNudge(token, id, NudgeMessageRequest(message))
        } catch (e: IOException) {
            return SendNudgeResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return when {
            response.isSuccessful && body != null -> SendNudgeResult.Success(body)
            response.code() == 400 || response.code() == 403 -> SendNudgeResult.Rejected(errorMessage(response))
            else -> SendNudgeResult.NetworkError(errorMessage(response))
        }
    }

    suspend fun deleteNudge(id: String): DeleteNudgeResult {
        val token = bearerToken() ?: return DeleteNudgeResult.NetworkError("not logged in")
        val response = try {
            api.deleteNudge(token, id)
        } catch (e: IOException) {
            return DeleteNudgeResult.NetworkError(e.toUserMessage())
        }
        return if (response.isSuccessful) DeleteNudgeResult.Success else DeleteNudgeResult.NetworkError(errorMessage(response))
    }

    /** Every note either of us sent today (IST), oldest first. */
    suspend fun getToday(): TodayNudgesResult {
        val token = bearerToken() ?: return TodayNudgesResult.NetworkError("not logged in")
        val response = try {
            api.getTodayNudges(token)
        } catch (e: IOException) {
            return TodayNudgesResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            TodayNudgesResult.Success(body)
        } else {
            TodayNudgesResult.NetworkError(errorMessage(response))
        }
    }
}

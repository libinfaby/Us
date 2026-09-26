package com.pingucodu.us.data.nudge

import com.pingucodu.us.data.local.TokenStore
import com.pingucodu.us.data.network.ApiService
import com.pingucodu.us.data.network.ErrorResponse
import com.pingucodu.us.data.network.NudgeDto
import com.pingucodu.us.data.network.SendNudgeRequest
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

sealed interface LatestNudgeResult {
    data class Success(val nudge: NudgeDto?) : LatestNudgeResult
    data class NetworkError(val message: String) : LatestNudgeResult
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

    /** [message] null = the server picks a random one. */
    suspend fun sendNudge(message: String? = null): SendNudgeResult {
        val token = bearerToken() ?: return SendNudgeResult.NetworkError("not logged in")
        val response = try {
            api.sendNudge(token, SendNudgeRequest(message))
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

    suspend fun getLatest(): LatestNudgeResult {
        val token = bearerToken() ?: return LatestNudgeResult.NetworkError("not logged in")
        val response = try {
            api.getLatestNudge(token)
        } catch (e: IOException) {
            return LatestNudgeResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            LatestNudgeResult.Success(body.nudge)
        } else {
            LatestNudgeResult.NetworkError(errorMessage(response))
        }
    }
}

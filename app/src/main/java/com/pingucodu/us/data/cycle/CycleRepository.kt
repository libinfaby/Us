package com.pingucodu.us.data.cycle

import com.pingucodu.us.data.local.TokenStore
import com.pingucodu.us.data.network.ApiService
import com.pingucodu.us.data.network.CreateCycleLogRequest
import com.pingucodu.us.data.network.CycleLogDto
import com.pingucodu.us.data.network.CycleStatusDto
import com.pingucodu.us.data.network.ErrorResponse
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CycleStatusResult {
    data class Success(val status: CycleStatusDto) : CycleStatusResult
    data class NetworkError(val message: String) : CycleStatusResult
}

sealed interface CycleLogsResult {
    data class Success(val logs: List<CycleLogDto>) : CycleLogsResult
    data class NetworkError(val message: String) : CycleLogsResult
}

sealed interface AddCycleLogResult {
    data class Success(val log: CycleLogDto) : AddCycleLogResult
    data class Forbidden(val message: String) : AddCycleLogResult
    data class NetworkError(val message: String) : AddCycleLogResult
}

sealed interface DeleteCycleLogResult {
    data object Success : DeleteCycleLogResult
    data class Forbidden(val message: String) : DeleteCycleLogResult
    data class NetworkError(val message: String) : DeleteCycleLogResult
}

@Singleton
class CycleRepository @Inject constructor(
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

    suspend fun getStatus(): CycleStatusResult {
        val token = bearerToken() ?: return CycleStatusResult.NetworkError("not logged in")
        val response = try {
            api.getCycleStatus(token)
        } catch (e: IOException) {
            return CycleStatusResult.NetworkError(e.message ?: "couldn't reach the server")
        }
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            CycleStatusResult.Success(body)
        } else {
            CycleStatusResult.NetworkError(errorMessage(response))
        }
    }

    suspend fun getLogs(): CycleLogsResult {
        val token = bearerToken() ?: return CycleLogsResult.NetworkError("not logged in")
        val response = try {
            api.getCycleLogs(token)
        } catch (e: IOException) {
            return CycleLogsResult.NetworkError(e.message ?: "couldn't reach the server")
        }
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            CycleLogsResult.Success(body)
        } else {
            CycleLogsResult.NetworkError(errorMessage(response))
        }
    }

    suspend fun addLog(logDate: String, flow: String, note: String?): AddCycleLogResult {
        val token = bearerToken() ?: return AddCycleLogResult.NetworkError("not logged in")
        val response = try {
            api.createCycleLog(token, CreateCycleLogRequest(logDate, flow, note))
        } catch (e: IOException) {
            return AddCycleLogResult.NetworkError(e.message ?: "couldn't reach the server")
        }
        val body = response.body()
        return when {
            response.isSuccessful && body != null -> AddCycleLogResult.Success(body)
            response.code() == 403 -> AddCycleLogResult.Forbidden(errorMessage(response))
            else -> AddCycleLogResult.NetworkError(errorMessage(response))
        }
    }

    suspend fun deleteLog(id: String): DeleteCycleLogResult {
        val token = bearerToken() ?: return DeleteCycleLogResult.NetworkError("not logged in")
        val response = try {
            api.deleteCycleLog(token, id)
        } catch (e: IOException) {
            return DeleteCycleLogResult.NetworkError(e.message ?: "couldn't reach the server")
        }
        return when {
            response.isSuccessful -> DeleteCycleLogResult.Success
            response.code() == 403 -> DeleteCycleLogResult.Forbidden(errorMessage(response))
            else -> DeleteCycleLogResult.NetworkError(errorMessage(response))
        }
    }
}

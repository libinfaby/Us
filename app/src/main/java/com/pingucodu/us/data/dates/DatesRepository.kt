package com.pingucodu.us.data.dates

import com.pingucodu.us.data.local.TokenStore
import com.pingucodu.us.data.network.ApiService
import com.pingucodu.us.data.network.ErrorResponse
import com.pingucodu.us.data.network.SpecialDateDto
import com.pingucodu.us.data.network.SpecialDateRequest
import com.pingucodu.us.data.network.toUserMessage
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DatesResult {
    data class Success(val dates: List<SpecialDateDto>) : DatesResult
    data class NetworkError(val message: String) : DatesResult
}

sealed interface SaveDateResult {
    data class Success(val date: SpecialDateDto) : SaveDateResult
    data class ValidationError(val message: String) : SaveDateResult
    data class NetworkError(val message: String) : SaveDateResult
}

sealed interface DeleteDateResult {
    data object Success : DeleteDateResult
    data class NetworkError(val message: String) : DeleteDateResult
}

@Singleton
class DatesRepository @Inject constructor(
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

    /** Server-sorted: upcoming countdowns, then milestones by next anniversary, then past countdowns. */
    suspend fun getDates(): DatesResult {
        val token = bearerToken() ?: return DatesResult.NetworkError("not logged in")
        val response = try {
            api.getDates(token)
        } catch (e: IOException) {
            return DatesResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            DatesResult.Success(body)
        } else {
            DatesResult.NetworkError(errorMessage(response))
        }
    }

    /** Creates when [id] is null, otherwise edits. */
    suspend fun saveDate(id: String?, request: SpecialDateRequest): SaveDateResult {
        val token = bearerToken() ?: return SaveDateResult.NetworkError("not logged in")
        val response = try {
            if (id == null) api.createDate(token, request) else api.updateDate(token, id, request)
        } catch (e: IOException) {
            return SaveDateResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return when {
            response.isSuccessful && body != null -> SaveDateResult.Success(body)
            response.code() == 400 -> SaveDateResult.ValidationError(errorMessage(response))
            else -> SaveDateResult.NetworkError(errorMessage(response))
        }
    }

    suspend fun deleteDate(id: String): DeleteDateResult {
        val token = bearerToken() ?: return DeleteDateResult.NetworkError("not logged in")
        val response = try {
            api.deleteDate(token, id)
        } catch (e: IOException) {
            return DeleteDateResult.NetworkError(e.toUserMessage())
        }
        return if (response.isSuccessful) {
            DeleteDateResult.Success
        } else {
            DeleteDateResult.NetworkError(errorMessage(response))
        }
    }
}

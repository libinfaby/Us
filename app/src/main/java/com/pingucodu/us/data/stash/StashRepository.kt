package com.pingucodu.us.data.stash

import com.pingucodu.us.data.local.TokenStore
import com.pingucodu.us.data.network.ApiService
import com.pingucodu.us.data.network.ErrorResponse
import com.pingucodu.us.data.network.toUserMessage
import com.pingucodu.us.data.network.StashItemDto
import com.pingucodu.us.data.network.StashItemRequest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface StashItemsResult {
    data class Success(val items: List<StashItemDto>) : StashItemsResult
    data class NetworkError(val message: String) : StashItemsResult
}

sealed interface AddStashItemResult {
    data class Success(val item: StashItemDto) : AddStashItemResult
    data class ValidationError(val message: String) : AddStashItemResult
    data class NetworkError(val message: String) : AddStashItemResult
}

sealed interface UpdateStashItemResult {
    data class Success(val item: StashItemDto) : UpdateStashItemResult
    data class ValidationError(val message: String) : UpdateStashItemResult
    data class NetworkError(val message: String) : UpdateStashItemResult
}

sealed interface ToggleStashItemResult {
    data class Success(val item: StashItemDto) : ToggleStashItemResult
    data class NetworkError(val message: String) : ToggleStashItemResult
}

sealed interface DeleteStashItemResult {
    data object Success : DeleteStashItemResult
    data class NetworkError(val message: String) : DeleteStashItemResult
}

@Singleton
class StashRepository @Inject constructor(
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

    suspend fun getItems(status: String = "saved", type: String? = null): StashItemsResult {
        val token = bearerToken() ?: return StashItemsResult.NetworkError("not logged in")
        val response = try {
            api.getStash(token, status, type)
        } catch (e: IOException) {
            return StashItemsResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            StashItemsResult.Success(body)
        } else {
            StashItemsResult.NetworkError(errorMessage(response))
        }
    }

    suspend fun addItem(request: StashItemRequest): AddStashItemResult {
        val token = bearerToken() ?: return AddStashItemResult.NetworkError("not logged in")
        val response = try {
            api.createStashItem(token, request)
        } catch (e: IOException) {
            return AddStashItemResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return when {
            response.isSuccessful && body != null -> AddStashItemResult.Success(body)
            response.code() == 400 -> AddStashItemResult.ValidationError(errorMessage(response))
            else -> AddStashItemResult.NetworkError(errorMessage(response))
        }
    }

    suspend fun updateItem(id: String, request: StashItemRequest): UpdateStashItemResult {
        val token = bearerToken() ?: return UpdateStashItemResult.NetworkError("not logged in")
        val response = try {
            api.updateStashItem(token, id, request)
        } catch (e: IOException) {
            return UpdateStashItemResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return when {
            response.isSuccessful && body != null -> UpdateStashItemResult.Success(body)
            response.code() == 400 -> UpdateStashItemResult.ValidationError(errorMessage(response))
            else -> UpdateStashItemResult.NetworkError(errorMessage(response))
        }
    }

    suspend fun toggleItem(id: String): ToggleStashItemResult {
        val token = bearerToken() ?: return ToggleStashItemResult.NetworkError("not logged in")
        val response = try {
            api.toggleStashItem(token, id)
        } catch (e: IOException) {
            return ToggleStashItemResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            ToggleStashItemResult.Success(body)
        } else {
            ToggleStashItemResult.NetworkError(errorMessage(response))
        }
    }

    suspend fun deleteItem(id: String): DeleteStashItemResult {
        val token = bearerToken() ?: return DeleteStashItemResult.NetworkError("not logged in")
        val response = try {
            api.deleteStashItem(token, id)
        } catch (e: IOException) {
            return DeleteStashItemResult.NetworkError(e.toUserMessage())
        }
        return if (response.isSuccessful) {
            DeleteStashItemResult.Success
        } else {
            DeleteStashItemResult.NetworkError(errorMessage(response))
        }
    }
}

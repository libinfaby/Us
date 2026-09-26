package com.pingucodu.us.data.stash

import com.pingucodu.us.data.local.TokenStore
import com.pingucodu.us.data.network.ApiService
import com.pingucodu.us.data.network.ErrorResponse
import com.pingucodu.us.data.network.LinkPreviewDto
import com.pingucodu.us.data.network.MovieSearchResultDto
import com.pingucodu.us.data.network.toUserMessage
import com.pingucodu.us.data.network.StashItemDto
import com.pingucodu.us.data.network.StashItemRequest
import com.pingucodu.us.data.network.StashTagDto
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

sealed interface StashTagsResult {
    data class Success(val tags: List<StashTagDto>) : StashTagsResult
    data class NetworkError(val message: String) : StashTagsResult
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

sealed interface LinkPreviewResult {
    data class Success(val preview: LinkPreviewDto) : LinkPreviewResult
    /** The link was fine but had nothing readable - the user can still fill it in by hand. */
    data class Unreadable(val message: String) : LinkPreviewResult
    data class NetworkError(val message: String) : LinkPreviewResult
}

sealed interface MovieSearchResult {
    data class Success(val movies: List<MovieSearchResultDto>) : MovieSearchResult
    data class Failed(val message: String) : MovieSearchResult
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

    suspend fun getItems(
        status: String = "saved",
        type: String? = null,
        tag: String? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): StashItemsResult {
        val token = bearerToken() ?: return StashItemsResult.NetworkError("not logged in")
        val response = try {
            api.getStash(token, status, type, tag, limit, offset)
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

    suspend fun getTags(): StashTagsResult {
        val token = bearerToken() ?: return StashTagsResult.NetworkError("not logged in")
        val response = try {
            api.getStashTags(token)
        } catch (e: IOException) {
            return StashTagsResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            StashTagsResult.Success(body)
        } else {
            StashTagsResult.NetworkError(errorMessage(response))
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

    suspend fun getLinkPreview(url: String): LinkPreviewResult {
        val token = bearerToken() ?: return LinkPreviewResult.NetworkError("not logged in")
        val response = try {
            api.getLinkPreview(token, url)
        } catch (e: IOException) {
            return LinkPreviewResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return when {
            response.isSuccessful && body != null -> LinkPreviewResult.Success(body)
            response.code() == 400 || response.code() == 422 -> LinkPreviewResult.Unreadable(errorMessage(response))
            else -> LinkPreviewResult.NetworkError(errorMessage(response))
        }
    }

    suspend fun searchMovies(query: String): MovieSearchResult {
        val token = bearerToken() ?: return MovieSearchResult.Failed("not logged in")
        val response = try {
            api.searchMovies(token, query)
        } catch (e: IOException) {
            return MovieSearchResult.Failed(e.toUserMessage())
        }
        val body = response.body()
        return if (response.isSuccessful && body != null) MovieSearchResult.Success(body) else MovieSearchResult.Failed(errorMessage(response))
    }

    /** The same preview a pasted IMDb link gives, for a film picked from [searchMovies]. */
    suspend fun getMoviePreview(id: String): LinkPreviewResult {
        val token = bearerToken() ?: return LinkPreviewResult.NetworkError("not logged in")
        val response = try {
            api.getMoviePreview(token, id)
        } catch (e: IOException) {
            return LinkPreviewResult.NetworkError(e.toUserMessage())
        }
        val body = response.body()
        return when {
            response.isSuccessful && body != null -> LinkPreviewResult.Success(body)
            response.code() == 422 -> LinkPreviewResult.Unreadable(errorMessage(response))
            else -> LinkPreviewResult.NetworkError(errorMessage(response))
        }
    }
}

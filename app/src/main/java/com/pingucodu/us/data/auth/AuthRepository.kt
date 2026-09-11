package com.pingucodu.us.data.auth

import com.pingucodu.us.data.local.TokenStore
import com.pingucodu.us.data.network.ApiService
import com.pingucodu.us.data.network.LoginRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LoginResult {
    data object Success : LoginResult
    data object WrongPin : LoginResult
    data object PinNotSetUp : LoginResult
    data class NetworkError(val message: String) : LoginResult
}

@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val tokenStore: TokenStore,
) {
    /** Non-null only once both a token and username are persisted. */
    val loggedInUsername: Flow<String?> =
        combine(tokenStore.token, tokenStore.username) { token, username ->
            if (token != null && username != null) username else null
        }

    suspend fun login(username: String, pin: String): LoginResult {
        val response = try {
            api.login(LoginRequest(username, pin))
        } catch (e: IOException) {
            return LoginResult.NetworkError(e.message ?: "couldn't reach the server")
        }

        return when (response.code()) {
            200 -> {
                val body = response.body() ?: return LoginResult.NetworkError("empty response")
                tokenStore.save(body.token, body.username)
                LoginResult.Success
            }
            400 -> LoginResult.PinNotSetUp
            401 -> LoginResult.WrongPin
            else -> LoginResult.NetworkError("unexpected error (${response.code()})")
        }
    }

    suspend fun logout() {
        tokenStore.clear()
    }
}

package com.pingucodu.us.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth/change-pin")
    suspend fun changePin(
        @Header("Authorization") bearerToken: String,
        @Body request: ChangePinRequest,
    ): Response<Unit>
}

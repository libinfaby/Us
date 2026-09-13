package com.pingucodu.us.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth/change-pin")
    suspend fun changePin(
        @Header("Authorization") bearerToken: String,
        @Body request: ChangePinRequest,
    ): Response<Unit>

    @GET("expenses")
    suspend fun getExpenses(
        @Header("Authorization") bearerToken: String,
        @Query("status") status: String? = null,
        @Query("hangoutId") hangoutId: String? = null,
    ): Response<List<ExpenseDto>>

    @POST("expenses")
    suspend fun createExpense(
        @Header("Authorization") bearerToken: String,
        @Body request: ExpenseRequest,
    ): Response<ExpenseDto>

    @PATCH("expenses/{id}")
    suspend fun updateExpense(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
        @Body request: ExpenseRequest,
    ): Response<ExpenseDto>

    @DELETE("expenses/{id}")
    suspend fun deleteExpense(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
    ): Response<Unit>

    @POST("expenses/{id}/settle")
    suspend fun settleExpense(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
    ): Response<ExpenseDto>

    @POST("expenses/settle-all")
    suspend fun settleAll(
        @Header("Authorization") bearerToken: String,
        @Body request: SettleAllRequest,
    ): Response<SettleAllResponse>

    @GET("expenses/balance")
    suspend fun getBalance(@Header("Authorization") bearerToken: String): Response<BalanceResponse>

    @GET("hangouts")
    suspend fun getHangouts(@Header("Authorization") bearerToken: String): Response<List<HangoutDto>>

    @POST("hangouts")
    suspend fun createHangout(
        @Header("Authorization") bearerToken: String,
        @Body request: CreateHangoutRequest,
    ): Response<HangoutDto>
}

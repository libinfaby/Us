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

    @PATCH("hangouts/{id}")
    suspend fun updateHangout(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
        @Body request: UpdateHangoutRequest,
    ): Response<HangoutDto>

    @DELETE("hangouts/{id}")
    suspend fun deleteHangout(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
    ): Response<Unit>

    @POST("hangouts/{id}/memories")
    suspend fun addHangoutMemory(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
        @Body request: HangoutMemoryRequest,
    ): Response<HangoutMemoryDto>

    @PATCH("hangouts/{id}/memories/{memoryId}")
    suspend fun updateHangoutMemory(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
        @Path("memoryId") memoryId: String,
        @Body request: HangoutMemoryRequest,
    ): Response<HangoutMemoryDto>

    @DELETE("hangouts/{id}/memories/{memoryId}")
    suspend fun deleteHangoutMemory(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
        @Path("memoryId") memoryId: String,
    ): Response<Unit>

    @GET("cycle/status")
    suspend fun getCycleStatus(@Header("Authorization") bearerToken: String): Response<CycleStatusDto>

    @GET("cycle/logs")
    suspend fun getCycleLogs(@Header("Authorization") bearerToken: String): Response<List<CycleLogDto>>

    @POST("cycle/logs")
    suspend fun createCycleLog(
        @Header("Authorization") bearerToken: String,
        @Body request: CreateCycleLogRequest,
    ): Response<CycleLogDto>

    @DELETE("cycle/logs/{id}")
    suspend fun deleteCycleLog(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
    ): Response<Unit>

    @GET("cycle/observations")
    suspend fun getObservations(@Header("Authorization") bearerToken: String): Response<List<CycleObservationDto>>

    @POST("cycle/observations")
    suspend fun createObservation(
        @Header("Authorization") bearerToken: String,
        @Body request: CreateObservationRequest,
    ): Response<CycleObservationDto>

    @DELETE("cycle/observations/{date}")
    suspend fun deleteObservation(
        @Header("Authorization") bearerToken: String,
        @Path("date") date: String,
    ): Response<Unit>

    @GET("stash")
    suspend fun getStash(
        @Header("Authorization") bearerToken: String,
        @Query("status") status: String? = null,
        @Query("type") type: String? = null,
        @Query("tag") tag: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): Response<List<StashItemDto>>

    @GET("stash/tags")
    suspend fun getStashTags(@Header("Authorization") bearerToken: String): Response<List<StashTagDto>>

    @POST("stash")
    suspend fun createStashItem(
        @Header("Authorization") bearerToken: String,
        @Body request: StashItemRequest,
    ): Response<StashItemDto>

    @PATCH("stash/{id}")
    suspend fun updateStashItem(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
        @Body request: StashItemRequest,
    ): Response<StashItemDto>

    @POST("stash/{id}/toggle")
    suspend fun toggleStashItem(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
    ): Response<StashItemDto>

    @DELETE("stash/{id}")
    suspend fun deleteStashItem(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
    ): Response<Unit>

    @POST("devices")
    suspend fun registerDeviceToken(
        @Header("Authorization") bearerToken: String,
        @Body request: RegisterDeviceTokenRequest,
    ): Response<Unit>

    @GET("stash/preview")
    suspend fun getLinkPreview(
        @Header("Authorization") bearerToken: String,
        @Query("url") url: String,
    ): Response<LinkPreviewDto>

    @POST("nudges")
    suspend fun sendNudge(
        @Header("Authorization") bearerToken: String,
        @Body request: NudgeMessageRequest,
    ): Response<NudgeDto>

    @GET("nudges/today")
    suspend fun getTodayNudges(@Header("Authorization") bearerToken: String): Response<List<NudgeDto>>

    @PATCH("nudges/{id}")
    suspend fun updateNudge(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
        @Body request: NudgeMessageRequest,
    ): Response<NudgeDto>

    @DELETE("nudges/{id}")
    suspend fun deleteNudge(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
    ): Response<Unit>

    @GET("dates")
    suspend fun getDates(@Header("Authorization") bearerToken: String): Response<List<SpecialDateDto>>

    @POST("dates")
    suspend fun createDate(
        @Header("Authorization") bearerToken: String,
        @Body request: SpecialDateRequest,
    ): Response<SpecialDateDto>

    @PATCH("dates/{id}")
    suspend fun updateDate(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
        @Body request: SpecialDateRequest,
    ): Response<SpecialDateDto>

    @DELETE("dates/{id}")
    suspend fun deleteDate(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
    ): Response<Unit>

    @GET("goals")
    suspend fun getGoals(
        @Header("Authorization") bearerToken: String,
        @Query("status") status: String? = null,
    ): Response<List<SavingsGoalDto>>

    @POST("goals")
    suspend fun createGoal(
        @Header("Authorization") bearerToken: String,
        @Body request: SavingsGoalRequest,
    ): Response<SavingsGoalDto>

    @PATCH("goals/{id}")
    suspend fun updateGoal(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
        @Body request: SavingsGoalRequest,
    ): Response<SavingsGoalDto>

    @DELETE("goals/{id}")
    suspend fun deleteGoal(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
    ): Response<Unit>

    @POST("goals/{id}/contributions")
    suspend fun addGoalContribution(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
        @Body request: GoalContributionRequest,
    ): Response<SavingsGoalDto>

    @DELETE("goals/{id}/contributions/{contributionId}")
    suspend fun deleteGoalContribution(
        @Header("Authorization") bearerToken: String,
        @Path("id") id: String,
        @Path("contributionId") contributionId: String,
    ): Response<SavingsGoalDto>
}

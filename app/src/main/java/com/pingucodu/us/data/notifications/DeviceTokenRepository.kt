package com.pingucodu.us.data.notifications

import com.google.firebase.messaging.FirebaseMessaging
import com.pingucodu.us.data.local.TokenStore
import com.pingucodu.us.data.network.ApiService
import com.pingucodu.us.data.network.RegisterDeviceTokenRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class DeviceTokenRepository @Inject constructor(
    private val api: ApiService,
    private val tokenStore: TokenStore,
) {
    /** Called right after login - registers whatever FCM token this device currently has. */
    suspend fun registerCurrentToken() {
        val fcmToken = try {
            currentFcmToken()
        } catch (e: Exception) {
            return
        }
        registerToken(fcmToken)
    }

    /** Called from FirebaseMessagingService.onNewToken. No-ops if not logged in yet -
     *  registerCurrentToken() backfills on the next login. */
    suspend fun registerToken(fcmToken: String) {
        val token = tokenStore.token.first() ?: return
        try {
            api.registerDeviceToken("Bearer $token", RegisterDeviceTokenRequest(fcmToken))
        } catch (e: IOException) {
            // Best-effort - will retry naturally next time onNewToken or login fires.
        }
    }

    private suspend fun currentFcmToken(): String = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }
}

package com.pingucodu.us.data.network

import kotlinx.serialization.Serializable

@Serializable
data class RegisterDeviceTokenRequest(val fcmToken: String)

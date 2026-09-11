package com.pingucodu.us.data.network

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val username: String, val pin: String)

@Serializable
data class LoginResponse(val token: String, val username: String)

@Serializable
data class ChangePinRequest(val currentPin: String, val newPin: String)

@Serializable
data class ErrorResponse(val error: String)

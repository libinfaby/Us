package com.pingucodu.us.data.network

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Maps a caught network exception to a message safe to show the user. Never surface
 * [IOException.message] directly - e.g. `UnknownHostException`'s message contains the
 * backend's raw hostname ("Unable to resolve host \"pingu-codu-api.<...>.workers.dev\"").
 */
fun IOException.toUserMessage(): String = when (this) {
    is UnknownHostException -> "no internet connection. check your network and try again."
    is SocketTimeoutException -> "the connection timed out. try again."
    else -> "couldn't reach the server. check your connection and try again."
}

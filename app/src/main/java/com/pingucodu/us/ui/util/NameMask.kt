package com.pingucodu.us.ui.util

import androidx.compose.runtime.compositionLocalOf

/** Swaps the app's two hardcoded usernames for user-chosen placeholder labels, for demos. */
data class NameMask(val enabled: Boolean, val pinguLabel: String, val coduLabel: String) {
    fun resolve(raw: String?): String? {
        if (!enabled || raw == null) return raw
        return when (raw.lowercase()) {
            "pingu" -> pinguLabel
            "codu" -> coduLabel
            else -> raw
        }
    }

    /** Inverse of [resolve] - turns whatever the masked label the user typed or tapped back into
     * the real backend username, so login requests always send "pingu"/"codu" regardless of the
     * placeholder label shown on screen. */
    fun unresolve(raw: String): String {
        if (!enabled) return raw
        val trimmed = raw.trim()
        return when {
            trimmed.equals(pinguLabel, ignoreCase = true) -> "pingu"
            trimmed.equals(coduLabel, ignoreCase = true) -> "codu"
            else -> raw
        }
    }
}

val LocalNameMask = compositionLocalOf { NameMask(enabled = false, pinguLabel = "pingu", coduLabel = "codu") }

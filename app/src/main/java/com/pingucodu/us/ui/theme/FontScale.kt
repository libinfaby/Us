package com.pingucodu.us.ui.theme

/**
 * The app's own text-size setting, independent of the device's accessibility font size.
 * Mirrors Android's 4-step font size slider (Small/Default/Large/Largest), but the app
 * defaults to "Large" (index 2) since the design reads cramped at the system default.
 */
enum class FontScaleLevel(val label: String, val scale: Float) {
    SMALL("small", 0.85f),
    DEFAULT("default", 1f),
    LARGE("large", 1.15f),
    LARGEST("largest", 1.3f);

    companion object {
        val Default = LARGE

        fun fromOrdinal(ordinal: Int): FontScaleLevel = entries.getOrElse(ordinal) { Default }
    }
}

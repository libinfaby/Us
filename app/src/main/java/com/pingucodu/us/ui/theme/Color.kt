package com.pingucodu.us.ui.theme

import androidx.compose.ui.graphics.Color

// Extracted from the Claude Design prototype (Pingu and Codu.html).
// Neo-brutalist palette: flat colors, near-black ink for borders/text, cream base.

val Cream = Color(0xFFFFF6E9)
val PinkTint = Color(0xFFFFE4EF)
val Ink = Color(0xFF111111)

val Pink = Color(0xFFFF4FA3)
val Yellow = Color(0xFFFFD84D)
val YellowDeep = Color(0xFFFFC93C)
val YellowSoft = Color(0xFFFFD166)
val Teal = Color(0xFF00CFC8)
val TealSoft = Color(0xFF7FE3DE)
val Green = Color(0xFF7ED957)
val Coral = Color(0xFFFF5A5F)
val Purple = Color(0xFF9B6BFF)
val Orange = Color(0xFFFF8A3D)

val DescriptionGrey = Color(0xFF5D585A)
val PlaceholderGrey = Color(0xFF7E7E7E)

/**
 * Semantic tokens for the app's own neo-brutalist components (cards, chips, tab bar).
 * Kept separate from Material3's ColorScheme because this design's roles (money/cycle/
 * stash per-section accents) don't map cleanly onto primary/secondary/tertiary.
 */
object PinguCoduColors {
    val background = Cream
    val surface = Color.White
    val surfaceTint = PinkTint
    val border = Ink
    val textPrimary = Ink
    val textMuted = DescriptionGrey

    val money = Pink
    val cycle = Teal
    val stash = YellowSoft
    val positive = Green
    val warning = Coral
    val accentPurple = Purple
    val accentOrange = Orange
}

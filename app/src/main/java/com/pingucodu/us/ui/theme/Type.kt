@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.pingucodu.us.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pingucodu.us.R

// Space Grotesk is a variable font (wght axis) - pin the specific weights the design uses.
val SpaceGrotesk = FontFamily(
    Font(
        R.font.space_grotesk,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        R.font.space_grotesk,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        R.font.space_grotesk,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    ),
)

// Archivo Black only ships one weight (~900) - used for the bold display headings.
val ArchivoBlack = FontFamily(
    Font(R.font.archivo_black, weight = FontWeight.Normal),
    Font(R.font.archivo_black, weight = FontWeight.Bold),
)

// DM Mono - amounts, dates, timestamps.
val DmMono = FontFamily(
    Font(R.font.dm_mono_regular, weight = FontWeight.Normal),
    Font(R.font.dm_mono_medium, weight = FontWeight.Medium),
)

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = ArchivoBlack, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    displayMedium = TextStyle(fontFamily = ArchivoBlack, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 30.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontFamily = ArchivoBlack, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontFamily = ArchivoBlack, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = ArchivoBlack, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    titleSmall = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodySmall = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelLarge = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 15.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, lineHeight = 13.sp, letterSpacing = 0.3.sp),
)

/** Extra text styles that don't have a natural Material3 Typography slot. */
object PinguCoduType {
    val amountLarge = TextStyle(fontFamily = DmMono, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 30.sp)
    val amount = TextStyle(fontFamily = DmMono, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp)
    val mono = TextStyle(fontFamily = DmMono, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 16.sp)
    val monoLabel = TextStyle(fontFamily = DmMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp)
}

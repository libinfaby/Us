package com.pingucodu.us.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Corner radii lifted from the design: 10-14dp on cards/inputs/buttons, up to 22-26dp on sheets.
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

object PinguCoduShapes {
    val card = RoundedCornerShape(12.dp)
    val chip = RoundedCornerShape(50)
    val sheet = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
}

/** Border width used on every neo-brutalist card/input/button outline. */
val BorderWidth = 2.5.dp

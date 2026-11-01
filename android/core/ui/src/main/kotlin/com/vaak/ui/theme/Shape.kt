package com.vaak.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** design-system §4 / §9.3. Buttons, chips and the nav pill use CircleShape. */
val VaakShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Metric tiles and better-line rows. */
val TileShape = RoundedCornerShape(18.dp)

/** design-system §3 / §9.4. */
object VaakSpace {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val gutter = 20.dp
    val touch = 48.dp
}

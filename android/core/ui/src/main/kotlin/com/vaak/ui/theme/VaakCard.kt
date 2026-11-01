package com.vaak.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** The one card (design-system §9.1): `card` fill, 1 dp `line` border, flat. Every card, tile and feedback card uses it. */
@Composable
fun VaakCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val elevation = CardDefaults.cardElevation(0.dp)
    if (onClick != null) {
        Card(onClick, modifier, shape = shape, colors = colors, elevation = elevation, border = border, content = content)
    } else {
        Card(modifier, shape = shape, colors = colors, elevation = elevation, border = border, content = content)
    }
}

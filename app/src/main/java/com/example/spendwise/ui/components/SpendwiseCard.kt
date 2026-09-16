package com.example.spendwise.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The only card in the app.
 *
 * Why this exists: before it, cards used three different corner radii
 * (16/18/20dp), only one had an elevation, and none had a border — so on a
 * surface ramp that measured 1.07:1 they dissolved into the page.
 *
 * Layering rule (see UI_UX_AUDIT.md §5.1):
 *   light → tonal step (1.16:1) + 1dp hairline (1.53:1)
 *   dark  → tonal step (1.13:1) + 1dp hairline (1.60:1)
 * Shadows are deliberately NOT used: they do nothing on a near-black
 * background, so relying on them is what made dark mode look flat.
 */
@Composable
fun SpendwiseCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = modifier.fillMaxWidth()
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
    )
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val shape = MaterialTheme.shapes.medium

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            shape = shape,
            colors = colors,
            border = border,
        ) { content() }
    } else {
        Card(
            modifier = cardModifier,
            shape = shape,
            colors = colors,
            border = border,
        ) { content() }
    }
}
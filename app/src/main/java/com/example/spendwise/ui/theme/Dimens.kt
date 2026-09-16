package com.example.spendwise.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The only spacing values allowed. 4pt base, 8 steps. Everything else in the
 * codebase was eyeballed (2/3/5/6/10/14/18/22dp), which is what makes layouts
 * feel almost-aligned rather than crisp.
 */
object Dimens {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp      // the screen gutter — already consistent, keep it
    val xl = 20.dp      // card inner padding
    val xxl = 24.dp     // section gap, sheet padding
    val xxxl = 32.dp    // empty-state outer
    val huge = 48.dp    // hero spacing

    /** Standard card inner padding + card corner, so cards stop diverging. */
    val cardPadding = xl
    val screenGutter = lg
}
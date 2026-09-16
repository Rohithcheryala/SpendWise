package com.example.spendwise.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Five corner roles and nothing else. Before this existed the UI used 13
 * different radii (2/3/4/8/10/12/14/16/18/20/24/28dp + a 50% pill), including
 * three different radii for "a card", which is why nothing looked related.
 */
val SpendwiseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // chips, small tiles, tags
    small = RoundedCornerShape(12.dp),       // text fields, segmented buttons
    medium = RoundedCornerShape(16.dp),      // cards (the default)
    large = RoundedCornerShape(20.dp),       // hero cards, dialogs
    extraLarge = RoundedCornerShape(28.dp),  // bottom sheets
)
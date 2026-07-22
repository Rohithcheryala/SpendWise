package com.example.spendwise.core.extensions

import androidx.compose.ui.graphics.Color

// Top-level operator extension
operator fun Color.div(divisor: Float): Color {
    require(divisor != 0f) { "Cannot divide color by zero" }
    return this.copy(alpha = (this.alpha / divisor).coerceIn(0f, 1f))
}

operator fun Color.div(divisor: Int): Color = this.div(divisor.toFloat())
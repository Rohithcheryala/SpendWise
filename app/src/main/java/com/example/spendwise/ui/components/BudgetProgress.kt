package com.example.spendwise.ui.components


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun BudgetProgress(
    modifier: Modifier = Modifier,
    spent: Double,
    budget: Double
) {
    val progress = if (budget <= 0) 0f else (spent / budget).coerceIn(0.0, 1.0).toFloat()

    Box(
        modifier = modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {

        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.matchParentSize(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 10.dp
        )

        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.matchParentSize(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = androidx.compose.ui.graphics.Color.Transparent,
            strokeWidth = 10.dp
        )

        Text(
            text = "${(progress * 100).roundToInt()}%",
            style = MaterialTheme.typography.titleMedium
        )
    }
}
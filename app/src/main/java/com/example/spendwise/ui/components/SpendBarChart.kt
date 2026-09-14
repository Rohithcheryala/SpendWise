package com.example.spendwise.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hand-rolled 30-day spend bar chart (no charting dependency).
 *
 * Deliberately dependency-free: `ui.graphics` Canvas is already on the
 * classpath, and a bar chart this simple doesn't justify pulling Vico (or
 * anything else) into the build.
 *
 * @param dailySpendPaise one value per day, **oldest first**. Bars with no
 *   spend still draw as a stub so the timeline reads as continuous.
 * @param highlightLastBar marks today (the newest value) in the accent colour —
 *   gives the header an anchor point for "where am I now".
 */
@Composable
fun SpendBarChart(
    dailySpendPaise: List<Long>,
    modifier: Modifier = Modifier,
    height: Dp = 76.dp,
    barColor: Color = MaterialTheme.colorScheme.primary,
    todayBarColor: Color = SpendwiseAccent(),
    emptyBarColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    highlightLastBar: Boolean = true,
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(dailySpendPaise.size) { appeared = true }

    val grow by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "SpendBarChartGrow"
    )

    if (dailySpendPaise.isEmpty()) return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val count = dailySpendPaise.size
        val maxValue = (dailySpendPaise.maxOrNull() ?: 0L).coerceAtLeast(1L)
        val slot = size.width / count
        val barWidth = (slot * 0.56f).coerceAtLeast(1f)
        val stubHeight = 3.dp.toPx()
        val corner = CornerRadius(barWidth / 2f, barWidth / 2f)

        dailySpendPaise.forEachIndexed { index, value ->
            val isLast = index == count - 1
            val ratio = (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f) * grow
            val barHeight = if (value <= 0L) stubHeight else {
                (size.height * ratio).coerceAtLeast(stubHeight)
            }
            val left = index * slot + (slot - barWidth) / 2f

            val color = when {
                value <= 0L -> emptyBarColor
                isLast && highlightLastBar -> todayBarColor
                else -> barColor
            }

            drawRoundRect(
                color = color,
                topLeft = Offset(left, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = corner
            )
        }
    }
}

/**
 * Keeps the chart's "today" accent in one place. Uses the theme's tertiary
 * container-tone accent so it reads as emphasis without competing with the
 * semantic income/expense colours.
 */
@Composable
private fun SpendwiseAccent(): Color = MaterialTheme.colorScheme.tertiary
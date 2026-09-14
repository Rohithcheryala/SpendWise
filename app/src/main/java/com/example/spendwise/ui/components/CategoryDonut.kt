package com.example.spendwise.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.spendwise.ui.theme.SpendwiseTheme

/** One arc of a [CategoryDonut]: what it is, how big it is, and its colour. */
data class DonutSlice(
    val label: String,
    val valuePaise: Long,
    val color: Color,
)

/**
 * Hand-rolled category donut (no charting dependency).
 *
 * Draws one arc per slice with a small inter-slice gap, animates the whole ring
 * sweeping in on first composition, and lets the caller overlay a centre label
 * (usually the month total) via [centerLabel]/[centerValue].
 *
 * The donut is purely presentational: callers pass paise values and colours, so
 * the same composable serves the budget breakdown today and any future
 * breakdown (income sources, spending by account) without new code.
 */
@Composable
fun CategoryDonut(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    diameter: Dp = 156.dp,
    strokeWidth: Dp = 22.dp,
    centerLabel: String? = null,
    centerValue: String? = null,
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(slices) { appeared = true }

    val sweep by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "CategoryDonutSweep"
    )

    val total = slices.sumOf { it.valuePaise }
    // Hoisted out of the Canvas lambda: DrawScope is not @Composable, so theme
    // reads must happen in composition.
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(
        modifier = modifier.size(diameter),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            // Track: a full faint ring so the donut still reads when data is thin.
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            if (total <= 0L) return@Canvas

            val gap = if (slices.size > 1) 3f else 0f
            var startAngle = -90f

            slices.forEach { slice ->
                val fraction = slice.valuePaise.toFloat() / total.toFloat()
                val sliceSweep = (fraction * 360f - gap).coerceAtLeast(0.5f) * sweep

                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = sliceSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )

                startAngle += fraction * 360f
            }
        }

        if (centerLabel != null || centerValue != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                centerValue?.let {
                    MoneyText(
                        text = it,
                        semantic = MoneySemantic.NEUTRAL,
                        size = MoneySize.TITLE
                    )
                }
                centerLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Category colours for the donut. Derived from the theme so light/dark both
 * work; cycled for callers with more categories than palette entries.
 */
@Composable
fun donutPalette(): List<Color> {
    val colors = SpendwiseTheme.colors
    return listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        colors.warning,
        colors.transfer,
        colors.income,
        MaterialTheme.colorScheme.secondary,
        colors.expense,
    )
}
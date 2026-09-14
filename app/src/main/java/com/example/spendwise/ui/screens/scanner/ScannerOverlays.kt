package com.example.spendwise.ui.screens.scanner

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spendwise.ui.theme.SpendwiseTheme
import kotlin.math.hypot

/**
 * Presentation-only overlays drawn on top of the camera preview: the QR
 * detection brackets / search reticle, the tap-to-focus pulse, and the status
 * pill. None of these know anything about analysis — they render the state that
 * the scan pipeline in `ScannerScreen.kt` hands them, which is what keeps the
 * decode logic readable.
 */@Composable
internal fun QrScanOverlay(
    corners: List<FloatArray>?,
    decoded: Boolean,
    modifier: Modifier = Modifier,
) {
    val bracketColor by animateColorAsState(
        targetValue = if (decoded) SpendwiseTheme.colors.income else Color.White,
        animationSpec = tween(200),
        label = "qrBracketColor",
    )
    val sweep = rememberInfiniteTransition(label = "qrSweep")
    val sweepY by sweep.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "qrSweepY",
    )

    Canvas(modifier) {
        if (corners != null && corners.size == 4) {
            // Detection: hug the QR's actual corners.
            val stroke = 5.dp.toPx()
            val bracketLen = size.minDimension * 0.16f
            corners.forEachIndexed { i, p ->
                drawQrBracketArm(p, corners[(i + 3) % 4], bracketLen, bracketColor, stroke)
                drawQrBracketArm(p, corners[(i + 1) % 4], bracketLen, bracketColor, stroke)
            }
        } else {
            // Searching: centered reticle with a sweeping line.
            val side = size.minDimension * 0.58f
            val topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.75f),
                topLeft = topLeft,
                size = Size(side, side),
                cornerRadius = CornerRadius(32f, 32f),
                style = Stroke(width = 2.5.dp.toPx()),
            )
            val y = topLeft.y + sweepY * side
            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = Offset(topLeft.x + 20f, y),
                end = Offset(topLeft.x + side - 20f, y),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

/** One bracket arm: from a corner, [length] pixels toward an adjacent corner. */
private fun DrawScope.drawQrBracketArm(
    from: FloatArray,
    toward: FloatArray,
    length: Float,
    color: Color,
    stroke: Float,
) {
    val vx = toward[0] - from[0]
    val vy = toward[1] - from[1]
    val len = hypot(vx, vy)
    if (len < 1f) return
    val t = (length / len).coerceAtMost(1f)
    drawLine(
        color = color,
        start = Offset(from[0], from[1]),
        end = Offset(from[0] + vx * t, from[1] + vy * t),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
}

/** Expanding ring where the user tapped to focus — confirms the AF request. */
@Composable
internal fun FocusPulseRing(
    x: Float,
    y: Float,
    modifier: Modifier = Modifier,
) {
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(x, y) {
        alpha.snapTo(1f)
        alpha.animateTo(0f, animationSpec = tween(800, easing = LinearEasing))
    }
    Canvas(modifier) {
        drawCircle(
            color = Color.White.copy(alpha = alpha.value),
            radius = 48.dp.toPx(),
            center = Offset(x, y),
            style = Stroke(width = 2.dp.toPx()),
        )
        drawCircle(
            color = Color.White.copy(alpha = alpha.value),
            radius = 3.dp.toPx(),
            center = Offset(x, y),
        )
    }
}

/** Status chip over the viewport: what the scanner is doing right now. */
@Composable
internal fun ScannerStatusPill(
    text: String,
    highlight: Boolean,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (highlight) {
            SpendwiseTheme.colors.income.copy(alpha = 0.92f)
        } else {
            Color.Black.copy(alpha = 0.55f)
        },
        animationSpec = tween(200),
        label = "statusPillBg",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (highlight) 1f else 0.6f)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

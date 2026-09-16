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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spendwise.ui.theme.SpendwiseTheme
import kotlin.math.hypot

/**
 * Presentation-only overlays drawn on top of the camera preview: the QR
 * detection brackets / search reticle, the tap-to-focus pulse, the status
 * pill, and frosted camera buttons. None of these know anything about
 * analysis — they render the state that the scan pipeline in
 * `ScannerScreen.kt` hands them, which is what keeps the decode logic
 * readable.
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
    // Searching state: the reticle "breathes" — soft alpha pulse, proof the
    // camera is alive without boxing the preview in a frame.
    val breathe = rememberInfiniteTransition(label = "qrBreathe")
    val breatheAlpha by breathe.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "qrBreatheAlpha",
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
            // Searching: centered corner brackets — framing guidance, not a box.
            val side = size.minDimension * 0.58f
            val topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f)
            val arm = side * 0.22f
            val stroke = 5.dp.toPx()
            val color = Color.White.copy(alpha = breatheAlpha)
            drawReticleCorner(topLeft, 1f, 1f, arm, color, stroke)
            drawReticleCorner(Offset(topLeft.x + side, topLeft.y), -1f, 1f, arm, color, stroke)
            drawReticleCorner(Offset(topLeft.x, topLeft.y + side), 1f, -1f, arm, color, stroke)
            drawReticleCorner(
                Offset(topLeft.x + side, topLeft.y + side), -1f, -1f, arm, color, stroke,
            )
        }
    }
}

/** One rounded-elbow corner of the search reticle: two arms from a corner. */
private fun DrawScope.drawReticleCorner(
    corner: Offset,
    dirX: Float,
    dirY: Float,
    arm: Float,
    color: Color,
    stroke: Float,
) {
    drawLine(color, corner, Offset(corner.x + dirX * arm, corner.y), stroke, StrokeCap.Round)
    drawLine(color, corner, Offset(corner.x, corner.y + dirY * arm), stroke, StrokeCap.Round)
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

/** Frosted circular camera control (torch etc.) floating over the preview. */
@Composable
internal fun ScannerIconButton(
    icon: ImageVector,
    contentDescription: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
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
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
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

package com.example.spendwise.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

@Composable
fun FriendAvatar(
    name: String,
    modifier: Modifier = Modifier
) {

    val initial = name
        .trim()
        .firstOrNull()
        ?.uppercaseChar()
        ?.toString()
        ?: "?"

    val colors = listOf(
        Color(0xFF2563EB),
        Color(0xFF059669),
        Color(0xFF7C3AED),
        Color(0xFFEA580C),
        Color(0xFFDC2626),
        Color(0xFF0891B2),
        Color(0xFF4F46E5),
        Color(0xFF65A30D)
    )

    val background = remember(name) {
        colors[name.hashCode().absoluteValue % colors.size]
    }

    Surface(
        modifier = modifier.size(52.dp),
        shape = CircleShape,
        color = background.copy(alpha = .18f)
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = background
            )
        }
    }
}
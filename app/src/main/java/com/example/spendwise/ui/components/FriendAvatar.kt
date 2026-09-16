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
import com.example.spendwise.ui.theme.SpendwiseTheme
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

    val swatches = SpendwiseTheme.categorical.swatches
    val background = remember(name) {
        swatches[name.hashCode().absoluteValue % swatches.size]
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
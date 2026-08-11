package com.example.spendwise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun TransactionListItem(
    title: String,
    account: String?,
    time: String,
    amount: String,
    direction: TransactionDirection,
    modifier: Modifier = Modifier,
    tags: List<String> = emptyList(),
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(clickable),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransactionDirectionIcon(direction)

            Spacer(Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!account.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    CircleShape
                                )
                        )
                        Spacer(Modifier.width(6.dp))

                        Text(
                            text = account,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tags.forEach { tag ->
                            TagPill(tag)
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            AmountText(
                amount = amount,
                direction = direction
            )
        }
    }
}

@Composable
private fun TagPill(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun AmountText(
    amount: String,
    direction: TransactionDirection,
    modifier: Modifier = Modifier
) {
    val (prefix, color) = when (direction) {
        TransactionDirection.EXPENSE -> "−" to MaterialTheme.colorScheme.error
        TransactionDirection.INCOME -> "+" to Color(0xFF16A34A)
        TransactionDirection.TRANSFER -> "" to MaterialTheme.colorScheme.primary
    }

    Text(
        text = prefix + amount,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = modifier
    )
}

@Composable
fun TransactionDirectionIcon(
    direction: TransactionDirection,
    modifier: Modifier = Modifier
) {
    val (icon, tint, bgColor) = when (direction) {
        TransactionDirection.EXPENSE -> Triple(
            Icons.Rounded.ArrowUpward,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer
        )
        TransactionDirection.INCOME -> Triple(
            Icons.Rounded.ArrowDownward,
            Color(0xFF16A34A),
            Color(0xFFDCFCE7)
        )
        TransactionDirection.TRANSFER -> Triple(
            Icons.Rounded.SwapHoriz,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer
        )
    }

    Surface(
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        color = bgColor
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

enum class TransactionDirection {
    EXPENSE,
    INCOME,
    TRANSFER
}

@Preview(showBackground = true)
@Composable
fun JustPreview() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TransactionListItem(
            title = "Cotton Dhora",
            account = "HDFC Savings",
            time = "11:24 AM",
            amount = "₹10,000",
            direction = TransactionDirection.INCOME,
            tags = listOf("Repayment")
        )

        TransactionListItem(
            title = "Amazon",
            account = "HDFC Savings",
            time = "Yesterday • 9:43 PM",
            amount = "₹252.90",
            direction = TransactionDirection.EXPENSE,
            tags = listOf("Shopping", "Home")
        )
    }
}
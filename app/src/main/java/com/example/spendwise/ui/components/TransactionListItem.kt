package com.example.spendwise.ui.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.collections.forEach
import kotlin.collections.isNotEmpty
import kotlin.to

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
    } else Modifier

    Surface(
        modifier = modifier.then(clickable),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            TransactionDirectionIcon(direction)

            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = time,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (account != null) {
                        Text(
                            text = " • ",
                            color = MaterialTheme.colorScheme.outline
                        )

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
                        tags.forEach {
                            MetadataChip(it)
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
private fun AmountText(
    amount: String,
    direction: TransactionDirection,
    modifier: Modifier = Modifier
) {

    val prefix = when (direction) {
        TransactionDirection.EXPENSE -> "−"
        TransactionDirection.INCOME -> "+"
        TransactionDirection.TRANSFER -> ""
    }

    Text(
        text = prefix + amount,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun TransactionDirectionIcon(
    direction: TransactionDirection,
    modifier: Modifier = Modifier
) {

    val (icon, tint) = when (direction) {

        TransactionDirection.EXPENSE -> {
            Icons.Rounded.ArrowUpward to MaterialTheme.colorScheme.error
        }

        TransactionDirection.INCOME -> {
            Icons.Rounded.ArrowDownward to Color(0xFF2E7D32)
        }

        TransactionDirection.TRANSFER -> {
            Icons.Rounded.SwapHoriz to MaterialTheme.colorScheme.outline
        }
    }

    Surface(
        modifier = modifier.size(32.dp),
        shape = CircleShape,
        color = tint.copy(alpha = .12f)
    ) {

        Box(
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun MetadataChip(
    text: String,
    modifier: Modifier = Modifier
) {
    AssistChip(
        modifier = modifier.height(28.dp),
        onClick = {},
        enabled = false,
        label = {
            Text(text)
        }
    )
}

enum class TransactionDirection {
    EXPENSE,
    INCOME,
    TRANSFER
}

@Preview(showBackground = true)
@Composable
fun JustPreview() {
    Column() {
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
package com.example.spendwise.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spendwise.ui.theme.SpendwiseTheme

/**
 * Compact transaction row for list views.
 *
 * Density decisions:
 * - Flat row on the screen background (no per-row Card): cards doubled row
 *   height and pushed content below the fold.
 * - Line 1 is the receiver/title. Line 2 is [time] • [category] • [tags] —
 *   time first so it can never be ellipsized away; account is NOT shown
 *   (detail-view metadata; day headers already carry the date).
 * - Target height ~56dp vs the previous ~86dp card+gap.
 */
@Composable
fun TransactionListItem(
    title: String,
    category: String?,
    time: String,
    amount: String,
    direction: TransactionDirection,
    modifier: Modifier = Modifier,
    tags: List<String> = emptyList(),
    showDivider: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(clickable)
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransactionDirectionIcon(direction)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val subtitle = listOfNotNull(
                    time.takeIf { it.isNotBlank() },
                    category?.takeIf { it.isNotBlank() },
                    tags.takeIf { it.isNotEmpty() }?.joinToString(", "),
                ).joinToString(" • ")
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            AmountText(amount = amount, direction = direction)
        }

        if (showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
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
    val colors = SpendwiseTheme.colors
    val (prefix, color) = when (direction) {
        TransactionDirection.EXPENSE -> "−" to colors.expense
        TransactionDirection.INCOME -> "+" to colors.income
        TransactionDirection.TRANSFER -> "" to colors.transfer
    }

    Text(
        text = prefix + amount,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier
    )
}

@Composable
fun TransactionDirectionIcon(
    direction: TransactionDirection,
    modifier: Modifier = Modifier
) {
    val colors = SpendwiseTheme.colors
    val (icon, tint, bgColor) = when (direction) {
        TransactionDirection.EXPENSE -> Triple(
            Icons.Rounded.ArrowUpward,
            colors.expense,
            colors.expenseContainer
        )

        TransactionDirection.INCOME -> Triple(
            Icons.Rounded.ArrowDownward,
            colors.income,
            colors.incomeContainer
        )

        TransactionDirection.TRANSFER -> Triple(
            Icons.Rounded.SwapHoriz,
            colors.transfer,
            colors.transferContainer
        )
    }

    Surface(
        modifier = modifier.size(36.dp),
        shape = RoundedCornerShape(10.dp),
        color = bgColor
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

enum class TransactionDirection {
    EXPENSE,
    INCOME,
    TRANSFER
}

@Preview(showBackground = true)
@Composable
fun JustPreview() {
    Column {
        TransactionListItem(
            title = "Cotton Dhora",
            category = "Others",
            time = "11:24 AM",
            amount = "₹10,000",
            direction = TransactionDirection.INCOME,
            showDivider = true
        )

        TransactionListItem(
            title = "Amazon",
            category = "Shopping",
            time = "9:43 PM",
            amount = "₹252.90",
            direction = TransactionDirection.EXPENSE
        )
    }
}
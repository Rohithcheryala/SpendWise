package com.example.spendwise.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TransactionSummaryCard(
    direction: TransactionDirection,
    amount: String,
    date: LocalDate,
    source: String?,
    modifier: Modifier = Modifier,
) {
    val (accent, bgContainer, icon, label) = when (direction) {
        TransactionDirection.EXPENSE -> Quadruple(
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer,
            Icons.Rounded.ArrowUpward,
            "Expense"
        )

        TransactionDirection.INCOME -> Quadruple(
            Color(0xFF16A34A),
            Color(0xFFDCFCE7),
            Icons.Rounded.ArrowDownward,
            "Income"
        )

        TransactionDirection.TRANSFER -> Quadruple(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer,
            Icons.Rounded.SwapHoriz,
            "Transfer"
        )
    }

    val amountPrefix = when (direction) {
        TransactionDirection.EXPENSE -> "−₹"
        TransactionDirection.INCOME -> "+₹"
        TransactionDirection.TRANSFER -> "₹"
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = bgContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        tint = accent,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )

                Text(
                    text = date.format(
                        DateTimeFormatter.ofPattern("dd MMM yyyy")
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                source?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = buildString {
                    append(amountPrefix)
                    append(amount.ifBlank { "0" })
                },
                style = MaterialTheme.typography.headlineMedium,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Preview(showBackground = true)
@Composable
private fun ExpensePreview() {
    TransactionSummaryCard(
        direction = TransactionDirection.EXPENSE,
        amount = "19",
        date = LocalDate.now(),
        source = "Source"
    )
}
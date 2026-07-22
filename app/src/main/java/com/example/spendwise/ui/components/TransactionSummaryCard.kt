package com.example.spendwise.ui.components


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val accent = when (direction) {
        TransactionDirection.EXPENSE -> MaterialTheme.colorScheme.error
        TransactionDirection.INCOME -> MaterialTheme.colorScheme.primary
        else -> TODO()
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = .12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (direction == TransactionDirection.EXPENSE)
                        Icons.Outlined.ArrowDownward
                    else
                        Icons.Outlined.ArrowUpward,
                    tint = accent,
                    contentDescription = null
                )
            }

            Spacer(Modifier.size(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {

                Text(
                    text = if (direction == TransactionDirection.EXPENSE)
                        "EXPENSE"
                    else
                        "INCOME",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = date.format(
                        DateTimeFormatter.ofPattern("dd MMM yyyy")
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    append(
                        if (direction == TransactionDirection.EXPENSE)
                            "- ₹"
                        else
                            "+ ₹"
                    )
                    append(amount.ifBlank { "0" })
                },
                style = MaterialTheme.typography.headlineSmall,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

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

@Preview(showBackground = true)
@Composable
private fun IncomePreview() {

    TransactionSummaryCard(
        direction = TransactionDirection.INCOME,
        amount = "1250",
        date = LocalDate.now(),
        source = "Salary"
    )
}
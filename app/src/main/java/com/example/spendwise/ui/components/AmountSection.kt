package com.example.spendwise.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun AmountSection(
    direction: TransactionDirection,
    amount: String,
    onDirectionChange: (TransactionDirection) -> Unit,
    onAmountChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor by animateColorAsState(
        targetValue = when (direction) {
            TransactionDirection.EXPENSE -> MaterialTheme.colorScheme.error
            TransactionDirection.INCOME -> Color(0xFF16A34A)
            TransactionDirection.TRANSFER -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(300),
        label = "accent_color"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Transaction Type",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            // Custom Segmented Pill Tab Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TransactionDirection.entries.forEach { dir ->
                        val isSelected = direction == dir
                        val tabBg by animateColorAsState(
                            targetValue = if (isSelected) {
                                when (dir) {
                                    TransactionDirection.EXPENSE -> MaterialTheme.colorScheme.errorContainer
                                    TransactionDirection.INCOME -> Color(0xFFDCFCE7)
                                    TransactionDirection.TRANSFER -> MaterialTheme.colorScheme.primaryContainer
                                }
                            } else MaterialTheme.colorScheme.surfaceContainer,
                            label = "tab_bg"
                        )
                        val tabTextColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                when (dir) {
                                    TransactionDirection.EXPENSE -> MaterialTheme.colorScheme.onErrorContainer
                                    TransactionDirection.INCOME -> Color(0xFF166534)
                                    TransactionDirection.TRANSFER -> MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            } else MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "tab_text"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(tabBg)
                                .clickable { onDirectionChange(dir) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dir.name.take(1) + dir.name.drop(1).lowercase(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = tabTextColor
                            )
                        }
                    }
                }
            }

            // Amount Input Card
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = amount,
                onValueChange = { value ->
                    onAmountChange(
                        value.filter {
                            it.isDigit() || it == '.'
                        }
                    )
                },
                singleLine = true,
                prefix = {
                    Text(
                        "₹ ",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                },
                placeholder = {
                    Text(
                        "0",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                ),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExpensePreview() {
    AmountSection(
        direction = TransactionDirection.EXPENSE,
        amount = "19",
        onDirectionChange = {},
        onAmountChange = {}
    )
}
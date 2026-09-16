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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import com.example.spendwise.ui.theme.Dimens
import com.example.spendwise.ui.theme.SpendwiseTheme

@Composable
fun AmountSection(
    direction: TransactionDirection,
    amount: String,
    onDirectionChange: (TransactionDirection) -> Unit,
    onAmountChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    showDirectionToggle: Boolean = true,
    directionLabels: Pair<String, String>? = null,
) {
    val accentColor by animateColorAsState(
        targetValue = when (direction) {
            TransactionDirection.EXPENSE -> SpendwiseTheme.colors.expense
            TransactionDirection.INCOME -> SpendwiseTheme.colors.income
            TransactionDirection.TRANSFER -> SpendwiseTheme.colors.transfer
        },
        animationSpec = tween(300),
        label = "accent_color"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Direction is binary (money out / money in) — exactly naa's
            // out|in toggle beside the amount. Transfer is NOT a direction:
            // it's chosen once, in the Type selector below, and when it is,
            // direction is meaningless (money leaves one account and lands in
            // another), so the toggle is hidden entirely.
            if (showDirectionToggle) {
                Text(
                    // Loan mode overloads direction with meaning ("which side
                    // of the loan am I on?") — say so instead of "Direction".
                    text = if (directionLabels != null) "Loan" else "Direction",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                // Custom Segmented Pill Tab Bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            TransactionDirection.EXPENSE,
                            TransactionDirection.INCOME,
                        ).forEach { dir ->
                        val isSelected = direction == dir
                        val tabBg by animateColorAsState(
                            targetValue = if (isSelected) {
                                when (dir) {
                                    TransactionDirection.EXPENSE -> SpendwiseTheme.colors.expenseContainer
                                    TransactionDirection.INCOME -> SpendwiseTheme.colors.incomeContainer
                                    TransactionDirection.TRANSFER -> MaterialTheme.colorScheme.primaryContainer
                                }
                            } else MaterialTheme.colorScheme.surfaceContainer,
                            label = "tab_bg"
                        )
                        val tabTextColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                when (dir) {
                                    TransactionDirection.EXPENSE -> MaterialTheme.colorScheme.onErrorContainer
                                    TransactionDirection.INCOME -> SpendwiseTheme.colors.income
                                    TransactionDirection.TRANSFER -> MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            } else MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "tab_text"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(tabBg)
                                .clickable { onDirectionChange(dir) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = directionLabels?.let { (out, _) ->
                                    if (dir == TransactionDirection.EXPENSE) out
                                    else directionLabels.second
                                } ?: dir.name.take(1) + dir.name.drop(1).lowercase(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = tabTextColor
                            )
                        }
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
                        color = SpendwiseTheme.text.tertiary
                    )
                },
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                ),
                shape = MaterialTheme.shapes.small,
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
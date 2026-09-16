package com.example.spendwise.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spendwise.ui.theme.Dimens
import com.example.spendwise.ui.theme.SpendwiseTheme

/**
 * The amount entry group: a slim direction toggle + the amount field, with no
 * card or "Direction" header around them — the pills and the ₹ prefix already
 * say everything a label would, and the detail screen needs every essential
 * field above the fold.
 *
 * Direction is binary (money out / money in). Transfer is NOT a direction:
 * it's chosen once, in the Type selector below, and when it is, the toggle is
 * hidden entirely (money leaves one account and lands in another). Loan mode
 * overloads direction with meaning ("which side of the loan am I on?") —
 * [directionLabels] says so instead of a generic header.
 */
@Composable
fun AmountSection(
    direction: TransactionDirection,
    amount: String,
    onDirectionChange: (TransactionDirection) -> Unit,
    onAmountChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    showDirectionToggle: Boolean = true,
    directionLabels: Pair<String, String>? = null,
    /** Optional composable placed beside the amount field (e.g. the date
     *  picker) — short values pair up so long-named fields keep full rows. */
    trailingField: (@Composable RowScope.() -> Unit)? = null,
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm)
    ) {
        if (showDirectionToggle) {
            // Custom segmented pill tab bar.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(Dimens.xs),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
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
                            } else MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                .heightIn(min = 36.dp)
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

        // With a [trailingField], amount + companion share one row; without,
        // the field is full width.
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
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
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                },
                placeholder = {
                    Text(
                        "0",
                        style = MaterialTheme.typography.titleLarge,
                        color = SpendwiseTheme.text.tertiary
                    )
                },
                textStyle = MaterialTheme.typography.titleLarge.copy(
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
            trailingField?.invoke(this)
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

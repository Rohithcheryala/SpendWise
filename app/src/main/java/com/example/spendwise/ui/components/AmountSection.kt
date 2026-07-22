package com.example.spendwise.ui.components


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmountSection(
    direction: TransactionDirection,
    amount: String,
    onDirectionChange: (TransactionDirection) -> Unit,
    onAmountChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = "Amount",
            style = MaterialTheme.typography.titleMedium
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {

            SegmentedButton(
                selected = direction == TransactionDirection.EXPENSE,
                onClick = {
                    onDirectionChange(TransactionDirection.EXPENSE)
                },
                shape = SegmentedButtonDefaults.itemShape(
                    index = 0,
                    count = 2
                )
            ) {
                Text("EXPENSE")
            }

            SegmentedButton(
                selected = direction == TransactionDirection.INCOME,
                onClick = {
                    onDirectionChange(TransactionDirection.INCOME)
                },
                shape = SegmentedButtonDefaults.itemShape(
                    index = 1,
                    count = 2
                )
            ) {
                Text("INCOME")
            }
        }

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
                Text("₹")
            },
            label = {
                Text("Amount")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            )
        )
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

@Preview(showBackground = true)
@Composable
private fun IncomePreview() {

    AmountSection(
        direction = TransactionDirection.INCOME,
        amount = "1250",
        onDirectionChange = {},
        onAmountChange = {}
    )
}
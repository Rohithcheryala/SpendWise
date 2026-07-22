package com.example.spendwise.ui.components


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spendwise.ui.screens.transaction.TransactionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionTypeSelector(
    selected: TransactionType,
    onSelected: (TransactionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = "Type",
            style = MaterialTheme.typography.titleMedium
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {

            TransactionType.entries.forEachIndexed { index, type ->

                SegmentedButton(
                    selected = selected == type,
                    onClick = {
                        onSelected(type)
                    },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = TransactionType.entries.size
                    )
                ) {
                    Text(
                        text = type.label
                    )
                }
            }
        }
    }
}

private val TransactionType.label: String
    get() = when (this) {
        TransactionType.CATEGORY -> "Category"
        TransactionType.TRANSFER -> "TRANSFER"
        TransactionType.LOAN -> "Loan"
    }

@Preview(showBackground = true)
@Composable
private fun CategoryPreview() {
    TransactionTypeSelector(
        selected = TransactionType.CATEGORY,
        onSelected = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun TransferPreview() {
    TransactionTypeSelector(
        selected = TransactionType.TRANSFER,
        onSelected = {}
    )
}
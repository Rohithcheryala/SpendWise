package com.example.spendwise.ui.components


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun TransactionBottomBar(
    onSave: () -> Unit,
    onVoid: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    canDelete: Boolean = true,
    canVoid: Boolean = true,
    isSaving: Boolean = false,
    saveEnabled: Boolean = true,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {

            HorizontalDivider()

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    if (canVoid) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = onVoid,
                        ) {
                            Text("Void")
                        }
                    }

                    if (canDelete) {
                        TextButton(
                            modifier = Modifier.weight(1f),
                            onClick = onDelete,
                        ) {
                            Text(
                                text = "Delete",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = saveEnabled && !isSaving,
                    onClick = onSave,
                ) {

                    if (isSaving) {

                        CircularProgressIndicator()

                    } else {

                        Text("Save")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TransactionBottomBarPreview() {
    TransactionBottomBar(
        onSave = {},
        onVoid = {},
        onDelete = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun TransactionBottomBarSavingPreview() {
    TransactionBottomBar(
        onSave = {},
        onVoid = {},
        onDelete = {},
        isSaving = true,
    )
}
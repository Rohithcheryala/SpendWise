package com.example.spendwise.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spendwise.ui.theme.Dimens

/**
 * The detail screen's action row: destructive actions (Void / Delete) as
 * low-emphasis text buttons on the left, Save dominant on the right — one
 * 48dp row, not a stack of full-width buttons eating a fifth of the screen.
 * The visibility flags come from the ledger: buffer entries can be voided,
 * manual entries can be deleted, never both.
 */
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
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Dimens.lg, vertical = Dimens.sm),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canVoid) {
                TextButton(
                    onClick = onVoid,
                    contentPadding = PaddingValues(horizontal = Dimens.sm)
                ) {
                    Text(
                        "Void",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (canDelete) {
                TextButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(horizontal = Dimens.sm),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Save wraps its content and sits right-aligned — a full-weight
            // button read as a banner, not an action.
            Spacer(modifier = Modifier.weight(1f))

            Button(
                modifier = Modifier.height(44.dp),
                contentPadding = PaddingValues(horizontal = Dimens.lg),
                shape = MaterialTheme.shapes.small,
                enabled = saveEnabled && !isSaving,
                onClick = onSave,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "Save Transaction",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
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

package com.example.spendwise.ui.screens.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.spendwise.ui.components.SpendwiseTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.spendwise.viewmodel.AccountEditViewModel

/**
 * Edit an existing account: rename, update bank / masked number, or close the
 * account (hidden everywhere; ledger history is kept).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditScreen(
    uiState: AccountEditViewModel.UiState,
    onUpdateState: (transform: (AccountEditViewModel.UiState) -> AccountEditViewModel.UiState) -> Unit,
    onSave: () -> Unit,
    onCloseAccount: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showCloseConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
        topBar = {
            SpendwiseTopBar(
                title = "Edit Account",
                onBack = onNavigateBack
            )
        }
    ) { padding ->

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = uiState.typeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = uiState.name,
                onValueChange = { value -> onUpdateState { it.copy(name = value) } },
                label = { Text("Account Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.bankName,
                onValueChange = { value -> onUpdateState { it.copy(bankName = value) } },
                label = { Text("Bank / Institution") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.last4,
                onValueChange = { value ->
                    onUpdateState { it.copy(last4 = value.filter { c -> c.isDigit() }.take(4)) }
                },
                label = { Text("Last 4 Digits") },
                supportingText = { Text("Used to auto-match bank SMS to this account") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving && uiState.name.isNotBlank()
            ) {
                Text(if (uiState.isSaving) "Saving…" else "Save Changes")
            }

            if (uiState.isActive) {
                OutlinedButton(
                    onClick = { showCloseConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close Account", color = MaterialTheme.colorScheme.error)
                }
            } else {
                Text(
                    text = "This account is closed. Its history is preserved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showCloseConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = { Text("Close account?") },
            text = {
                Text(
                    "It will be hidden everywhere but its transaction history " +
                        "is kept in the ledger."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCloseConfirm = false
                    onCloseAccount()
                }) {
                    Text("Close account", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

package com.example.spendwise.ui.screens.scanner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spendwise.ui.components.DropdownField
import com.example.spendwise.ui.components.FriendAvatar
import com.example.spendwise.ui.screens.transactiondetail.DropdownOption
import com.example.spendwise.ui.screens.transactiondetail.TagUiModel
import com.example.spendwise.ui.theme.SpendwiseTheme

/**
 * The "compose a payment" surfaces of Scan & Pay, split out of
 * `ScannerScreen.kt`: the full-screen payment form, the account/category picker
 * sheet, and the post-payment confirmation sheet. All of them are driven purely
 * by hoisted state from the screen, so they carry no scan or camera logic.
 *//**
 * The payment view: full-screen overlay (deliberately not a bottom sheet —
 * see [BackHandler] above), with payee identity, amount, the account the
 * money leaves (optional; inferred from the bank SMS on merge), an optional
 * category, tags, and a note. "Pay" hands off to the user's UPI app; on
 * return the intent is recorded as a buffer entry.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PaymentOverlay(
    target: UpiTarget,
    amount: String,
    onAmountChange: (String) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    tags: List<TagUiModel>,
    onRemoveTag: (String) -> Unit,
    newTag: String,
    onNewTagChange: (String) -> Unit,
    onAddTag: () -> Unit,
    accounts: List<DropdownOption>,
    selectedAccount: DropdownOption?,
    onAccountClick: () -> Unit,
    categories: List<DropdownOption>,
    selectedCategory: DropdownOption?,
    onCategoryClick: () -> Unit,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onPay: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Pay ${target.name.ifBlank { target.vpa }}",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Cancel payment")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Payee identity.
            Row(verticalAlignment = Alignment.CenterVertically) {
                FriendAvatar(name = target.name.ifBlank { target.vpa }, modifier = Modifier.size(48.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = target.name.ifBlank { target.vpa },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = target.vpa,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = amount,
                onValueChange = onAmountChange,
                label = { Text("Amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            DropdownField(
                label = "Pay from account (optional)",
                value = selectedAccount?.label.orEmpty(),
                placeholder = "Inferred from bank SMS",
                onClick = onAccountClick
            )

            DropdownField(
                label = "Category (optional)",
                value = selectedCategory?.label.orEmpty(),
                placeholder = "Uncategorised",
                onClick = onCategoryClick
            )
            // Tags: chips + inline add field.
            if (tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tags.forEach { tag ->
                        InputChip(
                            selected = true,
                            onClick = { onRemoveTag(tag.id) },
                            label = { Text(tag.label) },
                            trailingIcon = {
                                Icon(Icons.Outlined.Close, contentDescription = "Remove ${tag.label}")
                            }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = newTag,
                onValueChange = onNewTagChange,
                label = { Text("Add tag (e.g. trip, work)") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = onAddTag, enabled = newTag.isNotBlank()) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add tag")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                label = { Text("Note (optional)") },
                placeholder = { Text("Dinner split, groceries, rent…") },
                modifier = Modifier.fillMaxWidth()
            )

            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = onPay,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text("Pay ₹${amount.ifBlank { "0" }} via UPI app")
            }
        }
    }
}

/** Simple bottom-sheet radio list for account/category selection. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OptionPickerSheet(
    title: String,
    options: List<DropdownOption>,
    selected: DropdownOption?,
    onSelect: (DropdownOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            if (options.isEmpty()) {
                Text(
                    text = "Nothing available yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
            options.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(option) }
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (option == selected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
/** Post-payment confirmation: the intent was recorded, the bank SMS will finish it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedConfirmationSheet(
    amount: String,
    payee: String,
    onDone: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDone, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = SpendwiseTheme.colors.income,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "Recorded ₹${amount.ifBlank { "0" }} to $payee",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Saved to your Buffer Inbox. When the bank SMS arrives it merges " +
                    "with this entry and confirms automatically — or review it manually.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
    }
}

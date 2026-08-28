package com.example.spendwise.ui.screens.transaction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spendwise.ui.components.AmountSection
import com.example.spendwise.ui.components.DropdownField
import com.example.spendwise.ui.components.NotesSection
import com.example.spendwise.ui.components.TagSection
import com.example.spendwise.ui.components.TransactionBottomBar
import com.example.spendwise.ui.components.TransactionDirection
import com.example.spendwise.ui.components.TransactionSummaryCard
import com.example.spendwise.ui.components.TransactionTypeSelector
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    modifier: Modifier = Modifier,
    uiState: TransactionUiState,
    onUpdateState: ((TransactionUiState) -> TransactionUiState) -> Unit,
    onEvent: (TransactionUiEvent) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var activePickerType by remember { mutableStateOf<PickerType?>(null) }
    var showAddTagDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (uiState.mode == TransactionMode.CREATE) {
                            "New Transaction"
                        } else {
                            "Transaction Details"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            onEvent(TransactionUiEvent.NavigateBack)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        bottomBar = {
            TransactionBottomBar(
                canDelete = uiState.canDelete,
                isSaving = uiState.isSaving,
                onSave = {
                    onEvent(TransactionUiEvent.SaveClicked)
                },
                onDelete = {
                    onEvent(TransactionUiEvent.DeleteClicked)
                },
                onVoid = {
                    onEvent(TransactionUiEvent.VoidClicked)
                }
            )
        }
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 16.dp
            )
        ) {

            item {
                TransactionSummaryCard(
                    direction = uiState.direction,
                    date = uiState.date,
                    source = uiState.source,
                    amount = uiState.amount
                )
            }

            if (uiState.error != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = uiState.error.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            item {
                AmountSection(
                    direction = uiState.direction,
                    amount = uiState.amount,
                    onDirectionChange = { newDirection ->
                        onUpdateState { current -> current.copy(direction = newDirection) }
                    },
                    onAmountChange = { newAmount ->
                        onUpdateState { current -> current.copy(amount = newAmount) }
                    }
                )
            }

            item {
                DropdownField(
                    label = "Date",
                    value = uiState.date.toString(),
                    onClick = {
                        showDatePicker = true
                    }
                )
            }

            item {
                TransactionTypeSelector(
                    selected = uiState.type,
                    onSelected = { newType: TransactionType ->
                        onUpdateState { current -> current.copy(type = newType) }
                    }
                )
            }

            item {
                DropdownField(
                    label = "Account",
                    value = uiState.account?.label.orEmpty().ifBlank { "Select Account" },
                    onClick = {
                        activePickerType = PickerType.ACCOUNT
                    }
                )
            }

            if (uiState.type != TransactionType.CATEGORY) {

                item {
                    DropdownField(
                        label = if (uiState.type == TransactionType.TRANSFER) {
                            "To Account"
                        } else {
                            "Counterparty"
                        },
                        value = uiState.counterparty?.label.orEmpty(),
                        placeholder = if (uiState.type == TransactionType.TRANSFER) {
                            "Select Account"
                        } else {
                            "Optional"
                        },
                        onClick = {
                            activePickerType = PickerType.COUNTERPARTY
                        }
                    )
                }
            }

            if (uiState.type == TransactionType.CATEGORY) {

                item {
                    DropdownField(
                        label = "Category",
                        value = uiState.category?.label.orEmpty().ifBlank { "Select Category" },
                        onClick = {
                            activePickerType = PickerType.CATEGORY
                        }
                    )
                }

                item {
                    TagSection(
                        tags = uiState.tags,
                        onRemove = { tagId ->
                            onUpdateState { current ->
                                current.copy(tags = current.tags.filter { it.id != tagId })
                            }
                        },
                        onAddClick = {
                            showAddTagDialog = true
                        }
                    )
                }
            }

            item {
                NotesSection(
                    note = uiState.note,
                    onValueChange = { newNote ->
                        onUpdateState { current -> current.copy(note = newNote) }
                    }
                )
            }

            if (uiState.mode == TransactionMode.EDIT && !uiState.rawSms.isNullOrBlank()) {
                item {
                    BackingSmsSection(sms = uiState.rawSms)
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                            onUpdateState { it.copy(date = selectedDate) }
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            androidx.compose.material3.DatePicker(state = datePickerState)
        }
    }

    val currentPicker = activePickerType
    if (currentPicker != null) {
        val (title, options) = when (currentPicker) {
            PickerType.ACCOUNT -> "Select Account" to uiState.accounts

            PickerType.CATEGORY -> "Select Category" to uiState.categories

            PickerType.COUNTERPARTY -> (
                if (uiState.type == TransactionType.TRANSFER) "Select Destination Account" else "Select Counterparty"
                ) to uiState.counterparties
        }

        OptionPickerBottomSheet(
            title = title,
            options = options,
            onDismiss = { activePickerType = null },
            onSelect = { option ->
                onUpdateState { current ->
                    when (currentPicker) {
                        PickerType.ACCOUNT -> current.copy(account = option)
                        PickerType.CATEGORY -> current.copy(category = option)
                        PickerType.COUNTERPARTY -> current.copy(counterparty = option)
                    }
                }
                activePickerType = null
            }
        )
    }

    if (showAddTagDialog) {
        AddTagDialog(
            onDismiss = { showAddTagDialog = false },
            onAdd = { tagLabel ->
                onUpdateState { current ->
                    current.copy(
                        tags = current.tags + TagUiModel(
                            System.currentTimeMillis().toString(), tagLabel
                        )
                    )
                }
                showAddTagDialog = false
            }
        )
    }
}

private enum class PickerType {
    ACCOUNT, CATEGORY, COUNTERPARTY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionPickerBottomSheet(
    title: String,
    options: List<DropdownOption>,
    onDismiss: () -> Unit,
    onSelect: (DropdownOption) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            options.forEach { option ->
                androidx.compose.material3.OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(option) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = option.label,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun AddTagDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var tagText by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Tag") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = tagText,
                onValueChange = { tagText = it },
                label = { Text("Tag Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = { onAdd(tagText.trim()) },
                enabled = tagText.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@Preview(showBackground = true)
@Composable
private fun TransactionScreenPreview() {
    TransactionScreen(
        uiState = TransactionPreviewData.state,
        onEvent = {},
        modifier = Modifier,
        onUpdateState = {}
    )
}

@Composable
private fun BackingSmsSection(sms: String) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Backing SMS",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (expanded) "Hide" else "Show evidence",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { expanded = !expanded }
                )
            }
            if (expanded) {
                Text(
                    text = sms,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Immutable
data class TransactionUiState(
    val id: Long? = null,
    val mode: TransactionMode = TransactionMode.CREATE,
    val direction: TransactionDirection = TransactionDirection.EXPENSE,
    val amount: String = "",
    val date: LocalDate = LocalDate.now(),
    val type: TransactionType = TransactionType.CATEGORY,
    val account: DropdownOption? = null,
    val counterparty: DropdownOption? = null,
    val category: DropdownOption? = null,
    val tags: List<TagUiModel> = emptyList(),
    val note: String = "",
    val source: String? = null,
    /** Raw bank SMS / note that produced this entry (provenance) — shown as backing evidence. */
    val rawSms: String? = null,
    val isVoided: Boolean = false,
    val isSaving: Boolean = false,
    val canDelete: Boolean = false,
    val error: String? = null,
    val accounts: List<DropdownOption> = emptyList(),
    val counterparties: List<DropdownOption> = emptyList(),
    val categories: List<DropdownOption> = emptyList(),
)

enum class TransactionMode {
    CREATE,
    EDIT
}


enum class TransactionType {
    CATEGORY,
    TRANSFER,
    LOAN
}

@Immutable
data class DropdownOption(
    val id: String,
    val label: String,
)

@Immutable
data class TagUiModel(
    val id: String,
    val label: String,
)

sealed interface TransactionUiEvent {

    data object NavigateBack : TransactionUiEvent

    data class DirectionChanged(
        val value: TransactionDirection,
    ) : TransactionUiEvent

    data class AmountChanged(
        val value: String,
    ) : TransactionUiEvent

    data object DateClicked : TransactionUiEvent

    data class TypeChanged(
        val value: TransactionType,
    ) : TransactionUiEvent

    data object AccountClicked : TransactionUiEvent

    data object CounterpartyClicked : TransactionUiEvent

    data object CategoryClicked : TransactionUiEvent

    data class RemoveTag(
        val id: String,
    ) : TransactionUiEvent

    data object AddTagClicked : TransactionUiEvent

    data class NoteChanged(
        val value: String,
    ) : TransactionUiEvent

    data object SaveClicked : TransactionUiEvent

    data object DeleteClicked : TransactionUiEvent

    data object VoidClicked : TransactionUiEvent
}

object TransactionPreviewData {

    val state = TransactionUiState(
        id = 1,
        mode = TransactionMode.EDIT,
        direction = TransactionDirection.EXPENSE,
        amount = "19",
        date = LocalDate.of(2026, 7, 8),
        type = TransactionType.CATEGORY,
        account = DropdownOption("1", "HDFC Savings"),
        counterparty = DropdownOption("2", "BHIM"),
        category = DropdownOption("3", "Unclassified"),
        tags = listOf(
            TagUiModel("1", "rahul"),
            TagUiModel("2", "upi"),
        ),
        note = "",
        source = "Source",
        canDelete = true,
        accounts = listOf(
            DropdownOption("1", "HDFC Savings"),
            DropdownOption("2", "Cash"),
        ),
        counterparties = listOf(
            DropdownOption("1", "BHIM"),
            DropdownOption("2", "Paytm"),
        ),
        categories = listOf(
            DropdownOption("1", "Unclassified"),
            DropdownOption("2", "Food"),
            DropdownOption("3", "Bills"),
        ),
    )
}
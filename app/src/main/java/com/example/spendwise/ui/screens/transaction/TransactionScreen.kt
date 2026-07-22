package com.example.spendwise.ui.screens.transaction


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
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
                            "Transaction"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            onEvent(TransactionUiEvent.NavigateBack)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
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
                .padding(innerPadding)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 120.dp
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

            item {
                AmountSection(
                    direction = uiState.direction,
                    amount = uiState.amount,
                    onDirectionChange = { newDirection ->
                        onUpdateState { current -> current.copy(direction = newDirection) }
//                        onEvent(
////                            onUpdateState { it.copy(direction = it.direction) }
//                            TransactionUiEvent.DirectionChanged(it)
//                        )
                    },
                    onAmountChange = { newAmount ->
                        onUpdateState { current -> current.copy(amount = newAmount) }
//                        onEvent(
//                            TransactionUiEvent.AmountChanged(it)
//                        )
                    }
                )
            }

            item {
                DropdownField(
                    label = "Date",
                    value = uiState.date.toString(),
                    onClick = {
//                        TODO Date
//                        onEvent(
//                            TransactionUiEvent.DateClicked
//                        )
                    }
                )
            }

            item {
                TransactionTypeSelector(
                    selected = uiState.type,
                    onSelected = { newType: TransactionType ->
                        onUpdateState { current -> current.copy(type = newType) }
//                        onEvent(
//                            TransactionUiEvent.TypeChanged(it)
//                        )
                    }
                )
            }

            item {
                DropdownField(
                    label = "Account",
                    value = uiState.account?.label.orEmpty(),
                    onClick = {
                        onEvent(
                            TransactionUiEvent.AccountClicked
                        )
                    }
                )
            }

            if (uiState.type == TransactionType.CATEGORY) {

                item {
                    DropdownField(
                        label = "Counterparty",
                        value = uiState.counterparty?.label.orEmpty(),
                        placeholder = "Optional",
                        onClick = {
                            onEvent(
                                TransactionUiEvent.CounterpartyClicked
                            )
                        }
                    )
                }

                item {
                    DropdownField(
                        label = "Category",
                        value = uiState.category?.label.orEmpty(),
                        onClick = {
                            onEvent(
                                TransactionUiEvent.CategoryClicked
                            )
                        }
                    )
                }

                item {
                    TagSection(
                        tags = uiState.tags,
                        onRemove = {
                            onEvent(
                                TransactionUiEvent.RemoveTag(it)
                            )
                        },
                        onAddClick = {
                            onEvent(
                                TransactionUiEvent.AddTagClicked
                            )
                        }
                    )
                }
            }

            item {
                NotesSection(
                    note = uiState.note,
                    onValueChange = {
                        onEvent(
                            TransactionUiEvent.NoteChanged(it)
                        )
                    }
                )
            }

            item {
                HorizontalDivider()
            }
        }
    }
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
    val isVoided: Boolean = false,
    val isSaving: Boolean = false,
    val canDelete: Boolean = false,
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
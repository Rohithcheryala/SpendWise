package com.example.spendwise.ui.screens.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spendwise.ui.components.TransactionDirection
import com.example.spendwise.ui.components.TransactionListItem

import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    state: TransactionFilterState,
    transactions: List<TransactionUi>,
    onEvent: (TransactionEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEvent(TransactionEvent.OpenFilters) },
                icon = {
                    Icon(Icons.Rounded.FilterList, null)
                },
                text = {
                    if (state.activeFilterCount == 0) {
                        Text("Filters")
                    } else {
                        Text("Filters (${state.activeFilterCount})")
                    }
                }
            )
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier.padding(padding)
        ) {

            item {
                ActiveFilterRow(
                    filters = state.activeFilters,
                    onRemove = {
                        onEvent(TransactionEvent.RemoveFilter(it))
                    }
                )
            }

            items(
                items = transactions,
                key = { transaction -> transaction.id }
            ) {

                TransactionListItem(
                    title = it.title,
                    account = it.account,
                    time = it.time,
                    amount = it.amount,
                    direction = it.direction,
                    tags = it.tags
                )
                HorizontalDivider()
            }
        }
    }

    if (state.showFilters) {

        TransactionFilterSheet(
            state = state,
            sheetState = sheetState,
            onDismiss = {
                onEvent(TransactionEvent.CloseFilters)
            },
            onApply = {
                onEvent(TransactionEvent.ApplyFilters)
            },
            onReset = {
                onEvent(TransactionEvent.ResetFilters)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFilterSheet(
    state: TransactionFilterState,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit
) {

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {

        LazyColumn(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            item {

                Text(
                    "Filters",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            item {

                FilterDropdown(
                    title = "Status",
                    value = state.status.toString(),
                    onClick = {}
                )
            }

            item {

                FilterDropdown(
                    title = "Category",
                    value = state.category ?: "Any",
                    onClick = {}
                )
            }

            item {

                FilterDropdown(
                    title = "From account",
                    value = state.fromAccount ?: "Any",
                    onClick = {}
                )
            }

            item {

                FilterDropdown(
                    title = "To account",
                    value = state.toAccount ?: "Any",
                    onClick = {}
                )
            }

            item {

                DateRangeField(
                    from = state.fromDate.toString(),
                    to = state.toDate.toString(),
                    onClick = {}
                )
            }

            item {

                FilterDropdown(
                    title = "Tags",
                    value = state.tag ?: "Any",
                    onClick = {}
                )
            }

            item {

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onReset
                    ) {
                        Text("Reset")
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onApply
                    ) {
                        Text("Apply")
                    }
                }
            }

            item {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

@Composable
fun FilterDropdown(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier
    ) {

        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium
        )

        Spacer(Modifier.height(6.dp))

        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {

            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    value,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    null
                )
            }
        }
    }
}

@Composable
fun DateRangeField(
    from: String?,
    to: String?,
    onClick: () -> Unit
) {

    Column {

        Text(
            "Date Range",
            style = MaterialTheme.typography.labelMedium
        )

        Spacer(Modifier.height(6.dp))

        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {

            Row(
                modifier = Modifier.padding(16.dp)
            ) {

                Icon(
                    Icons.Rounded.CalendarMonth,
                    null
                )

                Spacer(Modifier.width(12.dp))

                Text(
                    "${from ?: "Any"} - ${to ?: "Any"}"
                )
            }
        }
    }
}

@Composable
fun ActiveFilterRow(
    filters: List<String>,
    onRemove: (String) -> Unit
) {

    if (filters.isEmpty()) return

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        items(filters.size) {

            InputChip(
                selected = true,
                onClick = { },
                label = {
//                    Text(it)
                },
                trailingIcon = {
                    Icon(
                        Icons.Rounded.Close,
                        null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}

data class TransactionUi(
    val id: Long,
    val title: String,
    val account: String?,
    val amount: String,
    val time: String,
    val direction: TransactionDirection,
    val tags: List<String> = emptyList()
)

data class TransactionFilterState(
    val showFilters: Boolean = false,

    val status: TransactionStatus? = null,
    val category: String? = null,

    val fromAccount: String? = null,
    val toAccount: String? = null,

    val tag: String? = null,

    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null
) {

    val activeFilterCount: Int
        get() = listOf(
            status,
            category,
            fromAccount,
            toAccount,
            tag,
            fromDate,
            toDate
        ).count { it != null }

    val activeFilters: List<String>
        get() = buildList {

            status?.let {
                add(it.label)
            }

            category?.let {
                add(it)
            }

            fromAccount?.let {
                add(it)
            }

            toAccount?.let {
                add(it)
            }

            tag?.let {
                add(it)
            }

            if (fromDate != null || toDate != null) {
                add("Date")
            }
        }
}

enum class TransactionStatus(
    val label: String
) {
    Confirmed("Confirmed"),
    Pending("Pending"),
    Voided("Voided")
}

sealed interface TransactionEvent {

    data object OpenFilters : TransactionEvent

    data object CloseFilters : TransactionEvent

    data object ApplyFilters : TransactionEvent

    data object ResetFilters : TransactionEvent

    data class RemoveFilter(
        val filter: String
    ) : TransactionEvent
}

// ----future
//Looking at your previous messages, your app is Compose-first, not React. I wouldn't pass a generic TransactionFilterState around.
//
//Instead I'd split it into two reusable models:
//data class TransactionFilters(
//    val status: TransactionStatus? = null,
//    val category: CategoryUi? = null,
//    val fromAccount: AccountUi? = null,
//    val toAccount: AccountUi? = null,
//    val tags: List<TagUi> = emptyList(),
//    val dateRange: ClosedRange<LocalDate>? = null
//)
//
//data class TransactionsUiState(
//    val transactions: List<TransactionUi> = emptyList(),
//    val filters: TransactionFilters = TransactionFilters(),
//    val isFilterSheetVisible: Boolean = false
//)

@Preview(
    name = "Transactions",
    showBackground = true,
    showSystemUi = true
)
@Composable
private fun TransactionsScreenPreview() {
    MaterialTheme {

        val transactions = listOf(
            TransactionUi(
                id = 1,
                title = "Cotton Dhora",
                account = "HDFC Savings",
                amount = "₹10,000",
                time = "11:24 AM",
                direction = TransactionDirection.INCOME,
                tags = listOf("Repayment")
            ),
            TransactionUi(
                id = 2,
                title = "BHIM",
                account = "HDFC Savings",
                amount = "₹19",
                time = "10:43 AM",
                direction = TransactionDirection.EXPENSE,
                tags = listOf("Rahul")
            ),
            TransactionUi(
                id = 3,
                title = "Amazon",
                account = "Credit Card",
                amount = "₹252.90",
                time = "Yesterday • 9:41 PM",
                direction = TransactionDirection.EXPENSE,
                tags = listOf("Shopping", "Home")
            ),
            TransactionUi(
                id = 4,
                title = "Salary",
                account = "HDFC Savings",
                amount = "₹58,000",
                time = "8:30 AM",
                direction = TransactionDirection.INCOME
            )
        )

        TransactionsScreen(
            state = TransactionFilterState(
                showFilters = false,
                status = TransactionStatus.Confirmed,
                category = "Food",
                tag = "Rahul"
            ),
            transactions = transactions,
            onEvent = {}
        )
    }
}
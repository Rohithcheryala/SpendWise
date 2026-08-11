package com.example.spendwise.ui.screens.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spendwise.ui.components.TransactionDirection
import com.example.spendwise.ui.components.TransactionListItem

import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    state: TransactionFilterState = TransactionFilterState(),
    transactions: List<TransactionUi> = defaultTransactions,
    onNavigateBack: (() -> Unit)? = null,
    onTransactionClick: ((Long) -> Unit)? = null,
    onAddTransactionClick: (() -> Unit)? = null,
    onEvent: (TransactionEvent) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }

    val filteredTransactions = transactions.filter {
        searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) || (it.account?.contains(searchQuery, ignoreCase = true) == true)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Transactions", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(TransactionEvent.OpenFilters) }) {
                        Icon(Icons.Rounded.FilterList, contentDescription = "Filters")
                    }
                }
            )
        },
        floatingActionButton = {
            if (onAddTransactionClick != null) {
                FloatingActionButton(
                    onClick = onAddTransactionClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Transaction")
                }
            }
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search transactions...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    singleLine = true
                )
            }

            item {
                ActiveFilterRow(
                    filters = state.activeFilters,
                    onRemove = {
                        onEvent(TransactionEvent.RemoveFilter(it))
                    }
                )
            }

            if (filteredTransactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No transactions found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Try clearing your search or filters",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredTransactions,
                    key = { transaction -> transaction.id }
                ) { item ->
                    TransactionListItem(
                        title = item.title,
                        account = item.account,
                        time = item.time,
                        amount = item.amount,
                        direction = item.direction,
                        tags = item.tags,
                        onClick = { onTransactionClick?.invoke(item.id) }
                    )
                }
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filters.size) { index ->
            val filterText = filters[index]
            InputChip(
                selected = true,
                onClick = { onRemove(filterText) },
                label = {
                    Text(filterText)
                },
                trailingIcon = {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Remove",
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}

val defaultTransactions = listOf(
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
        title = "BHIM / Swiggy",
        account = "HDFC Savings",
        amount = "₹450",
        time = "10:43 AM",
        direction = TransactionDirection.EXPENSE,
        tags = listOf("Food")
    ),
    TransactionUi(
        id = 3,
        title = "Amazon Shopping",
        account = "ICICI Credit Card",
        amount = "₹2,529.90",
        time = "Yesterday • 9:41 PM",
        direction = TransactionDirection.EXPENSE,
        tags = listOf("Shopping", "Electronics")
    ),
    TransactionUi(
        id = 4,
        title = "Monthly Salary",
        account = "HDFC Savings",
        amount = "₹85,000",
        time = "01 Aug • 8:30 AM",
        direction = TransactionDirection.INCOME
    )
)

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
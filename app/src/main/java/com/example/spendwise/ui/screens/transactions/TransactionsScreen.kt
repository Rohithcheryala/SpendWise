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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spendwise.ui.components.EmptyState
import com.example.spendwise.ui.components.MoneySemantic
import com.example.spendwise.ui.components.MoneySize
import com.example.spendwise.ui.components.MoneyText
import com.example.spendwise.ui.components.SpendBarChart
import com.example.spendwise.ui.components.TransactionDirection
import com.example.spendwise.ui.components.TransactionListItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
        state: TransactionFilterState = TransactionFilterState(),
    transactions: List<TransactionUi> = emptyList(),
    onNavigateBack: (() -> Unit)? = null,
    onTransactionClick: ((Long) -> Unit)? = null,
    onAddTransactionClick: (() -> Unit)? = null,
    onEvent: (TransactionEvent) -> Unit = {},
    onStatusChange: (TransactionFilterStatus?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }

    val filteredTransactions = transactions.filter { tx ->
        // Search: title or account
        val matchesSearch = searchQuery.isBlank() ||
            tx.title.contains(searchQuery, ignoreCase = true) ||
            (tx.account?.contains(searchQuery, ignoreCase = true) == true)

        // Status filter (null = keep whatever the ViewModel loaded)
        val matchesStatus = state.status == null ||
            tx.status == state.status

        // Date-range filter (both bounds inclusive on the local date)
        val txDate = Instant.ofEpochMilli(tx.occurredOn)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val matchesDate = (state.fromDate == null || !txDate.isBefore(state.fromDate)) &&
            (state.toDate == null || !txDate.isAfter(state.toDate))

        matchesSearch && matchesStatus && matchesDate
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (transactions.isNotEmpty()) {
                item(key = "spend-summary") {
                    SpendSummaryHeader(transactions = transactions)
                }
            }

            item {
                CompactSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier.padding(bottom = 4.dp)
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
                    EmptyState(
                        icon = Icons.Rounded.ReceiptLong,
                        title = if (transactions.isEmpty()) {
                            "No transactions yet"
                        } else {
                            "No matches"
                        },
                        message = if (transactions.isEmpty()) {
                            "Transactions detected from bank SMS and added by hand " +
                                "will show up here."
                        } else {
                            "Nothing matches this search or these filters. " +
                                "Try clearing them."
                        },
                        actionLabel = if (transactions.isNotEmpty() &&
                            (searchQuery.isNotBlank() || state.activeFilters.isNotEmpty())
                        ) {
                            "Clear filters"
                        } else {
                            null
                        },
                        onAction = {
                            searchQuery = ""
                            onEvent(TransactionEvent.ResetFilters)
                            onStatusChange(null)
                        }
                    )
                }
            } else {
                itemsIndexed(
                    items = filteredTransactions,
                    key = { _, transaction -> transaction.id }
                ) { index, item ->
                    val showHeader = index == 0 ||
                        filteredTransactions[index - 1].dayLabel != item.dayLabel
                    val showDivider = index < filteredTransactions.lastIndex &&
                        filteredTransactions[index + 1].dayLabel == item.dayLabel

                    Column(modifier = Modifier.animateItem()) {
                        if (showHeader && item.dayLabel.isNotBlank()) {
                            DayHeader(label = item.dayLabel)
                        }

                        TransactionListItem(
                            title = item.title,
                            category = item.category,
                            time = item.time,
                            amount = item.amount,
                            direction = item.direction,
                            tags = item.tags,
                            showDivider = showDivider,
                            onClick = { onTransactionClick?.invoke(item.id) }
                        )
                    }
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
            },
            onStatusChange = onStatusChange
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
    onReset: () -> Unit,
    onStatusChange: (TransactionFilterStatus?) -> Unit
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

                Text(
                    "Status",
                    style = MaterialTheme.typography.labelMedium
                )

                Spacer(Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.status == null,
                        onClick = { onStatusChange(null) },
                        label = { Text("Any") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = state.status == TransactionFilterStatus.Confirmed,
                        onClick = { onStatusChange(TransactionFilterStatus.Confirmed) },
                        label = { Text("Confirmed") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = state.status == TransactionFilterStatus.Pending,
                        onClick = { onStatusChange(TransactionFilterStatus.Pending) },
                        label = { Text("Pending") },
                        modifier = Modifier.weight(1f)
                    )
                }
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

/**
 * The 30-day spend spark at the top of the transactions list.
 *
 * Two things it has to do at once: give the screen a "hero" element so it
 * doesn't open on a bare list, and answer "am I spending more than usual?"
 * without a tap. Bars are expense-only (transfers and income aren't spend)
 * and the total counts the same window.
 */
@Composable
fun SpendSummaryHeader(
    transactions: List<TransactionUi>,
    modifier: Modifier = Modifier
) {
    val series = remember(transactions) { last30DaySpendSeries(transactions) }
    val total = series.sum()

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Last 30 days",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MoneyText(
                    amountPaise = total,
                    semantic = MoneySemantic.EXPENSE,
                    size = MoneySize.TITLE
                )
            }

            Spacer(Modifier.height(14.dp))

            SpendBarChart(dailySpendPaise = series)

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "30 days ago",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Today",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Buckets expense transactions into the last 30 local days, oldest first, so
 * the chart can render a continuous timeline (days with no spend come back as
 * 0 rather than being skipped).
 */
fun last30DaySpendSeries(
    transactions: List<TransactionUi>,
    today: LocalDate = LocalDate.now()
): List<Long> {
    val zone = ZoneId.systemDefault()
    val spendByDay = transactions
        .filter { it.direction == TransactionDirection.EXPENSE && it.amountPaise > 0L }
        .groupBy { Instant.ofEpochMilli(it.occurredOn).atZone(zone).toLocalDate() }
        .mapValues { (_, dayTransactions) -> dayTransactions.sumOf { it.amountPaise } }

    return (29 downTo 0).map { daysAgo ->
        spendByDay[today.minusDays(daysAgo.toLong())] ?: 0L
    }
}
@Composable
fun DayHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 2.dp, start = 4.dp)
    )
}

/**
 * Compact search field (~46dp) built from a Surface + BasicTextField.
 * Replaces OutlinedTextField which has a fixed 56dp minimum height.
 */
@Composable
fun CompactSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )

            Spacer(Modifier.width(10.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search transactions...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(20.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
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
        category = "Others",
        tags = listOf("Repayment"),
        dayLabel = "Today"
    ),
    TransactionUi(
        id = 2,
        title = "BHIM / Swiggy",
        account = "HDFC Savings",
        amount = "₹450",
        time = "10:43 AM",
        direction = TransactionDirection.EXPENSE,
        category = "Food",
        tags = listOf("Food"),
        dayLabel = "Today"
    ),
    TransactionUi(
        id = 3,
        title = "Amazon Shopping",
        account = "ICICI Credit Card",
        amount = "₹2,529.90",
        time = "9:41 PM",
        direction = TransactionDirection.EXPENSE,
        category = "Shopping",
        tags = listOf("Shopping", "Electronics"),
        dayLabel = "Yesterday"
    ),
    TransactionUi(
        id = 4,
        title = "Monthly Salary",
        account = "HDFC Savings",
        amount = "₹85,000",
        time = "8:30 AM",
        direction = TransactionDirection.INCOME,
        category = "Income",
        dayLabel = "01 Aug"
    )
)

data class TransactionUi(
    val id: Long,
    val title: String,
    val account: String?,
    val amount: String,
    val time: String,
    val direction: TransactionDirection,
    /** Category name shown in the row subtitle (null when unclassified). */
    val category: String? = null,
    val tags: List<String> = emptyList(),
    /** Day bucket label used for list section headers, e.g. "Today", "12 Aug" */
    val dayLabel: String = "",
    /** Ledger status surfaced so the list can be filtered (confirmed vs pended). */
    val status: TransactionFilterStatus = TransactionFilterStatus.Confirmed,
    /** Epoch millis the entry occurred; enables date-range filtering. */
    val occurredOn: Long = 0L,
    /** Same value as [amount] but numeric, so charts never parse the string. */
    val amountPaise: Long = 0L
)

data class TransactionFilterState(
    val showFilters: Boolean = false,

    val status: TransactionFilterStatus? = null,
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

    /** Drop an active-filter chip by its label (used by RemoveFilter events). */
    fun removeChip(label: String): TransactionFilterState = when {
        status?.label == label -> copy(status = null)
        category == label -> copy(category = null)
        fromAccount == label -> copy(fromAccount = null)
        toAccount == label -> copy(toAccount = null)
        tag == label -> copy(tag = null)
        label == "Date" -> copy(fromDate = null, toDate = null)
        else -> this
    }
}

enum class TransactionFilterStatus(
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
                category = "Others",
                tags = listOf("Repayment")
            ),
            TransactionUi(
                id = 2,
                title = "BHIM",
                account = "HDFC Savings",
                amount = "₹19",
                time = "10:43 AM",
                direction = TransactionDirection.EXPENSE,
                category = "Food",
                tags = listOf("Rahul")
            ),
            TransactionUi(
                id = 3,
                title = "Amazon",
                account = "Credit Card",
                amount = "₹252.90",
                time = "9:41 PM",
                direction = TransactionDirection.EXPENSE,
                category = "Shopping",
                tags = listOf("Shopping", "Home")
            ),
            TransactionUi(
                id = 4,
                title = "Salary",
                account = "HDFC Savings",
                amount = "₹58,000",
                time = "8:30 AM",
                direction = TransactionDirection.INCOME,
                category = "Income"
            )
        )

        TransactionsScreen(
            state = TransactionFilterState(
                showFilters = false,
                status = TransactionFilterStatus.Confirmed,
                category = "Food",
                tag = "Rahul"
            ),
            transactions = transactions,
            onEvent = {}
        )
    }
}
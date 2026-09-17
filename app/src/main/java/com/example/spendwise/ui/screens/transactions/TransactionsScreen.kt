package com.example.spendwise.ui.screens.transactions

import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.spendwise.ui.components.SpendwiseTopBar
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
import com.example.spendwise.ui.theme.Dimens
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
import com.example.spendwise.ui.components.SpendwiseCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    state: TransactionFilterState = TransactionFilterState(),
    transactions: List<TransactionUi> = emptyList(),
    onNavigateBack: (() -> Unit)? = null,
    onTransactionClick: ((Long) -> Unit)? = null,
    onAddTransactionClick: (() -> Unit)? = null,
    onOpenFilters: () -> Unit = {},
    onEvent: (TransactionEvent) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    // Rules live in TransactionFilters.kt so they can be unit-tested.
    val filteredTransactions = transactions.filter {
        matchesTransactionFilters(it, state, searchQuery)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
        topBar = {
            SpendwiseTopBar(
                title = "Transactions",
                onBack = onNavigateBack,
                actions = {
                    IconButton(onClick = onOpenFilters) {
                        // A live count, so "did my filters stick?" is answerable
                        // without reopening the filters. `activeFilterCount`
                        // was computed but never shown.
                        if (state.activeFilterCount > 0) {
                            BadgedBox(
                                badge = { Badge { Text(state.activeFilterCount.toString()) } }
                            ) {
                                Icon(Icons.Rounded.FilterList, contentDescription = "Filters")
                            }
                        } else {
                            Icon(
                                Icons.Rounded.FilterList,
                                contentDescription = "Filters"
                            )
                        }
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
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add Transaction")
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
                    onRemove = { chip ->
                        onEvent(TransactionEvent.RemoveFilter(chip.key))
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
                            // ResetFilters clears status too, and it now
                            // re-queries; calling onStatusChange(null) as well
                            // would fire a second identical ledger read.
                            onEvent(TransactionEvent.ResetFilters)
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
}

@Composable
fun ActiveFilterRow(
    filters: List<ActiveFilterChip>,
    onRemove: (ActiveFilterChip) -> Unit
) {
    if (filters.isEmpty()) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
    ) {
        items(filters.size) { index ->
            val filter = filters[index]
            InputChip(
                selected = true,
                onClick = { onRemove(filter) },
                label = {
                    Text(filter.label)
                },
                trailingIcon = {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Remove ${filter.label}",
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

    SpendwiseCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
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

            Spacer(Modifier.height(16.dp))

            SpendBarChart(dailySpendPaise = series)

            Spacer(Modifier.height(4.dp))

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
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp)
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
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Search,
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
    val amountPaise: Long = 0L,
    /*
     * Ids alongside the display strings. The sheet's category/account filters
     * match on these, not on the names — two accounts may share a name
     * (`SCHEMA_SYNC.md` G1) and [account] is a composite "A → B" on transfers,
     * so neither is a safe match key.
     */
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val toAccountId: Long? = null
)

sealed interface TransactionEvent {

    /**
     * Re-query and close. Filters apply live against the loaded list, so this
     * exists so the user's explicit "done" always re-reads the ledger — it is
     * not what applies the selections.
     */
    data object ApplyFilters : TransactionEvent

    data object ResetFilters : TransactionEvent

    /** Remove one active chip by its [FilterKeys] key. */
    data class RemoveFilter(
        val key: String
    ) : TransactionEvent

    /*
     * The selection events the sheet needs to be more than decoration. All of
     * these are applied live against the already-loaded list (the predicate in
     * `TransactionFilters.kt`), so none of them re-queries — status is not here
     * because it goes through `setStatusFilter`, which must re-query: the ledger
     * set itself differs between CONFIRMED and BUFFER.
     */
    data class SetCategory(
        val categoryId: Long?
    ) : TransactionEvent

    data class SetFromAccount(
        val accountId: Long?
    ) : TransactionEvent

    data class SetToAccount(
        val accountId: Long?
    ) : TransactionEvent

    data class SetTag(
        val tag: String?
    ) : TransactionEvent

    data class SetDateRange(
        val from: LocalDate?,
        val to: LocalDate?
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
                status = TransactionFilterStatus.Confirmed,
                categoryId = 2L,
                tag = "Rahul",
                options = TransactionFilterOptions(
                    categories = listOf(
                        FilterOption(2L, "Food"),
                        FilterOption(3L, "Income"),
                    ),
                    accounts = listOf(FilterOption(1L, "HDFC Savings")),
                    tags = listOf("Rahul", "Shopping", "Home"),
                )
            ),
            transactions = transactions,
            onOpenFilters = {},
            onEvent = {}
        )
    }
}

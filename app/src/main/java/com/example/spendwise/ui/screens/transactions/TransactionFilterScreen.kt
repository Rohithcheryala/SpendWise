package com.example.spendwise.ui.screens.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.spendwise.ui.components.DropdownField
import com.example.spendwise.ui.components.FieldLabel
import com.example.spendwise.ui.components.SpendwiseTopBar
import com.example.spendwise.ui.theme.Dimens
import java.time.LocalDate

/**
 * The transactions filters, as a **full screen**.
 *
 * This has been wrong three times, and the history matters because each fix
 * solved the last symptom without fixing the cause:
 *
 *  1. Every control was a placeholder — Category, both account legs, tags and
 *     the date range were `onClick = {}`, and no event could set their state.
 *     The date card printed `LocalDate?.toString()`: the string "null - null".
 *  2. The fix worked but did not match the app: a platform `DropdownMenu` (used
 *     nowhere else in the codebase) floated over the form, and the fields were
 *     a hand-rolled copy of the shared [DropdownField].
 *  3. A picker **page inside the same bottom sheet** — which still meant a
 *     sheet, and a second surface appearing over the first.
 *
 * The cause under 2 and 3 was the container, not the contents: a
 * `ModalBottomSheet` is the wrong home for a six-control form with a two-level
 * picker. Every other form in this app that is more than a confirmation is a
 * full screen with its own top bar — `AccountEditScreen`,
 * `TransactionDetailScreen` — and that is what this is now. There is no sheet
 * here to overlap anything, and no drawer to dismiss the one underneath.
 *
 * Consistent with the rest of the app:
 *  - [SpendwiseTopBar] — the app's one top bar, including the back arrow it
 *    renders itself. On the option page the same bar shows the field's name, so
 *    "back" to the filters is that arrow.
 *  - [DropdownField] for all five fields, so they are the transaction form's
 *    fields rather than a second design language.
 *  - Option rows styled like `OptionPickerSheet`: full-width, 24dp gutters,
 *    16dp vertical padding, `bodyLarge`, SemiBold + a primary `CheckCircle` on
 *    the set one.
 *  - `FilterChip` in a `FlowRow` for status, the `CounterpartiesScreen` shape.
 *  - Actions at the end of the form (full-width `Button` then `TextButton`),
 *    the `AccountEditScreen`/`AddAccountBottomSheet` arrangement.
 *
 * Selections apply immediately — the list is filtered live by
 * `matchesTransactionFilters` — so "Apply" only re-queries and goes back.
 * Status is the exception that must re-query as it is picked, because the
 * ledger set itself differs between CONFIRMED and BUFFER.
 */
@Composable
fun TransactionFilterScreen(
    state: TransactionFilterState,
    onStatusChange: (TransactionFilterStatus?) -> Unit,
    onEvent: (TransactionEvent) -> Unit,
    onBack: () -> Unit
) {
    // Which picker page is showing, or null for the filter form itself.
    var page by remember { mutableStateOf<FilterPickPage?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val current = page
            SpendwiseTopBar(
                title = current?.title ?: "Filters",
                // One back affordance for both levels: on the option page it
                // returns to the filters, so the work in progress is not thrown
                // away by a system back press.
                onBack = { if (current == null) onBack() else page = null }
            )
        }
    ) { padding ->

        val current = page
        if (current == null) {
            FilterForm(
                state = state,
                onStatusChange = onStatusChange,
                onOpenPage = { page = it },
                onSetDateRange = { from, to ->
                    onEvent(TransactionEvent.SetDateRange(from, to))
                },
                onApply = {
                    onEvent(TransactionEvent.ApplyFilters)
                    onBack()
                },
                onReset = { onEvent(TransactionEvent.ResetFilters) },
                modifier = Modifier.padding(padding)
            )
        } else {
            FilterOptionPage(
                page = current,
                state = state,
                onSelect = { option ->
                    onEvent(current.selectEvent(option))
                    page = null
                },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

/** The four fields that need a list to choose from. */
private enum class FilterPickPage(val title: String) {
    CATEGORY("Category"),
    FROM_ACCOUNT("From account"),
    TO_ACCOUNT("To account"),
    TAGS("Tags");

    /** The event this page's selection emits; `null` option means "Any". */
    fun selectEvent(option: FilterOption?): TransactionEvent = when (this) {
        CATEGORY -> TransactionEvent.SetCategory(option?.id)
        FROM_ACCOUNT -> TransactionEvent.SetFromAccount(option?.id)
        TO_ACCOUNT -> TransactionEvent.SetToAccount(option?.id)
        // Tags are named, not numbered — the id *is* the label.
        TAGS -> TransactionEvent.SetTag(option?.label)
    }
}
// ── page 1: the filters ──────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterForm(
    state: TransactionFilterState,
    onStatusChange: (TransactionFilterStatus?) -> Unit,
    onOpenPage: (FilterPickPage) -> Unit,
    onSetDateRange: (LocalDate?, LocalDate?) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // contentPadding, not padding: with padding the last rows sit inside a
        // fixed inset and cannot scroll clear of the screen's edge.
        contentPadding = PaddingValues(
            start = Dimens.xxl,
            end = Dimens.xxl,
            top = Dimens.lg,
            bottom = Dimens.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.lg)
    ) {

        item {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {

                FieldLabel(text = "Status")

                // Content-width chips in a wrapping row, the way every other
                // filtered list in the app does it. Stretched to `weight(1f)`
                // they read as segmented buttons, which is a different control.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sm)
                ) {
                    FilterChip(
                        selected = state.status == null,
                        onClick = { onStatusChange(null) },
                        label = { Text("Any") }
                    )

                    TransactionFilterStatus.entries.forEach { status ->
                        FilterChip(
                            selected = state.status == status,
                            onClick = { onStatusChange(status) },
                            label = { Text(status.label) }
                        )
                    }
                }
            }
        }

        item {
            DropdownField(
                label = "Category",
                value = state.options.categoryLabel(state.categoryId).orEmpty(),
                placeholder = "Any",
                enabled = state.options.categories.isNotEmpty(),
                onClick = { onOpenPage(FilterPickPage.CATEGORY) }
            )
        }

        item {
            DropdownField(
                label = "From account",
                value = state.options.accountLabel(state.fromAccountId).orEmpty(),
                placeholder = "Any",
                enabled = state.options.accounts.isNotEmpty(),
                onClick = { onOpenPage(FilterPickPage.FROM_ACCOUNT) }
            )
        }

        item {
            DropdownField(
                label = "To account",
                value = state.options.accountLabel(state.toAccountId).orEmpty(),
                placeholder = "Any",
                enabled = state.options.accounts.isNotEmpty(),
                onClick = { onOpenPage(FilterPickPage.TO_ACCOUNT) }
            )
        }

        item {
            DateRangeField(
                from = state.fromDate,
                to = state.toDate,
                onSelect = { from, to -> onSetDateRange(from, to) }
            )
        }

        item {
            DropdownField(
                label = "Tags",
                value = state.tag.orEmpty(),
                placeholder = "Any",
                enabled = state.options.tags.isNotEmpty(),
                onClick = { onOpenPage(FilterPickPage.TAGS) }
            )
        }

        item {
            // Stacked full-width, as AddAccountBottomSheet does: the primary
            // action on top, the secondary one as a quiet text button beneath.
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                Button(
                    onClick = onApply,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply")
                }

                TextButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset")
                }
            }
        }
    }
}


// ── page 2: pick an option ───────────────────────────────────────────────

/**
 * The option list for one field, filling the screen. Row styling is
 * deliberately identical to `OptionPickerSheet`: full-width rows, 24dp gutters,
 * 16dp vertical padding, `bodyLarge`, and SemiBold + a primary `CheckCircle` on
 * the one that is set.
 */
@Composable
private fun FilterOptionPage(
    page: FilterPickPage,
    state: TransactionFilterState,
    onSelect: (FilterOption?) -> Unit,
    modifier: Modifier = Modifier
) {
    val options: List<FilterOption> = when (page) {
        FilterPickPage.CATEGORY -> state.options.categories
        FilterPickPage.FROM_ACCOUNT, FilterPickPage.TO_ACCOUNT -> state.options.accounts
        // Tags have no id — the label is the key.
        FilterPickPage.TAGS -> state.options.tags.map { FilterOption(0L, it) }
    }

    val selectedLabel: String? = when (page) {
        FilterPickPage.CATEGORY -> state.options.categoryLabel(state.categoryId)
        FilterPickPage.FROM_ACCOUNT -> state.options.accountLabel(state.fromAccountId)
        FilterPickPage.TO_ACCOUNT -> state.options.accountLabel(state.toAccountId)
        FilterPickPage.TAGS -> state.tag
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Dimens.sm)
    ) {

        item(key = "any") {
            FilterOptionRow(
                label = "Any",
                selected = selectedLabel == null,
                onClick = { onSelect(null) }
            )
        }

        items(options, key = { "${page.name}-${it.id}-${it.label}" }) { option ->
            FilterOptionRow(
                label = option.label,
                selected = option.label == selectedLabel,
                onClick = { onSelect(option) }
            )
        }
    }
}

@Composable
private fun FilterOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.xxl, vertical = Dimens.lg)
    ) {

        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )

        if (selected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}


// ── date range ───────────────────────────────────────────────────────────

/**
 * The range picker, on the shared [DropdownField] so it is the same box as the
 * fields above it, opening the same `DatePickerDialog` the transaction form
 * uses for its single date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeField(
    from: LocalDate?,
    to: LocalDate?,
    onSelect: (LocalDate?, LocalDate?) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    DropdownField(
        label = "Date range",
        // Blank when unset, so the shared field renders "Any date" in its
        // placeholder style rather than as a chosen value.
        value = if (from == null && to == null) "" else dateRangeLabel(from, to),
        placeholder = "Any date",
        trailingIcon = Icons.Rounded.CalendarMonth,
        onClick = { showPicker = true }
    )

    if (showPicker) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = from?.toUtcPickerMillis(),
            initialSelectedEndDateMillis = to?.toUtcPickerMillis()
        )

        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSelect(
                            pickerState.selectedStartDateMillis?.toUtcPickerDate(),
                            pickerState.selectedEndDateMillis?.toUtcPickerDate()
                        )
                        showPicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        // Clearing lives in here: the shared field has no slot
                        // for a trailing clear button, and every filter is also
                        // removable from the chips on the list itself.
                        onSelect(null, null)
                        showPicker = false
                    }
                ) {
                    Text("Clear")
                }
            }
        ) {
            DateRangePicker(
                state = pickerState,
                showModeToggle = false,
                title = { Text("Select a date range") }
            )
        }
    }
}


package com.example.spendwise.ui.screens.budget

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.spendwise.ui.components.BudgetCategoryCard
import com.example.spendwise.ui.components.BudgetSummaryCard
import com.example.spendwise.ui.components.CategoryDonut
import com.example.spendwise.ui.components.DonutSlice
import com.example.spendwise.ui.components.EmptyState
import com.example.spendwise.ui.components.donutPalette
import com.example.spendwise.ui.components.formatPaise
import com.example.spendwise.viewmodel.BudgetViewModel
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    modifier: Modifier = Modifier,
    viewModel: BudgetViewModel = hiltViewModel()
) {
            val state by viewModel.uiState.collectAsState()
    val displayMonth by viewModel.displayMonth.collectAsState()
    var showManageDialog by remember { mutableStateOf(false) }
    val formatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    val currentMonthLabel = displayMonth.format(formatter)

    BudgetContent(
        modifier = modifier,
        state = state,
        currentMonth = currentMonthLabel,
        canGoNext = viewModel.canGoNext,
        onPrevMonth = { viewModel.goToPreviousMonth() },
        onNextMonth = { viewModel.goToNextMonth() },
        onManageClick = {
            showManageDialog = true
        },
        onRefreshClick = {
            viewModel.onAction(BudgetViewModel.Action.Refresh)
        },
        onCategoryExpand = { categoryId ->
            viewModel.onAction(BudgetViewModel.Action.ToggleCategory(categoryId))
        }
    )

    if (showManageDialog) {
        ManageBudgetDialog(
            currentBudget = state.budget,
            onDismiss = { showManageDialog = false },
            onSave = { _ ->
                showManageDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetContent(
    state: BudgetViewModel.UiState,
    currentMonth: String,
    canGoNext: Boolean = false,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onManageClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onCategoryExpand: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Budget", fontWeight = FontWeight.Bold)
                        Text(
                            text = currentMonth,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onManageClick) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Manage"
                        )
                    }

                    IconButton(onClick = onRefreshClick) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                }
            )
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPrevMonth) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous Month"
                        )
                    }
                    Text(
                        text = currentMonth,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onNextMonth, enabled = canGoNext) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next Month"
                        )
                    }
                }
            }

            item {
                BudgetSummaryCard(
                    spent = state.spent,
                    budget = state.budget
                )
            }

            if (state.categories.isNotEmpty()) {
                item(key = "category-donut") {
                    CategoryBreakdownCard(
                        categories = state.categories,
                        totalSpentPaise = (state.spent * 100).roundToLong()
                    )
                }
            }

            items(state.categories, key = { it.id }) { category ->
                BudgetCategoryCard(
                    modifier = Modifier.animateItem(),
                    category = category,
                    onExpandClick = {
                        onCategoryExpand(category.id)
                    }
                )
            }

            if (state.categories.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.PieChart,
                        title = "No budget categories yet",
                        message = "Create expense categories to start tracking monthly " +
                            "caps. Spending on a category fills its ring automatically.",
                        actionLabel = "Set up budget",
                        onAction = onManageClick
                    )
                }
            }
        }
    }
}

/**
 * "Spend by category" donut + legend.
 *
 * Complements [BudgetSummaryCard] (a single progress bar for the whole month):
 * this answers *where* the money went. Categories with zero spend are dropped —
 * an empty slice would make the ring harder to read and adds no information —
 * and the ring is capped at the top slices for legibility, with the legend
 * listing exactly what's drawn.
 */
@Composable
fun CategoryBreakdownCard(
    categories: List<BudgetViewModel.Category>,
    totalSpentPaise: Long,
    modifier: Modifier = Modifier
) {
    val palette = donutPalette()
    val slices = remember(categories, palette) {
        categories
            .filter { it.spent > 0.0 }
            .sortedByDescending { it.spent }
            .mapIndexed { index, category ->
                DonutSlice(
                    label = category.title,
                    valuePaise = (category.spent * 100).roundToLong(),
                    color = palette[index % palette.size]
                )
            }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Spend by category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(16.dp))

            if (slices.isEmpty()) {
                Text(
                    text = "Nothing spent this month yet. Once transactions land, " +
                        "the breakdown appears here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryDonut(
                        slices = slices,
                        centerLabel = "spent",
                        centerValue = formatPaise(totalSpentPaise)
                    )

                    Spacer(Modifier.width(20.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        slices.take(LEGEND_MAX_ROWS).forEach { slice ->
                            DonutLegendRow(slice = slice, total = slices.sumOf { it.valuePaise })
                        }
                        if (slices.size > LEGEND_MAX_ROWS) {
                            Text(
                                text = "+${slices.size - LEGEND_MAX_ROWS} more",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** How many legend rows fit beside the donut before it becomes a list screen. */
private const val LEGEND_MAX_ROWS = 5

@Composable
private fun DonutLegendRow(slice: DonutSlice, total: Long) {
    val share = if (total <= 0L) 0 else (slice.valuePaise * 100 / total).toInt()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(10.dp),
            shape = CircleShape,
            color = slice.color
        ) {}

        Spacer(Modifier.width(8.dp))

        Text(
            text = slice.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(Modifier.width(6.dp))

        Text(
            text = "$share%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ManageBudgetDialog(
    currentBudget: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var budgetText by remember { mutableStateOf(currentBudget.toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Monthly Budget") },
        text = {
            OutlinedTextField(
                value = budgetText,
                onValueChange = { budgetText = it },
                label = { Text("Budget Amount (₹)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val newBudget = budgetText.toDoubleOrNull() ?: currentBudget
                    onSave(newBudget)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

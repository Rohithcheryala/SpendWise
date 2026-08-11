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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.spendwise.ui.components.BudgetCategoryCard
import com.example.spendwise.ui.components.BudgetSummaryCard
import com.example.spendwise.viewmodel.BudgetViewModel
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    modifier: Modifier = Modifier,
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showManageDialog by remember { mutableStateOf(false) }
    // monthOffset: 0 = current month, 1 = one month back, 2 = two months back, etc.
    var monthOffset by remember { mutableIntStateOf(0) }

    val formatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    val displayMonth = YearMonth.now().minusMonths(monthOffset.toLong()).format(formatter)

    BudgetContent(
        modifier = modifier,
        state = state,
        currentMonth = displayMonth,
        canGoNext = monthOffset > 0,
        onPrevMonth = { monthOffset++ },
        onNextMonth = { if (monthOffset > 0) monthOffset-- },
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
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Month")
                    }
                    Text(
                        text = currentMonth,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onNextMonth, enabled = canGoNext) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Month")
                    }
                }
            }

            item {
                BudgetSummaryCard(
                    spent = state.spent,
                    budget = state.budget
                )
            }

            items(state.categories, key = { it.id }) { category ->
                BudgetCategoryCard(
                    category = category,
                    onExpandClick = {
                        onCategoryExpand(category.id)
                    }
                )
            }

            if (state.categories.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(72.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.PieChart,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                            Text(
                                text = "No budget categories set",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap the settings icon to manage your budget.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
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

package com.example.spendwise.ui.screens.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.spendwise.ui.components.BudgetCategoryCard
import com.example.spendwise.ui.components.BudgetSummaryCard
import com.example.spendwise.viewmodel.BudgetViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    modifier: Modifier = Modifier,
    viewModel: BudgetViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    BudgetContent(
        modifier = modifier,
        state = state,
        onManageClick = {
            viewModel.onAction(BudgetViewModel.Action.ManageBudgets)
        },
        onRefreshClick = {
            viewModel.onAction(BudgetViewModel.Action.Refresh)
        },
        onCategoryExpand = { categoryId ->
            viewModel.onAction(BudgetViewModel.Action.ToggleCategory(categoryId))
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetContent(
    state: BudgetViewModel.UiState,
    onManageClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onCategoryExpand: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text("Budget")
                },
//                subtitle = {
//                    Text(month)
//                },
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
                BudgetSummaryCard(
                    spent = state.spent,
                    budget = state.budget
                )
            }

            items(state.categories) { category ->

                BudgetCategoryCard(
                    category = category,
                    onExpandClick = {
                        onCategoryExpand(category.id)
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BudgetScreenPreview() {
    BudgetScreen()
}


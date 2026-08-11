package com.example.spendwise.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import com.example.spendwise.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val repository: CategoryRepository
) : ViewModel() {

    data class UiState(
        val month: String = "July 2026",
        val spent: Double = 1118.9,
        val budget: Double = 10_000.0,
        val isRefreshing: Boolean = false,
        val categories: List<Category> = previewCategories
    )

    data class Category(
        val id: Long,
        val icon: ImageVector,
        val title: String,
        val spent: Double,
        val budget: Double,
        val expanded: Boolean = true,
        val children: List<Subcategory> = emptyList()
    )

    data class Subcategory(
        val id: Long,
        val title: String,
        val spent: Double,
        val budget: Double
    )

    sealed interface Action {
        data object Refresh : Action
        data object ManageBudgets : Action
        data class ToggleCategory(val id: Long) : Action
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()

            Action.ManageBudgets -> manageBudgets()

            is Action.ToggleCategory -> toggleCategory(action.id)
        }
    }

    private fun refresh() {
        // TODO
    }

    private fun manageBudgets() {
        // TODO
    }

    private fun toggleCategory(categoryId: Long) {
        _uiState.update { state ->
            state.copy(
                categories = state.categories.map { category ->
                    if (category.id == categoryId) {
                        category.copy(expanded = !category.expanded)
                    } else {
                        category
                    }
                }
            )
        }
    }

    companion object {

        private val previewCategories = listOf(
            Category(
                id = 1,
                icon = Icons.Outlined.Restaurant,
                title = "Food & Drink",
                spent = 1008.9,
                budget = 4000.0,
                children = listOf(
                    Subcategory(
                        id = 1,
                        title = "Groceries",
                        spent = 136.0,
                        budget = 1000.0
                    ),
                    Subcategory(
                        id = 2,
                        title = "Restaurants",
                        spent = 872.9,
                        budget = 3000.0
                    )
                )
            ),
            Category(
                id = 2,
                icon = Icons.Outlined.Home,
                title = "Home",
                spent = 0.0,
                budget = 3000.0,
                children = listOf(
                    Subcategory(
                        id = 3,
                        title = "Rent",
                        spent = 0.0,
                        budget = 2500.0
                    ),
                    Subcategory(
                        id = 4,
                        title = "Utilities",
                        spent = 0.0,
                        budget = 500.0
                    )
                )
            ),
            Category(
                id = 3,
                icon = Icons.Outlined.DirectionsCar,
                title = "Transport",
                spent = 110.0,
                budget = 1000.0,
                children = listOf(
                    Subcategory(
                        id = 5,
                        title = "Fuel",
                        spent = 90.0,
                        budget = 800.0
                    ),
                    Subcategory(
                        id = 6,
                        title = "Parking",
                        spent = 20.0,
                        budget = 200.0
                    )
                )
            )
        )
    }
}
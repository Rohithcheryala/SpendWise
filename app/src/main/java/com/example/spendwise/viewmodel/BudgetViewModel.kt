package com.example.spendwise.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendwise.data.database.dao.BudgetDao
import com.example.spendwise.data.database.dao.AccountDao
import com.example.spendwise.data.database.dao.TransactionLineDao
import com.example.spendwise.data.database.entity.BudgetEntity
import com.example.spendwise.data.database.entity.AccountEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val accountDao: AccountDao,
    private val budgetDao: BudgetDao,
    private val transactionLineDao: TransactionLineDao,
) : ViewModel() {

    data class UiState(
        val spent: Double = 0.0,
        val budget: Double = 0.0,
        val isRefreshing: Boolean = false,
        val categories: List<Category> = emptyList()
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

    /** Current month being viewed; starts at today and is moved by the screen. */
    private var currentMonth: YearMonth = YearMonth.now()

    /** Backing year-month exposed as a StateFlow so the screen recomposes on change. */
    private val _displayMonth = MutableStateFlow(currentMonth)
    val displayMonth = _displayMonth

    init {
        refresh()
    }

    /** True when the user can move forward to a newer month (i.e. not past today). */
    val canGoNext: Boolean
        get() = currentMonth < YearMonth.now()

    /** Move one month back; reloads totals. */
    fun goToPreviousMonth() {
        currentMonth = currentMonth.minusMonths(1)
        _displayMonth.value = currentMonth
        refresh()
    }

    /** Move one month forward (only allowed up to the current real month). */
    fun goToNextMonth() {
        if (canGoNext) {
            currentMonth = currentMonth.plusMonths(1)
            _displayMonth.value = currentMonth
            refresh()
        }
    }

    fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()
            Action.ManageBudgets -> {} // budgets are edited per-category elsewhere
            is Action.ToggleCategory -> toggleCategory(action.id)
        }
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

    /** Load real expense categories, their budgets and spend for [currentMonth]. */
    fun refresh() {
        viewModelScope.launch {
            val monthStart = currentMonth.atDay(1)
                .atTime(0, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val monthEnd = currentMonth.atEndOfMonth()
                .atTime(23, 59, 59, 999_999_999)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            _uiState.update { it.copy(isRefreshing = true) }

            runCatching {
                val budgets = budgetDao.getActiveBudgets(monthStart).first()
                    .associateBy { it.accountId }
                val roots = accountDao.getRootCategoryAccounts().first()
                    .filter { it.accountClass == KIND_EXPENSE }

                val categories = roots.map { root ->
                    root.toCategory(budgets, monthStart, monthEnd)
                }

                UiState(
                    spent = categories.sumOf { it.spent },
                    budget = categories.sumOf { it.budget },
                    isRefreshing = false,
                    categories = categories,
                )
            }.onSuccess { result ->
                _uiState.value = result
            }.onFailure {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private suspend fun AccountEntity.toCategory(
        budgets: Map<Long, BudgetEntity>,
        monthStart: Long,
        monthEnd: Long,
    ): Category {
        val children = accountDao.listChildren(id)
            .filter { it.accountClass == KIND_EXPENSE }
            .map { it.toSubcategory(budgets, monthStart, monthEnd) }
        val ownSpent = transactionLineDao.sumConfirmedForAccountInMonth(id, monthStart, monthEnd)
        val ownBudget = budgets[id]?.amountPaise ?: 0L
        return Category(
            id = id,
            icon = iconFor(icon),
            title = name,
            spent = (ownSpent + children.sumOf { (it.spent * 100).toLong() }) / 100.0,
            budget = (ownBudget + children.sumOf { (it.budget * 100).toLong() }) / 100.0,
            expanded = true,
            children = children,
        )
    }

    private suspend fun AccountEntity.toSubcategory(
        budgets: Map<Long, BudgetEntity>,
        monthStart: Long,
        monthEnd: Long,
    ): Subcategory = Subcategory(
        id = id,
        title = name,
        spent = transactionLineDao.sumConfirmedForAccountInMonth(id, monthStart, monthEnd) / 100.0,
        budget = (budgets[id]?.amountPaise ?: 0L) / 100.0,
    )

            companion object {
        const val KIND_EXPENSE = "expense"

        /** Map an AccountEntity icon-name hint to a vector icon; default to a generic. */
        fun iconFor(name: String?): ImageVector = when (name?.trim()?.lowercase()) {
            "food", "restaurant", "groceries", "shopping" -> Icons.Filled.Restaurant
            "home", "rent", "utilities" -> Icons.Filled.Home
            "transport", "car", "fuel", "parking" -> Icons.Filled.DirectionsCar
            "savings", "account", "bank" -> Icons.Filled.AccountBalance
            else -> Icons.Filled.Category
        }
    }
}
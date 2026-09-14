package com.example.spendwise.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendwise.data.database.dao.BudgetDao
import com.example.spendwise.data.database.dao.CategoryDao
import com.example.spendwise.data.database.dao.TransactionLineDao
import com.example.spendwise.data.database.entity.CategoryEntity
import com.example.spendwise.ui.screens.categories.CategoryUiModel
import com.example.spendwise.ui.screens.categories.SubcategoryUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * Real source of truth for the Categories screen: persisted categories plus
 * their live confirmed spend and active budgets for the current month — no
 * placeholder rows (mirrors [BudgetViewModel]'s loading logic).
 */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val transactionLineDao: TransactionLineDao,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val categories: List<CategoryUiModel> = emptyList(),
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val month = YearMonth.now()
            val monthStart = month.atDay(1)
                .atTime(0, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val monthEnd = month.atEndOfMonth()
                .atTime(23, 59, 59, 999_999_999)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            _uiState.value = _uiState.value.copy(isLoading = _uiState.value.categories.isEmpty())

            runCatching {
                val budgets = budgetDao.getActiveBudgets(monthStart).first()
                    .associateBy { it.categoryId }
                val roots = categoryDao.getRootCategories().first()
                    .filter { it.kind == KIND_EXPENSE }

                roots.mapIndexed { index, root ->
                    val children = categoryDao.getChildren(root.id).first()
                        .filter { it.kind == KIND_EXPENSE }
                        .map { child ->
                            SubcategoryUiModel(
                                id = child.id,
                                title = child.name,
                                spent = transactionLineDao.sumConfirmedForCategoryInMonth(
                                    child.id, monthStart, monthEnd
                                ) / 100.0,
                                budget = (budgets[child.id]?.amountPaise ?: 0L) / 100.0,
                            )
                        }
                    val ownSpent = transactionLineDao.sumConfirmedForCategoryInMonth(
                        root.id, monthStart, monthEnd
                    )
                    CategoryUiModel(
                        id = root.id,
                        title = root.name,
                        icon = BudgetViewModel.iconFor(root.icon),
                        color = PALETTE[index % PALETTE.size],
                        spent = (ownSpent + children.sumOf { (it.spent * 100).toLong() }) / 100.0,
                        budget = ((budgets[root.id]?.amountPaise ?: 0L) +
                            children.sumOf { (it.budget * 100).toLong() }) / 100.0,
                        subcategories = children,
                    )
                }
            }.onSuccess { categories ->
                _uiState.value = UiState(isLoading = false, categories = categories)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Could not load categories",
                )
            }
        }
    }

    /** Persist a new root expense category with an optional monthly budget. */
    fun addCategory(name: String, monthlyBudget: Double) {
        viewModelScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                val id = categoryDao.insert(
                    CategoryEntity(
                        name = name.trim(),
                        kind = KIND_EXPENSE,
                        createdAt = now,
                    )
                )
                val budgetPaise = (monthlyBudget * 100).toLong()
                if (budgetPaise > 0) {
                    budgetDao.insert(
                        com.example.spendwise.data.database.entity.BudgetEntity(
                            categoryId = id,
                            amountPaise = budgetPaise,
                            effectiveFrom = YearMonth.now().atDay(1)
                                .atTime(0, 0)
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli(),
                            createdAt = now,
                        )
                    )
                }
            }.onSuccess {
                refresh()
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun consumeError() {
        if (_uiState.value.error != null) {
            _uiState.value = _uiState.value.copy(error = null)
        }
    }

    companion object {
        const val KIND_EXPENSE = "expense"

        /** Stable per-index accent colors for category icons. */
        val PALETTE = listOf(
            Color(0xFF2563EB),
            Color(0xFF0D9488),
            Color(0xFFDC2626),
            Color(0xFF8B5CF6),
            Color(0xFFF59E0B),
            Color(0xFF16A34A),
            Color(0xFFDB2777),
        )
    }
}
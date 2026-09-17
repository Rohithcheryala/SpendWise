package com.example.spendwise.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendwise.data.database.dao.BudgetDao
import com.example.spendwise.data.database.dao.AccountDao
import com.example.spendwise.data.database.dao.TransactionLineDao
import com.example.spendwise.data.database.entity.BudgetEntity
import com.example.spendwise.data.database.entity.AccountEntity
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
    private val accountDao: AccountDao,
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
                val budgets = budgetDao.getByPeriod(month.toString()).first()
                    .associateBy { it.accountId }
                val roots = accountDao.getRootCategoryAccounts().first()
                    .filter { it.accountClass == KIND_EXPENSE }

                roots.mapIndexed { index, root ->
                    val children = accountDao.listChildren(root.id)
                        .filter { it.accountClass == KIND_EXPENSE }
                        .map { child ->
                            SubcategoryUiModel(
                                id = child.id,
                                title = child.name,
                                spent = transactionLineDao.sumConfirmedForAccountInMonth(
                                    child.id, monthStart, monthEnd
                                ) / 100.0,
                                budget = (budgets[child.id]?.amountPaise ?: 0L) / 100.0,
                            )
                        }
                    val ownSpent = transactionLineDao.sumConfirmedForAccountInMonth(
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
                        ownBudget = (budgets[root.id]?.amountPaise ?: 0L) / 100.0,
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
                val id = accountDao.insert(
                    AccountEntity(
                        name = name.trim(),
                        accountClass = KIND_EXPENSE,
                        createdAt = System.currentTimeMillis(),
                    )
                )
                upsertMonthlyBudget(id, monthlyBudget)
            }.onSuccess {
                refresh()
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    /** Persist a new sub-category under [parentId] with an optional monthly budget. */
    fun addSubcategory(parentId: Long, name: String, monthlyBudget: Double) {
        viewModelScope.launch {
            runCatching {
                val id = accountDao.insert(
                    AccountEntity(
                        name = name.trim(),
                        accountClass = KIND_EXPENSE,
                        parentId = parentId,
                        createdAt = System.currentTimeMillis(),
                    )
                )
                upsertMonthlyBudget(id, monthlyBudget)
            }.onSuccess {
                refresh()
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    /** Rename any category row (root or sub). */
    fun renameCategory(id: Long, name: String) {
        viewModelScope.launch {
            runCatching {
                val account = accountDao.getById(id) ?: return@runCatching
                accountDao.update(account.copy(name = name.trim()))
            }.onSuccess {
                refresh()
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    /**
     * Set (or clear, with a blank/zero budget) the current month's budget on
     * any category row — updates the active budget in place so history that
     * references it stays intact.
     */
    fun setCategoryBudget(accountId: Long, monthlyBudget: Double) {
        viewModelScope.launch {
            runCatching {
                val period = YearMonth.now().toString()
                val existing = budgetDao.getFor(accountId, period)
                val paise = (monthlyBudget * 100).toLong()
                when {
                    paise > 0 && existing != null ->
                        budgetDao.update(existing.copy(amountPaise = paise))
                    paise > 0 -> budgetDao.insert(
                        BudgetEntity(
                            accountId = accountId,
                            period = period,
                            amountPaise = paise,
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                    existing != null -> budgetDao.delete(existing)
                }
            }.onSuccess {
                refresh()
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    /** Insert or update the current month's budget for one category row. */
    private suspend fun upsertMonthlyBudget(accountId: Long, monthlyBudget: Double) {
        val paise = (monthlyBudget * 100).toLong()
        if (paise <= 0) return
        val period = YearMonth.now().toString()
        val existing = budgetDao.getFor(accountId, period)
        if (existing != null) {
            budgetDao.update(existing.copy(amountPaise = paise))
        } else {
            budgetDao.insert(
                BudgetEntity(
                    accountId = accountId,
                    period = period,
                    amountPaise = paise,
                    createdAt = System.currentTimeMillis(),
                )
            )
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
package com.example.spendwise.ui.screens.categories

import androidx.compose.material.icons.Icons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.spendwise.ui.components.SpendwiseTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.spendwise.ui.components.formatRupees
import com.example.spendwise.ui.theme.Dimens
import com.example.spendwise.ui.theme.SpendwiseTheme
import com.example.spendwise.viewmodel.CategoriesViewModel
import com.example.spendwise.ui.components.SpendwiseCard

data class CategoryUiModel(
    val id: Long,
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val spent: Double,
    val budget: Double,
    /** The category's own budget — [budget] includes subcategory sums. */
    val ownBudget: Double = 0.0,
    val subcategories: List<SubcategoryUiModel> = emptyList(),
    val isExpanded: Boolean = false
)

data class SubcategoryUiModel(
    val id: Long,
    val title: String,
    val spent: Double,
    val budget: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    // Real data: persisted categories with live confirmed spend and active
    // budgets for the current month (same loading logic as the Budget screen).
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var expandedIds by remember { mutableStateOf(setOf<Long>()) }

    var editorRequest by remember { mutableStateOf<CategoryEditorRequest?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
        topBar = {
            // Create lives on the FAB (the convention on Transactions,
            // Accounts and Friends) — a second "+" up here was pure noise.
            SpendwiseTopBar(
                title = "Categories",
                onBack = onNavigateBack,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editorRequest = CategoryEditorRequest(
                        title = "Create Category",
                        confirmLabel = "Add",
                    ) { name, monthlyBudget ->
                        viewModel.addCategory(name, monthlyBudget)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Category")
            }
        }
    ) { padding ->

        if (uiState.isLoading && uiState.categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.categories, key = { it.id }) { category ->
                    CategoryItemCard(
                        category = category.copy(isExpanded = category.id in expandedIds),
                        onToggleExpand = {
                            expandedIds = if (category.id in expandedIds) {
                                expandedIds - category.id
                            } else {
                                expandedIds + category.id
                            }
                        },
                        onEdit = {
                            editorRequest = CategoryEditorRequest(
                                title = "Edit Category",
                                initialName = category.title,
                                initialBudget = formatBudgetInput(category.ownBudget),
                            ) { name, monthlyBudget ->
                                viewModel.renameCategory(category.id, name)
                                viewModel.setCategoryBudget(category.id, monthlyBudget)
                            }
                        },
                        onAddSub = {
                            editorRequest = CategoryEditorRequest(
                                title = "Add Subcategory",
                                parentTitle = category.title,
                                confirmLabel = "Add",
                            ) { name, monthlyBudget ->
                                viewModel.addSubcategory(category.id, name, monthlyBudget)
                            }
                        },
                        onEditSub = { sub ->
                            editorRequest = CategoryEditorRequest(
                                title = "Edit Subcategory",
                                parentTitle = category.title,
                                initialName = sub.title,
                                initialBudget = formatBudgetInput(sub.budget),
                            ) { name, monthlyBudget ->
                                viewModel.renameCategory(sub.id, name)
                                viewModel.setCategoryBudget(sub.id, monthlyBudget)
                            }
                        },
                    )
                }
            }
        }
    }

    editorRequest?.let { request ->
        CategoryEditorDialog(
            title = request.title,
            parentTitle = request.parentTitle,
            initialName = request.initialName,
            initialBudget = request.initialBudget,
            confirmLabel = request.confirmLabel,
            onDismiss = { editorRequest = null },
            onConfirm = { name, monthlyBudget ->
                request.onConfirm(name, monthlyBudget)
                editorRequest = null
            },
        )
    }
}

@Composable
fun CategoryItemCard(
    category: CategoryUiModel,
    onToggleExpand: () -> Unit,
    onEdit: () -> Unit,
    onAddSub: () -> Unit,
    onEditSub: (SubcategoryUiModel) -> Unit,
) {
    // Guard the division: real categories can have budget == 0.0 (no budget
    // set), giving 0/0 = NaN or X/0 = Infinity — coerceIn passes NaN through
    // and a NaN progress crashes LinearProgressIndicator. Same guard as
    // BudgetCategoryCard/BudgetSummary.
    val progress = if (category.budget > 0)
        (category.spent / category.budget).coerceIn(0.0, 1.0).toFloat()
    else 0f
    val progressColor = when {
        progress >= 0.9f -> MaterialTheme.colorScheme.error
        progress >= 0.75f -> SpendwiseTheme.colors.warning
        else -> MaterialTheme.colorScheme.primary
    }

    SpendwiseCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = category.color.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = category.color,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${formatRupees(category.spent)} of ${formatRupees(category.budget)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit ${category.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Icon(
                    imageVector = if (category.isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            AnimatedVisibility(visible = category.isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    category.subcategories.forEach { sub ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable { onEditSub(sub) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• ${sub.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${formatRupees(sub.spent)} of ${formatRupees(sub.budget)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Edit ${sub.title}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    // Subcategories are plain child ledger rows — anyone can
                    // grow the tree right where it will be used.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable(onClick = onAddSub)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Add subcategory",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/** What the category editor dialog is currently open for. */
data class CategoryEditorRequest(
    val title: String,
    val parentTitle: String? = null,
    val initialName: String = "",
    val initialBudget: String = "",
    val confirmLabel: String = "Save",
    val onConfirm: (name: String, monthlyBudget: Double) -> Unit,
)

/** "500.0" → "500"; blank for unset budgets. */
private fun formatBudgetInput(value: Double): String =
    if (value > 0.0 && value % 1.0 == 0.0) value.toLong().toString() else if (value > 0.0) value.toString() else ""

/**
 * The create/edit form for categories and subcategories — one themed dialog
 * for all four flows. A blank budget clears it.
 */
@Composable
fun CategoryEditorDialog(
    title: String,
    parentTitle: String?,
    initialName: String,
    initialBudget: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, monthlyBudget: Double) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var budget by remember { mutableStateOf(initialBudget) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                parentTitle?.let { parent ->
                    Text(
                        text = "under $parent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = budget,
                    onValueChange = { budget = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Monthly Budget (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("Optional — clear to remove the budget") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), budget.toDoubleOrNull() ?: 0.0) },
                enabled = name.isNotBlank()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

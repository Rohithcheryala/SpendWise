package com.example.spendwise.ui.screens.categories

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp

data class CategoryUiModel(
    val id: Long,
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val spent: Double,
    val budget: Double,
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
    modifier: Modifier = Modifier
) {
    var categories by remember {
        mutableStateOf(
            listOf(
                CategoryUiModel(
                    1,
                    "Food & Dining",
                    Icons.Default.Restaurant,
                    Color(0xFFEF4444),
                    8420.0,
                    12000.0,
                    listOf(
                        SubcategoryUiModel(101, "Groceries", 4200.0, 6000.0),
                        SubcategoryUiModel(102, "Restaurants", 3100.0, 4000.0),
                        SubcategoryUiModel(103, "Coffee & Snacks", 1120.0, 2000.0)
                    )
                ),
                CategoryUiModel(
                    2,
                    "Housing & Utilities",
                    Icons.Default.Home,
                    Color(0xFF3B82F6),
                    22500.0,
                    25000.0,
                    listOf(
                        SubcategoryUiModel(201, "Rent", 18000.0, 18000.0),
                        SubcategoryUiModel(202, "Electricity & Water", 2800.0, 4000.0),
                        SubcategoryUiModel(203, "Internet & WiFi", 1700.0, 3000.0)
                    )
                ),
                CategoryUiModel(
                    3,
                    "Transportation",
                    Icons.Default.DirectionsCar,
                    Color(0xFFF59E0B),
                    3450.0,
                    6000.0,
                    listOf(
                        SubcategoryUiModel(301, "Fuel", 2500.0, 4000.0),
                        SubcategoryUiModel(302, "Cab & Public Transit", 950.0, 2000.0)
                    )
                ),
                CategoryUiModel(
                    4,
                    "Shopping & Lifestyle",
                    Icons.Default.ShoppingBag,
                    Color(0xFFEC4899),
                    5600.0,
                    8000.0,
                    listOf(
                        SubcategoryUiModel(401, "Clothing", 3200.0, 5000.0),
                        SubcategoryUiModel(402, "Electronics", 2400.0, 3000.0)
                    )
                ),
                CategoryUiModel(
                    5,
                    "Healthcare & Fitness",
                    Icons.Default.MedicalServices,
                    Color(0xFF10B981),
                    1200.0,
                    4000.0,
                    listOf(
                        SubcategoryUiModel(501, "Medicines", 700.0, 2000.0),
                        SubcategoryUiModel(502, "Gym & Sports", 500.0, 2000.0)
                    )
                ),
                CategoryUiModel(
                    6, "Entertainment", Icons.Default.Movie, Color(0xFF8B5CF6), 2100.0, 3500.0,
                    listOf(
                        SubcategoryUiModel(601, "Movies & Events", 1200.0, 2000.0),
                        SubcategoryUiModel(602, "Subscriptions", 900.0, 1500.0)
                    )
                )
            )
        )
    }

    var showAddCategoryDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Categories", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showAddCategoryDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Category")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddCategoryDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Category")
            }
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(categories, key = { it.id }) { category ->
                CategoryItemCard(
                    category = category,
                    onToggleExpand = {
                        categories = categories.map { c ->
                            if (c.id == category.id) c.copy(isExpanded = !c.isExpanded) else c
                        }
                    }
                )
            }
        }
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            onDismiss = { showAddCategoryDialog = false },
            onAdd = { newCat ->
                categories = categories + newCat
                showAddCategoryDialog = false
            }
        )
    }
}

@Composable
fun CategoryItemCard(
    category: CategoryUiModel,
    onToggleExpand: () -> Unit
) {
    val progress = (category.spent / category.budget).coerceIn(0.0, 1.0).toFloat()
    val progressColor = when {
        progress >= 0.9f -> MaterialTheme.colorScheme.error
        progress >= 0.75f -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
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
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "₹${"%,.0f".format(category.spent)} of ₹${"%,.0f".format(category.budget)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (category.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
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
                    .clip(RoundedCornerShape(3.dp)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            AnimatedVisibility(visible = category.isExpanded && category.subcategories.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    category.subcategories.forEach { sub ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• ${sub.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "₹${"%,.0f".format(sub.spent)} / ₹${"%,.0f".format(sub.budget)}",
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
fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onAdd: (CategoryUiModel) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Category Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = budget,
                    onValueChange = { budget = it },
                    label = { Text("Monthly Budget (₹)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(
                        CategoryUiModel(
                            id = System.currentTimeMillis(),
                            title = title.ifBlank { "Custom Category" },
                            icon = Icons.Default.ShoppingBag,
                            color = Color(0xFF10B981),
                            spent = 0.0,
                            budget = budget.toDoubleOrNull() ?: 5000.0
                        )
                    )
                },
                enabled = title.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

package com.example.spendwise.ui.components


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spendwise.viewmodel.BudgetViewModel

@Composable
fun BudgetCategoryCard(
    modifier: Modifier = Modifier,
    category: BudgetViewModel.Category,
    onExpandClick: () -> Unit
) {

    Card(
        modifier = modifier.fillMaxWidth()
    ) {

        Column {

            ListItem(
                headlineContent = {
                    Text(
                        category.title,
                        fontWeight = FontWeight.SemiBold
                    )
                },

                supportingContent = {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                    )
                },

                trailingContent = {
                    IconButton(
                        onClick = onExpandClick
                    ) {
                        Icon(
                            imageVector = if (category.expanded)
                                Icons.Rounded.ExpandLess
                            else
                                Icons.Rounded.ExpandMore,
                            contentDescription = null
                        )
                    }
                },

                overlineContent = {
                    Text(
                        "₹${"%,.0f".format(category.spent)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            )

            AnimatedVisibility(category.expanded) {

                Column {

                    Divider()

                    category.children.forEach { child ->

                        BudgetSubcategoryItem(
                            item = child
                        )
                    }
                }
            }
        }
    }
}
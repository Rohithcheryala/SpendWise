package com.example.spendwise.ui.components


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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

            Column(
                modifier = Modifier.padding(20.dp)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {

                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.height(2.dp))

                        Text(
                            text = "₹${"%,.0f".format(category.spent)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onExpandClick) {
                        Icon(
                            imageVector = if (category.expanded)
                                Icons.Rounded.ExpandLess
                            else
                                Icons.Rounded.ExpandMore,
                            contentDescription = null
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { 0.35f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

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
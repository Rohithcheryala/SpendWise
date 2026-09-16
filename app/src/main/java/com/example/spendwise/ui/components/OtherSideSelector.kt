package com.example.spendwise.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spendwise.ui.screens.transactiondetail.OtherSide

/**
 * The one structural question the in/out direction can't answer: where does
 * the OTHER SIDE of the money land? A category — an internal division the
 * money is filed under (counterparty still names who it was to/from) —,
 * another of my own accounts (transfer), or a person who owes me (loan).
 * Ported from naa's OtherSideToggle. Deliberately not called a "transaction
 * type" and never offering Expense/Income: the direction pills cover those,
 * and the Expense/Income/Transfer label on a saved entry is DERIVED from the
 * resulting ledger lines, never stored.
 */
@Composable
fun OtherSideSelector(
    selected: OtherSide,
    onSelected: (OtherSide) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Type",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OtherSide.entries.forEach { side ->
                    val isSelected = selected == side

                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHigh,
                        label = "otherside_bg"
                    )

                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "otherside_text"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(bgColor)
                            .clickable { onSelected(side) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = side.icon,
                                contentDescription = null,
                                tint = textColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = side.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = textColor
                            )
                        }
                    }
                }
            }
        }
    }
}

private val OtherSide.label: String
    get() = when (this) {
        OtherSide.CATEGORY -> "Category"
        OtherSide.TRANSFER -> "Transfer"
        OtherSide.LOAN -> "Loan"
    }

private val OtherSide.icon: androidx.compose.ui.graphics.vector.ImageVector
    get() = when (this) {
        OtherSide.CATEGORY -> Icons.Filled.Sell          // a spending category
        OtherSide.TRANSFER -> Icons.Rounded.SwapHoriz  // my account ↔ my account
        OtherSide.LOAN -> Icons.Filled.Handshake         // a person who owes me
    }

@Preview(showBackground = true)
@Composable
private fun CategoryPreview() {
    OtherSideSelector(
        selected = OtherSide.CATEGORY,
        onSelected = {}
    )
}
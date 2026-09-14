package com.example.spendwise.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spendwise.ui.theme.SpendwiseTheme

@Composable
fun FriendCard(
    name: String,
    amountGiven: Double,
    amountReceived: Double,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {

    val balance = amountGiven - amountReceived

    val (balanceColor, balanceLabel) = when {
        balance > 0 -> MaterialTheme.colorScheme.primary to "OWES YOU"
        balance < 0 -> MaterialTheme.colorScheme.error to "YOU OWE"
        else -> MaterialTheme.colorScheme.onSurfaceVariant to "SETTLED"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .let {
                if (onClick != null)
                    it.clickable(onClick = onClick)
                else
                    it
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            FriendAvatar(name)

            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Icon(
                            Icons.Rounded.ArrowUpward,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )

                        Spacer(Modifier.width(4.dp))

                        MoneyText(
                            text = formatRupees(amountGiven),
                            size = MoneySize.LABEL,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Icon(
                            Icons.Rounded.ArrowDownward,
                            null,
                            tint = SpendwiseTheme.colors.income,
                            modifier = Modifier.size(18.dp)
                        )

                        Spacer(Modifier.width(4.dp))

                        MoneyText(
                            text = formatRupees(amountReceived),
                            size = MoneySize.LABEL,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {

                    Text(
                        text = "Direct • ${formatRupees(kotlin.math.abs(balance))}",
                        modifier = Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 5.dp
                        ),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(
                horizontalAlignment = Alignment.End
            ) {

                MoneyText(
                    text = buildString {
                        if (balance > 0) append("+")
                        append(formatRupees(kotlin.math.abs(balance)))
                    },
                    size = MoneySize.HEADING,
                    color = balanceColor
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = balanceLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = balanceColor
                )
            }
        }
    }
}
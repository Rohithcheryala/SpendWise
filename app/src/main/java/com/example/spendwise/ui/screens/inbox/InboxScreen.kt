package com.example.spendwise.ui.screens.inbox

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.spendwise.core.extensions.toFormattedDateTime
import com.example.spendwise.viewmodel.InboxViewModel

data class PendingTransactionItem(
    val id: String,
    val sender: String,
    val amount: String,
    val isDebit: Boolean,
    val account: String,
    val date: String,
    val rawSms: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: InboxViewModel = hiltViewModel()
) {
    val state = viewModel.uiState

    var pendingItems by remember(state.messages) {
        mutableStateOf(
            if (state.messages.isNotEmpty()) {
                state.messages.mapIndexed { index, msg ->
                    PendingTransactionItem(
                        id = msg.id.toString(),
                        sender = msg.address.orEmpty().ifBlank { "HDFCBK" },
                        amount = "₹${(index + 1) * 250 + 49}",
                        isDebit = index % 3 != 0,
                        account = "HDFC Bank •••• 4921",
                        date = msg.date.toFormattedDateTime(),
                        rawSms = msg.body.orEmpty()
                            .ifBlank { "Rs. ${(index + 1) * 250 + 49} debited from A/C 4921 at Starbucks on ${msg.date.toFormattedDateTime()}." }
                    )
                }
            } else {
                listOf(
                    PendingTransactionItem(
                        id = "1",
                        sender = "HDFCBK",
                        amount = "₹450.00",
                        isDebit = true,
                        account = "HDFC A/C 4921",
                        date = "Today • 2:15 PM",
                        rawSms = "Rs 450.00 debited from A/C **4921 at Swiggy on 11-08-26. Ref UPI/428190281."
                    ),
                    PendingTransactionItem(
                        id = "2",
                        sender = "SBIBANK",
                        amount = "₹12,500.00",
                        isDebit = false,
                        account = "SBI A/C 8102",
                        date = "Yesterday • 10:30 AM",
                        rawSms = "Rs 12500.00 credited to A/C **8102 by NEFT-SALARY. Available bal Rs 150000."
                    ),
                    PendingTransactionItem(
                        id = "3",
                        sender = "ICICIBK",
                        amount = "₹1,890.00",
                        isDebit = true,
                        account = "ICICI Card 9012",
                        date = "09 Aug • 8:45 PM",
                        rawSms = "Spent Rs 1890.00 on ICICI Bank Credit Card ending 9012 at Zara Store."
                    )
                )
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Buffer Inbox", fontWeight = FontWeight.Bold)
                        Text(
                            text = "${pendingItems.size} pending review",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->

        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            pendingItems.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Inbox,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Inbox Empty",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "All detected bank SMS transactions have been reviewed and imported!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Auto-Detected Transactions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Button(
                                onClick = { pendingItems = emptyList() },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    "Approve All (${pendingItems.size})",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    items(pendingItems, key = { it.id }) { item ->
                        InboxPendingCard(
                            item = item,
                            onApprove = {
                                pendingItems = pendingItems.filter { it.id != item.id }
                            },
                            onDismiss = {
                                pendingItems = pendingItems.filter { it.id != item.id }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InboxPendingCard(
    item: PendingTransactionItem,
    onApprove: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = if (item.isDebit) MaterialTheme.colorScheme.errorContainer else Color(
                        0xFFDCFCE7
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Sms,
                            contentDescription = null,
                            tint = if (item.isDebit) MaterialTheme.colorScheme.error else Color(
                                0xFF16A34A
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.sender,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.account,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = (if (item.isDebit) "−" else "+") + item.amount,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isDebit) MaterialTheme.colorScheme.error else Color(
                            0xFF16A34A
                        )
                    )
                    Text(
                        text = item.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = item.rawSms,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Dismiss")
                }

                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Import")
                }
            }
        }
    }
}
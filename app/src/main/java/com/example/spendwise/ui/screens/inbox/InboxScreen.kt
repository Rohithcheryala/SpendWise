package com.example.spendwise.ui.screens.inbox


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.spendwise.core.extensions.toFormattedDateTime
import com.example.spendwise.data.mapper.SmsMessage
import com.example.spendwise.ui.components.TransactionDirection
import com.example.spendwise.ui.components.TransactionListItem
import com.example.spendwise.viewmodel.InboxViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    modifier: Modifier = Modifier,
    viewModel: InboxViewModel = hiltViewModel()
) {
    val state = viewModel.uiState

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Inbox")

                        if (!state.isLoading) {
                            Text(
                                text = "${state.messages.size} pending",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO Filter */ }) {
                        Icon(
                            Icons.Outlined.FilterList,
                            contentDescription = "Filter"
                        )
                    }

                    IconButton(onClick = { /* TODO Refresh */ }) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Refresh"
                        )
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

            state.error != null -> {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.error)
                }
            }

            state.messages.isEmpty() -> {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No transactions to review.")
                }
            }

            else -> {

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    items(state.messages) { message ->

                        TransactionListItem(
                            title = message.address.orEmpty(),
                            account = "AC${message.address.orEmpty()}",
                            time = message.date.toFormattedDateTime(),
                            amount = message.body.orEmpty().length.toString(),
                            direction = TransactionDirection.EXPENSE,
                            tags = emptyList()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InboxScreenLegacy(
    modifier: Modifier = Modifier,
    viewModel: InboxViewModel = hiltViewModel()
) {
    val state = viewModel.uiState

    when {
        state.isLoading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        state.error != null -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(state.error)
            }
        }

        state.messages.isEmpty() -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No new SMS found.")
            }
        }

        else -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = state.messages) { message: SmsMessage ->
//                    InboxMessageItem(
//                        sms = message,
//                        onClick = null
//                    )
                    TransactionListItem(
                        title = message.address!!,
                        account = "AC" + message.address!!,
                        time = message.date.toFormattedDateTime(),
                        amount = message.body!!.length.toString(),
                        direction = TransactionDirection.EXPENSE,
                        tags = arrayListOf("tag", "check"),
//                        onClick = () -> {}
                    )
                }
            }
        }
    }
}

@Composable
fun InboxMessageItem(
    sms: SmsMessage,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = MaterialTheme.shapes.large
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = sms.address!!,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = sms.body!!,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = sms.date.toFormattedDateTime(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
private fun PermissionRequiredScreen(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("SMS permission is required.")
    }
}
package com.example.spendwise.ui.screens.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.spendwise.ui.components.FriendAvatar
import com.example.spendwise.ui.components.FriendCard
import com.example.spendwise.viewmodel.FriendsViewModel

data class FriendUi(
    val id: Long,
    val name: String,
    val amountGiven: Double,
    val amountReceived: Double
)

@Composable
fun FriendsScreen(
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: FriendsViewModel = hiltViewModel()
) {
    val state = viewModel.uiState

    FriendsContent(
        state = state,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsContent(
    state: FriendsViewModel.UiState,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var friendList by remember(state.friends) {
        mutableStateOf(
            if (state.friends.isNotEmpty()) state.friends else listOf(
                FriendUi(1, "Rahul Sharma", 1500.0, 0.0),
                FriendUi(2, "Ananya Verma", 0.0, 450.0),
                FriendUi(3, "Vikram Malhotra", 2400.0, 1200.0),
                FriendUi(4, "Sneha Reddy", 0.0, 0.0)
            )
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedFriendForDetail by remember { mutableStateOf<FriendUi?>(null) }

    val filteredFriends = friendList.filter {
        it.name.contains(searchQuery, ignoreCase = true)
    }

    val totalGiven = friendList.sumOf { it.amountGiven }
    val totalReceived = friendList.sumOf { it.amountReceived }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Friends & Split", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = "Add Friend")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Outlined.PersonAdd, contentDescription = "Add Friend")
            }
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search friends...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    singleLine = true
                )
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "You lent",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "₹${"%,.0f".format(totalGiven)}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        VerticalDivider()

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "You borrowed",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "₹${"%,.0f".format(totalReceived)}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            items(filteredFriends, key = { it.id }) { friend ->
                FriendCard(
                    name = friend.name,
                    amountGiven = friend.amountGiven,
                    amountReceived = friend.amountReceived,
                    onClick = {
                        selectedFriendForDetail = friend
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        AddFriendDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { newFriend ->
                friendList = friendList + newFriend
                showAddDialog = false
            }
        )
    }

    if (selectedFriendForDetail != null) {
        FriendDetailBottomSheet(
            friend = selectedFriendForDetail!!,
            onDismiss = { selectedFriendForDetail = null },
            onSettleUp = { settledFriendId ->
                friendList = friendList.map { f ->
                    if (f.id == settledFriendId) f.copy(
                        amountGiven = 0.0,
                        amountReceived = 0.0
                    ) else f
                }
                selectedFriendForDetail = null
            }
        )
    }
}

@Composable
fun AddFriendDialog(
    onDismiss: () -> Unit,
    onAdd: (FriendUi) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Friend") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Friend's Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(
                        FriendUi(
                            id = System.currentTimeMillis(),
                            name = name.ifBlank { "New Friend" },
                            amountGiven = 0.0,
                            amountReceived = 0.0
                        )
                    )
                },
                enabled = name.isNotBlank()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendDetailBottomSheet(
    friend: FriendUi,
    onDismiss: () -> Unit,
    onSettleUp: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val netBalance = friend.amountGiven - friend.amountReceived

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FriendAvatar(name = friend.name, modifier = Modifier.size(64.dp))

            Text(
                text = friend.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = when {
                    netBalance > 0 -> "${friend.name} owes you ₹${"%,.0f".format(netBalance)}"
                    netBalance < 0 -> "You owe ${friend.name} ₹${"%,.0f".format(-netBalance)}"
                    else -> "All settled up!"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    netBalance > 0 -> MaterialTheme.colorScheme.primary
                    netBalance < 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onSettleUp(friend.id) },
                    modifier = Modifier.weight(1f),
                    enabled = netBalance != 0.0
                ) {
                    Text("Settle Up")
                }
            }
        }
    }
}
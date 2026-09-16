package com.example.spendwise.ui.screens.friends

import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.spendwise.ui.theme.Dimens
import com.example.spendwise.ui.components.EmptyState
import com.example.spendwise.ui.components.FriendAvatar
import com.example.spendwise.ui.components.FriendCard
import com.example.spendwise.ui.components.MoneySemantic
import com.example.spendwise.ui.components.MoneySize
import com.example.spendwise.ui.components.MoneyText
import com.example.spendwise.ui.components.formatRupees
import com.example.spendwise.viewmodel.FriendsViewModel
import kotlin.math.roundToLong
import com.example.spendwise.ui.components.SpendwiseCard

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
        onAddFriend = viewModel::addFriend,
        onLoadContacts = viewModel::loadContacts,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsContent(
    state: FriendsViewModel.UiState,
    onNavigateBack: (() -> Unit)? = null,
    onAddFriend: (String, String?) -> Unit = { _, _ -> },
    onLoadContacts: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val friendList = state.friends

    var searchQuery by remember { mutableStateOf("") }
    var showAddSheet by remember { mutableStateOf(false) }
    var selectedFriendForDetail by remember { mutableStateOf<FriendUi?>(null) }

    val filteredFriends = friendList.filter {
        it.name.contains(searchQuery, ignoreCase = true)
    }

    val totalGiven = friendList.sumOf { it.amountGiven }
    val totalReceived = friendList.sumOf { it.amountReceived }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                title = { Text("Friends & Split", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showAddSheet = true }) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = "Add Friend")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Rounded.PersonAdd, contentDescription = "Add Friend")
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
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    singleLine = true
                )
            }

            item {
    SpendwiseCard(modifier = Modifier.fillMaxWidth()) {
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
                            Spacer(Modifier.height(4.dp))
                            MoneyText(
                                amountPaise = (totalGiven * 100).roundToLong(),
                                semantic = MoneySemantic.INCOME,
                                size = MoneySize.TITLE
                            )
                        }

                        VerticalDivider()

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "You borrowed",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            MoneyText(
                                amountPaise = (totalReceived * 100).roundToLong(),
                                semantic = MoneySemantic.EXPENSE,
                                size = MoneySize.TITLE
                            )
                        }
                    }
                }
            }

            if (state.error != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = state.error ?: "",
                            modifier = Modifier.padding(Dimens.cardPadding),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (filteredFriends.isEmpty()) {
                item {
                    EmptyState(
                        icon = if (searchQuery.isBlank()) {
                            Icons.Rounded.PersonAdd
                        } else {
                            Icons.Rounded.PersonSearch
                        },
                        title = if (searchQuery.isBlank()) {
                            "No friends yet"
                        } else {
                            "No matches"
                        },
                        message = if (searchQuery.isBlank()) {
                            "Add people you split with to track who owes whom. " +
                                "Pick them straight from your contacts."
                        } else {
                            "No friend matches \"$searchQuery\"."
                        },
                        actionLabel = if (searchQuery.isBlank()) "Add friend" else null,
                        onAction = if (searchQuery.isBlank()) {
                            { showAddSheet = true }
                        } else {
                            null
                        }
                    )
                }
            } else {
                items(filteredFriends, key = { it.id }) { friend ->
                    FriendCard(
                        modifier = Modifier.animateItem(),
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
    }

    if (showAddSheet) {
        AddFriendSheet(
            contacts = state.contacts,
            contactsLoading = state.contactsLoading,
            onLoadContacts = onLoadContacts,
            onAdd = { name, phone ->
                onAddFriend(name, phone)
                showAddSheet = false
            },
            onDismiss = { showAddSheet = false }
        )
    }

    if (selectedFriendForDetail != null) {
        FriendDetailBottomSheet(
            friend = selectedFriendForDetail!!,
            onDismiss = { selectedFriendForDetail = null },
            onSettleUp = { _ ->
                // Real settlement is a loan-repayment entry created from the
                // transaction screen; the sheet closes without faking a zero-out.
                selectedFriendForDetail = null
            }
        )
    }
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
                    netBalance > 0 -> "${friend.name} owes you ${formatRupees(netBalance)}"
                    netBalance < 0 -> "You owe ${friend.name} ${formatRupees(-netBalance)}"
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
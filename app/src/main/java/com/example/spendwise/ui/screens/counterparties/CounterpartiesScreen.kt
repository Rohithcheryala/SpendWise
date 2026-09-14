package com.example.spendwise.ui.screens.counterparties

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.spendwise.ledger.service.CounterpartyService
import com.example.spendwise.ui.components.FriendAvatar
import com.example.spendwise.viewmodel.CounterpartyUi
import com.example.spendwise.viewmodel.CounterpartiesViewModel

private enum class PartyFilter(val label: String) {
    ALL("All"),
    PERSONS("People"),
    MERCHANTS("Merchants"),
}

/**
 * Counterparties — every party the ledger knows: person-type counterparties
 * (friends, also on the Friends page) and merchants auto-created from
 * SMS/UPI ingestion. Shows known aliases and net outstanding for people.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterpartiesScreen(
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: CounterpartiesViewModel = hiltViewModel()
) {
    val state = viewModel.uiState

    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(PartyFilter.ALL) }
    var selectedForDetail by remember { mutableStateOf<CounterpartyUi?>(null) }

    val filtered = state.counterparties.filter { cp ->
        val matchesFilter = when (filter) {
            PartyFilter.ALL -> true
            PartyFilter.PERSONS -> cp.partyType == CounterpartyService.PARTY_PERSON
            PartyFilter.MERCHANTS -> cp.partyType == CounterpartyService.PARTY_MERCHANT
        }
        val matchesQuery = searchQuery.isBlank() ||
            cp.name.contains(searchQuery, ignoreCase = true) ||
            cp.aliases.any { it.contains(searchQuery, ignoreCase = true) }
        matchesFilter && matchesQuery
    }

    val personCount = state.counterparties.count {
        it.partyType == CounterpartyService.PARTY_PERSON
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Counterparties", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
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
                    placeholder = { Text("Search name or alias...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    singleLine = true
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PartyFilter.entries) { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = {
                                Text(
                                    when (f) {
                                        PartyFilter.ALL -> "All (${state.counterparties.size})"
                                        PartyFilter.PERSONS -> "People ($personCount)"
                                        PartyFilter.MERCHANTS ->
                                            "Merchants (${state.counterparties.size - personCount})"
                                    }
                                )
                            }
                        )
                    }
                }
            }

            if (state.error != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = state.error ?: "",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Storefront,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (state.counterparties.isEmpty())
                                "No counterparties yet.\nThey appear automatically as you " +
                                    "transact and ingest SMS/UPI payments."
                            else "No counterparties match your search.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(filtered, key = { it.id }) { party ->
                    CounterpartyCard(
                        party = party,
                        onClick = { selectedForDetail = party }
                    )
                }
            }
        }
    }

    if (selectedForDetail != null) {
        CounterpartyDetailSheet(
            party = selectedForDetail!!,
            onDismiss = { selectedForDetail = null }
        )
    }
}

@Composable
private fun CounterpartyCard(
    party: CounterpartyUi,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val isPerson = party.partyType == CounterpartyService.PARTY_PERSON

    Card(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FriendAvatar(party.name, modifier = Modifier.size(44.dp))

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = party.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isPerson)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = if (isPerson) "PERSON" else "MERCHANT",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isPerson)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (party.aliases.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = party.aliases.first(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (party.aliases.size > 1) {
                            Text(
                                text = " +${party.aliases.size - 1}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            if (isPerson && party.netPaise != null && party.netPaise != 0L) {
                val net = party.netPaise / 100.0
                val (color, label) = if (net > 0)
                    MaterialTheme.colorScheme.primary to "owes you"
                else
                    MaterialTheme.colorScheme.error to "you owe"
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹${"%,.0f".format(kotlin.math.abs(net))}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = color
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CounterpartyDetailSheet(
    party: CounterpartyUi,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isPerson = party.partyType == CounterpartyService.PARTY_PERSON
    val net = party.netPaise?.let { it / 100.0 }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FriendAvatar(party.name, modifier = Modifier.size(64.dp))

            Text(
                text = party.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isPerson)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    text = if (isPerson) "Person" else "Merchant",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPerson)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isPerson && net != null) {
                Text(
                    text = when {
                        net > 0 -> "Owes you ₹${"%,.0f".format(net)}"
                        net < 0 -> "You owe ₹${"%,.0f".format(-net)}"
                        else -> "All settled up!"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        net > 0 -> MaterialTheme.colorScheme.primary
                        net < 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Known aliases (${party.aliases.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (party.aliases.isEmpty()) {
                    Text(
                        text = "No aliases recorded yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    party.aliases.forEach { alias ->
                        Text(
                            text = "• $alias",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

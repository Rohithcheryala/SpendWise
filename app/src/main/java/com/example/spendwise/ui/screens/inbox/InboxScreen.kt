package com.example.spendwise.ui.screens.inbox

import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.spendwise.ui.components.SpendwiseTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.spendwise.core.extensions.toAmountString
import com.example.spendwise.data.repository.ActiveContext
import com.example.spendwise.data.repository.InboxItem
import com.example.spendwise.ui.components.EmptyState
import com.example.spendwise.ui.components.MoneySemantic
import com.example.spendwise.ui.components.MoneySize
import com.example.spendwise.ui.components.MoneyText
import com.example.spendwise.ui.components.TransactionDirection
import com.example.spendwise.ui.components.TransactionDirectionIcon
import kotlinx.coroutines.delay
import com.example.spendwise.ui.theme.SpendwiseTheme
import com.example.spendwise.viewmodel.InboxViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.example.spendwise.ui.components.SpendwiseCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onNavigateBack: (() -> Unit)? = null,
    onEditItem: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InboxViewModel = hiltViewModel()
) {
    val state = viewModel.uiState
    val activeContext by viewModel.activeContext.collectAsStateWithLifecycle()
    var showContextDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // One-time POST_NOTIFICATIONS prompt, here where its value is obvious:
    // onboarding historically never asked for it, so installs that completed
    // onboarding have notifications permanently (and silently) disabled.
    var askedNotificationPermission by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* the notifier re-checks the grant at post time */ }
    LaunchedEffect(Unit) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED &&
            !askedNotificationPermission
        ) {
            askedNotificationPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val items = state.items
    val snackbarHostState = remember { SnackbarHostState() }

    // Reload the buffer whenever this screen resumes: transactions can be edited,
    // confirmed or voided in the transaction screen opened from here, and the
    // list must not show stale rows on return.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshBuffer()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeActionMessage()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeError()
        }
    }

    if (showContextDialog) {
        ContextDialog(
            onDismiss = { showContextDialog = false },
            onConfirm = { tag, expiresAt ->
                viewModel.startContext(tag, expiresAt)
                showContextDialog = false
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SpendwiseTopBar(
                title = "Inbox",
                subtitle = "${items.size} pending review",
                onBack = onNavigateBack,
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
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

            items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Rounded.Inbox,
                        title = "Inbox is clear",
                        message = "Every bank SMS we detected has been reviewed and " +
                            "imported. New ones land here first so nothing is posted " +
                            "without your say-so."
                    )
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
                    item(key = "context-banner") {
                        ContextBanner(
                            activeContext = activeContext,
                            onSetContext = { showContextDialog = true },
                            onEndContext = { viewModel.endContext() }
                        )
                    }

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
                                onClick = { viewModel.importAll() },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    "Approve All (${items.size})",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    items(items, key = { it.transactionId }) { item ->
                        InboxPendingCard(
                            modifier = Modifier.animateItem(),
                            item = item,
                            onOpen = { onEditItem(item.transactionId) },
                            onApprove = { viewModel.import(item.transactionId) },
                            onDismiss = { viewModel.dismiss(item.transactionId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InboxPendingCard(
    item: InboxItem,
    onOpen: () -> Unit,
    onApprove: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SpendwiseTheme.colors

    val direction = if (item.isDebit) TransactionDirection.EXPENSE else TransactionDirection.INCOME

    // Approving is the app's most-used gesture, so it gets a small payoff: the
    // tick pops, then the row is imported and the list animates it away.
    var approving by remember { mutableStateOf(false) }
    val tickScale by animateFloatAsState(
        targetValue = if (approving) 1.45f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "inbox_approve"
    )
    LaunchedEffect(approving) {
        if (approving) {
            delay(APPROVE_ANIMATION_MILLIS)
            onApprove()
        }
    }

    SpendwiseCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onOpen
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransactionDirectionIcon(direction)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Line 1: receiver info. Line 2: date/time • category • tags.
                Text(
                    text = item.sender,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val subtitle = listOfNotNull(
                    compactDate(item.date),
                    item.category?.takeIf { it.isNotBlank() },
                    item.tags.takeIf { it.isNotEmpty() }?.joinToString(", "),
                ).joinToString(" • ")
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            MoneyText(
                text = (if (item.isDebit) "\u2212" else "+") + item.amountPaise.toAmountString(),
                size = MoneySize.BODY,
                semantic = if (item.isDebit) MoneySemantic.EXPENSE else MoneySemantic.INCOME
            )

            // Compact actions: import is the single primary action; dismiss is
            // a small secondary icon. Neither deserves a full-width button,
            // and the raw SMS belongs in the transaction detail screen only.
            IconButton(
                onClick = { if (!approving) approving = true },
                enabled = !approving,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Import",
                    tint = colors.income,
                    modifier = Modifier
                        .size(18.dp)
                        .scale(tickScale)
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private val inboxTimeFormat = DateTimeFormatter.ofPattern("h:mm a")
private val inboxDayFormat = DateTimeFormatter.ofPattern("d MMM")

private const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1000

/**
 * How long the approve tick pops before the row is actually imported: long
 * enough to register as feedback, short enough that a fast reviewer never
 * feels the list lag behind their taps.
 */
private const val APPROVE_ANIMATION_MILLIS = 220L

/** Expiry presets for the tagging context. */
private data class ContextDuration(val label: String, val expiresAt: () -> Long)

private val contextDurations = listOf(
    ContextDuration("Rest of today") {
        LocalDate.now().plusDays(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    },
    ContextDuration("24 hours") { System.currentTimeMillis() + ONE_DAY_MILLIS },
    ContextDuration("3 days") { System.currentTimeMillis() + 3 * ONE_DAY_MILLIS },
    ContextDuration("7 days") { System.currentTimeMillis() + 7 * ONE_DAY_MILLIS },
)

/**
 * The trip-mode banner. When a context is active every import from this
 * screen (✓ or Approve All) and every editor save is auto-tagged with it,
 * so a burst of trip spends doesn't need one-by-one tagging.
 */
@Composable
private fun ContextBanner(
    activeContext: ActiveContext?,
    onSetContext: () -> Unit,
    onEndContext: () -> Unit
) {
    if (activeContext != null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Sell,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "\u201C${activeContext.tag}\u201D",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Auto-tagging until ${formatExpiry(activeContext.expiresAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                TextButton(onClick = onEndContext) {
                    Text("End")
                }
            }
        }
    } else {
        SpendwiseCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Sell,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "On a trip? Auto-tag new saves with a context.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onSetContext) {
                    Text("Set")
                }
            }
        }
    }
}

private val contextExpiryFormat = DateTimeFormatter.ofPattern("d MMM, h:mm a")

private fun formatExpiry(epochMillis: Long): String =
    contextExpiryFormat.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContextDialog(
    onDismiss: () -> Unit,
    onConfirm: (tag: String, expiresAt: Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedIdx by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set context") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "While active, every transaction saved from the inbox or " +
                        "the editor is tagged with this context — no per-SMS tagging " +
                        "during a trip.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("e.g. Goa trip") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    contextDurations.forEachIndexed { idx, duration ->
                        FilterChip(
                            selected = selectedIdx == idx,
                            onClick = {
                                selectedIdx = if (selectedIdx == idx) null else idx
                            },
                            label = { Text(duration.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && selectedIdx != null,
                onClick = {
                    val expiresAt = selectedIdx?.let { contextDurations[it].expiresAt() }
                        ?: return@TextButton
                    onConfirm(name.trim(), expiresAt)
                }
            ) {
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/** Dense list label: the time for today, "Yesterday", otherwise a short date. */
private fun compactDate(epochMillis: Long): String {
    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    val date = dateTime.toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> inboxTimeFormat.format(dateTime)
        today.minusDays(1) -> "Yesterday"
        else -> inboxDayFormat.format(date)
    }
}
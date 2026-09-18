package com.example.spendwise.ui.screens.accounts

import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.spendwise.ui.components.SpendwiseTopBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.spendwise.ui.components.MoneySemantic
import com.example.spendwise.ui.components.MoneySize
import com.example.spendwise.ui.components.MoneyText
import com.example.spendwise.ui.components.SpendwiseCard
import com.example.spendwise.ui.theme.Dimens
import com.example.spendwise.ui.theme.SpendwiseTheme
import com.example.spendwise.viewmodel.AccountsViewModel
import kotlin.math.roundToLong
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.core.content.ContextCompat

data class AccountUiModel(
    val id: Long,
    val name: String,
    val accountNumber: String,
    val balance: Double,
    val type: AccountType,
    val bankName: String
)

enum class AccountType(val label: String, val icon: ImageVector, val swatchIndex: Int) {
    SAVINGS("Savings", Icons.Default.Savings, 4),
    CHECKING("Checking", Icons.Default.AccountBalance, 1),
    CREDIT_CARD("Credit Card", Icons.Default.CreditCard, 5),
    CASH("Cash", Icons.Default.MonetizationOn, 6)
}

/**
 * Theme-aware categorical colour for an account type. The old per-constant
 * hardcoded colours were identical in light and dark and included a third
 * green (`#16A34A`) that meant nothing (see UI_UX_AUDIT.md §5.10).
 */
@Composable
fun AccountType.accentColor(): Color =
    SpendwiseTheme.categorical.swatches[
        swatchIndex % SpendwiseTheme.categorical.swatches.size
    ]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    modifier: Modifier = Modifier,
    viewModel: AccountsViewModel = hiltViewModel(),
    onNavigateBack: (() -> Unit)? = null,
    onAccountClick: ((Long) -> Unit)? = null
) {
            val uiState = viewModel.uiState
    val showAddBottomSheet = remember { mutableStateOf(false) }
    val context = LocalContext.current
    var pendingDetect by remember { mutableStateOf(false) }

    // READ_SMS is a runtime permission — the scan is meaningless without it,
    // so gate the detection flow the same way onboarding does.
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingDetect) viewModel.detectFromSms()
        pendingDetect = false
    }

    fun startDetection() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.detectFromSms()
        } else {
            pendingDetect = true
            smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    val accounts = uiState.accounts
    val totalAssets = accounts.filter { it.balance > 0 }.sumOf { it.balance }
    val totalLiabilities = accounts.filter { it.balance < 0 }.sumOf { -it.balance }
    val netWorth = totalAssets - totalLiabilities

    // Show any error from data operations (e.g. balance fetch failures)
    if (uiState.error != null) {
        LaunchedEffect(uiState.error) {
            Toast.makeText(context, uiState.error, Toast.LENGTH_LONG).show()
            viewModel.consumeError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Create lives on the FAB (the convention across Transactions,
            // Friends and Categories) — a second "+" up here was pure noise.
            SpendwiseTopBar(
                title = "Accounts & Cards",
                onBack = onNavigateBack,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddBottomSheet.value = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Account")
            }
        }
    ) { padding ->

        if (uiState.isLoading && accounts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    NetWorthSummaryCard(
                        netWorth = netWorth,
                        totalAssets = totalAssets,
                        totalLiabilities = totalLiabilities
                    )
                }

                item {
                    Text(
                        text = "Your Accounts (${accounts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                items(accounts, key = { it.id }) { account ->
                    AccountCardItem(
                        account = account,
                        onClick = { onAccountClick?.invoke(account.id) }
                    )
                }

                if (accounts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No accounts added",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(12.dp))
                                // The fastest possible on-ramp: the bank SMS
                                // the user already has name their accounts.
                                OutlinedButton(onClick = { startDetection() }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Sms,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Detect from SMS")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddBottomSheet.value) {
        AddAccountBottomSheet(
            onDismiss = { showAddBottomSheet.value = false },
            onAdd = { newAcc ->
                viewModel.addAccount(newAcc)
                showAddBottomSheet.value = false
            },
            onDetectFromSms = {
                showAddBottomSheet.value = false
                startDetection()
            }
        )
    }

    // Detection progress / results / import — one sheet for the whole flow.
    val detection = viewModel.detectionState
    if (detection.isDetecting || detection.detected.isNotEmpty() ||
        detection.scanSummary != null || detection.error != null
    ) {
        DetectedAccountsSheet(
            state = detection,
            onToggle = viewModel::toggleDetected,
            onImport = viewModel::importDetectedAccounts,
            onDismiss = viewModel::dismissDetection,
            onInitialBalanceChange = viewModel::updateInitialBalance,
        )
    }
}

@Composable
fun NetWorthSummaryCard(
    netWorth: Double,
    totalAssets: Double,
    totalLiabilities: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding)
        ) {
            Text(
                text = "Total Net Worth",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            MoneyText(
                amountPaise = (netWorth * 100).roundToLong(),
                size = MoneySize.BALANCE,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Assets",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    MoneyText(
                        amountPaise = (totalAssets * 100).roundToLong(),
                        semantic = MoneySemantic.INCOME,
                        size = MoneySize.TITLE
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Liabilities",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    MoneyText(
                        amountPaise = (totalLiabilities * 100).roundToLong(),
                        semantic = MoneySemantic.EXPENSE,
                        size = MoneySize.TITLE
                    )
                }
            }
        }
    }
}

@Composable
fun AccountCardItem(
    account: AccountUiModel,
    onClick: () -> Unit
) {
    SpendwiseCard(
        modifier = Modifier,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val accent = account.type.accentColor()
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = accent.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = account.type.icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = account.accountNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(
                                text = account.type.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                MoneyText(
                    amountPaise = (account.balance * 100).roundToLong(),
                    size = MoneySize.TITLE,
                    color = if (account.balance >= 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                Text(
                    text = account.bankName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountBottomSheet(
    onDismiss: () -> Unit,
    onAdd: (AccountUiModel) -> Unit,
    onDetectFromSms: () -> Unit
) {
    // Swipe-to-dismiss is disabled: one accidental swipe used to wipe every
    // field the user had typed. The sheet can only be closed via the Cancel
    // button or system back — both of which ask for confirmation first.
    //
    // Back handling: predictive back dismisses ModalBottomSheet regardless of
    // confirmValueChange (Material3 1.3 bug), which used to throw away the
    // whole form when the user only meant to fold the keyboard. The sheet's
    // own back handling is switched off and a BackHandler takes over: with
    // the keyboard up, back folds the keyboard; only a second back dismisses
    // (asking for confirmation when anything was typed).
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )
    val context = LocalContext.current
    val view = LocalView.current

    /** Ask the IME to hide; true when it actually initiated hiding. */
    fun hideKeyboard(): Boolean {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) {
                val focus = view.findFocus() ?: return false
                val imm = ctx.getSystemService(Activity.INPUT_METHOD_SERVICE)
                    as? InputMethodManager
                return imm?.hideSoftInputFromWindow(focus.windowToken, 0) == true
            }
            ctx = ctx.baseContext
        }
        return false
    }

    var name by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var initialBalance by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AccountType.SAVINGS) }

    val hasInput = name.isNotBlank() || bankName.isNotBlank() ||
        accountNumber.isNotBlank() || initialBalance.isNotBlank()
    var confirmDiscard by remember { mutableStateOf(false) }

    fun attemptDismiss() {
        if (hasInput) confirmDiscard = true else onDismiss()
    }

    BackHandler {
        if (view.findFocus() != null && hideKeyboard()) {
            // First back with the keyboard up: fold the keyboard only.
        } else {
            attemptDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { attemptDismiss() },
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Add New Account",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Most users shouldn't be typing bank details at all — their
            // inbox already knows them. Manual entry is the fallback, not
            // the primary path.
            OutlinedButton(
                onClick = onDetectFromSms,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Sms,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Detect from SMS instead")
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Account Name") },
                placeholder = { Text("e.g. HDFC Salary") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = bankName,
                onValueChange = { bankName = it },
                label = { Text("Bank / Institution") },
                placeholder = { Text("e.g. HDFC Bank") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = accountNumber,
                onValueChange = { accountNumber = it },
                label = { Text("Account Number / Masked ID") },
                placeholder = { Text("e.g. •••• 4921") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = initialBalance,
                onValueChange = { initialBalance = it },
                label = { Text("Current Balance (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text("Account Type", style = MaterialTheme.typography.labelMedium)
            // 2×2 icon-card grid: the old single-row segmented control
            // squeezed four labels ("Credit Card") into a quarter of a
            // 360dp screen and truncated to unreadable slivers.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountType.entries.chunked(2).forEach { rowTypes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowTypes.forEach { type ->
                            val selected = selectedType == type
                            Surface(
                                onClick = { selectedType = type },
                                shape = MaterialTheme.shapes.small,
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp, vertical = 14.dp
                                    )
                                ) {
                                    Icon(
                                        imageVector = type.icon,
                                        contentDescription = null,
                                        tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = type.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val bal = initialBalance.toDoubleOrNull() ?: 0.0
                    onAdd(
                        AccountUiModel(
                            id = System.currentTimeMillis(),
                            name = name.ifBlank { "New Account" },
                            accountNumber = if (accountNumber.isNotBlank()) accountNumber else "•••• ${
                                name.takeLast(
                                    4
                                )
                            }",
                            balance = if (selectedType == AccountType.CREDIT_CARD) -Math.abs(bal) else bal,
                            type = selectedType,
                            bankName = bankName.ifBlank { "Bank" }
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank()
            ) {
                Text("Save Account")
            }

            TextButton(
                onClick = { attemptDismiss() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard account?") },
            text = { Text("The details you entered will be lost.") },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text("Keep editing")
                }
            }
        )
    }
}

/**
 * The detect-from-SMS flow in one sheet: scanning progress, the accounts
 * found (auto-selected, tap to untick), a scan summary, and the import
 * action. Mirrors the onboarding account-selection flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectedAccountsSheet(
    state: AccountsViewModel.DetectionState,
    onToggle: (String) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
    onInitialBalanceChange: (String, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // Full-height sheet: the detected-accounts list gets a bounded
        // viewport so it scrolls, and Import/Cancel stay pinned at the bottom
        // instead of being pushed off-screen by a long list.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Accounts found in SMS",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            when {
                state.isDetecting -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 24.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "Scanning your messages…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                state.detected.isEmpty() -> {
                    Text(
                        text = state.scanSummary
                            ?: state.error
                            ?: "No new accounts were found in your messages.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }

                else -> {
                    state.scanSummary?.let { summary ->
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // The scrollable middle: takes all leftover height and
                    // scrolls when the list exceeds it, leaving the buttons
                    // below always visible.
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                    state.detected.forEach { account ->
                        val selected = account.key in state.selectedKeys
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable { onToggle(account.key) }
                                .padding(vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = account.suggestedName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selected) FontWeight.SemiBold
                                    else FontWeight.Normal
                                )
                                Text(
                                    text = "${account.transactionCount} transaction" +
                                        if (account.transactionCount == 1) "" else "s",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (selected) Icons.Rounded.CheckCircle
                                else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = if (selected) "Selected" else "Not selected",
                                tint = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // What the bank shows today — the window's SMS
                        // transactions are counted on top of it when the
                        // opening balance is derived.
                        OutlinedTextField(
                            value = state.initialBalances[account.key] ?: "",
                            onValueChange = { onInitialBalanceChange(account.key, it) },
                            label = { Text("Current Balance (₹)") },
                            supportingText = { Text("Balance today — past SMS activity is included on top") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            enabled = selected,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        )
                    }
                    }

                    Button(
                        onClick = onImport,
                        enabled = state.selectedKeys.isNotEmpty() && !state.isImporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            val count = state.selectedKeys.size
                            Text(
                                text = "Import " +
                                    if (count == 1) "1 account" else "$count accounts"
                            )
                        }
                    }
                }
            }

            TextButton(
                onClick = onDismiss,
                enabled = !state.isDetecting && !state.isImporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.detected.isEmpty()) "Close" else "Cancel")
            }
        }
    }
}

package com.example.spendwise.ui.screens.settings

import androidx.compose.material.icons.Icons
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.spendwise.ui.components.SpendwiseTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.spendwise.data.repository.SettingsRepository
import com.example.spendwise.data.repository.ThemeMode
import com.example.spendwise.viewmodel.SettingsViewModel
import com.example.spendwise.ui.components.SpendwiseCard
import com.example.spendwise.ui.theme.Dimens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ── screen body ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onNavigateToUpdate: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings = viewModel.settings
    val context = LocalContext.current

    var showThemeDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    // SMS auto-detection needs READ_SMS + RECEIVE_SMS; asking for them is
    // part of flipping the toggle on.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            viewModel.setSmsAutoDetect(true)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
        topBar = {
            SpendwiseTopBar(
                title = "Settings",
                onBack = onNavigateBack
            )
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SpendwiseCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(Dimens.cardPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = settings.profileName.ifBlank { "SpendWise User" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Currency: ${settings.currency}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── preferences card ──

            item {
                SettingsSectionHeader("Preferences")
            }

            item {
                SpendwiseCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsSwitchItem(
                            icon = Icons.Rounded.Sms,
                            title = "SMS Auto-Detection",
                            subtitle = "Automatically parse bank transaction SMS",
                            checked = settings.smsAutoDetect,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val readSms = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.READ_SMS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    val receiveSms = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.RECEIVE_SMS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (readSms && receiveSms) {
                                        viewModel.setSmsAutoDetect(true)
                                    } else {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.READ_SMS,
                                                Manifest.permission.RECEIVE_SMS,
                                            )
                                        )
                                    }
                                } else {
                                    viewModel.setSmsAutoDetect(false)
                                }
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = Dimens.screenGutter))

                        SettingsSwitchItem(
                            icon = Icons.Rounded.Notifications,
                            title = "Notifications",
                            subtitle = "Alert for uncategorized or large transactions",
                            checked = settings.notificationsEnabled,
                            onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = Dimens.screenGutter))

                        SettingsSwitchItem(
                            icon = Icons.Rounded.Security,
                            title = "Strict mode",
                            subtitle = "Buffer transactions need a category AND a tag " +
                                "before they can be saved/confirmed",
                            checked = settings.strictMode,
                            onCheckedChange = { viewModel.setStrictMode(it) }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = Dimens.screenGutter))

                        SettingsClickItem(
                            icon = Icons.Rounded.CurrencyRupee,
                            title = "Default Currency",
                            value = settings.currency,
                            onClick = { showCurrencyDialog = true }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = Dimens.screenGutter))

                        SettingsClickItem(
                            icon = Icons.Rounded.ColorLens,
                            title = "Theme",
                            value = when (settings.themeMode) {
                                ThemeMode.SYSTEM -> "System Default"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            },
                            onClick = { showThemeDialog = true }
                        )
                    }
                }
            }

            // ── data card ──

            item {
                SettingsSectionHeader("Data & Privacy")
            }

            item {
                SpendwiseCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        // "Day one" of the ledger — picked during onboarding's
                        // SMS scan; initial balances are meaningful as of it.
                        SettingsInfoItem(
                            icon = Icons.Rounded.Event,
                            title = "Tracking start date",
                            subtitle = "Day one of your ledger — SMS before this " +
                                "date are ignored and balances were recorded here",
                            value = viewModel.trackingStartDate?.let { millis ->
                                DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
                                    .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
                            } ?: "Not set",
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = Dimens.screenGutter))

                        SettingsClickItem(
                            icon = Icons.Rounded.Storage,
                            title = "Re-seed Sample Data",
                            value = "Reset database",
                            onClick = { showResetDialog = true }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = Dimens.screenGutter))

                        SettingsClickItem(
                            icon = Icons.Rounded.Security,
                            title = "Privacy & Permissions",
                            subtitle = "Manage SMS & Notification permissions",
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_SMS,
                                        Manifest.permission.RECEIVE_SMS,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    )
                                )
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = Dimens.screenGutter))

                        SettingsClickItem(
                            icon = Icons.Rounded.DeleteForever,
                            title = "Clear Local Data",
                            subtitle = "Delete all stored transactions and categories",
                            onClick = { showResetDialog = true },
                            isDestructive = true
                        )
                    }
                }
            }

            item {
                if (viewModel.isResetting) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Resetting database…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                SettingsSectionHeader("About")
            }

            item {
                SpendwiseCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(Dimens.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Spendwise Android", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Version ${com.example.spendwise.BuildConfig.VERSION_NAME} " +
                                    "(code ${com.example.spendwise.BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = Dimens.screenGutter))

                    SettingsClickItem(
                        icon = Icons.Rounded.SystemUpdate,
                        title = "App update",
                        subtitle = "Check for and install the latest release",
                        onClick = onNavigateToUpdate
                    )
                }
            }
        }
    }

    if (showThemeDialog) {
        ThemeDialog(
            current = settings.themeMode,
            onSelect = { mode ->
                viewModel.setThemeMode(mode)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showCurrencyDialog) {
        CurrencyDialog(
            current = settings.currency,
            onSelect = { currency ->
                viewModel.setCurrency(currency)
                showCurrencyDialog = false
            },
            onDismiss = { showCurrencyDialog = false }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset database?") },
            text = {
                Text(
                    "All locally stored transactions, categories and settings " +
                        "for the ledger will be wiped and re-seeded. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        viewModel.resetDatabase()
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ── dialogs & row widgets ──

@Composable
private fun ThemeDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme") },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == current,
                            onClick = { onSelect(mode) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when (mode) {
                                ThemeMode.SYSTEM -> "System Default"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CurrencyDialog(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default Currency") },
        text = {
            Column {
                SettingsRepository.CURRENCIES.forEach { currency ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(currency) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currency == current,
                            onClick = { onSelect(currency) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(currency)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(Dimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Non-clickable settings row (no chevron) — for facts, not actions. */
@Composable
fun SettingsInfoItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsClickItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(Dimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

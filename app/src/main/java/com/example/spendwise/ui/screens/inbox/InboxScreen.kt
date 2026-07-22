package com.example.spendwise.ui.screens.inbox


import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.spendwise.core.extensions.toFormattedDateTime
import com.example.spendwise.core.parser_pw.bank.BankParserFactory
import com.example.spendwise.data.mapper.SmsMessage
import com.example.spendwise.viewmodel.InboxViewModel
import java.time.Instant
import java.time.ZoneId

@Composable
fun InboxScreen(
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
                    InboxMessageItem(
                        sms = message,
                        onClick = null
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
fun InboxScreenLegacy(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var messages by remember { mutableStateOf(emptyList<SmsMessage>()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

//    LaunchedEffect(hasPermission) {
//        if (hasPermission) {
//            messages = withContext(Dispatchers.IO) {
//                readAllSms(context)
//            }
//        }
//    }

    when {
        !hasPermission -> {
            PermissionRequiredScreen(modifier)
        }

        messages.isEmpty() -> {
            EmptyInboxScreen(modifier)
        }

        else -> {
            InboxList(
                modifier = modifier,
                messages = messages
            )
        }
    }
}

@Composable
private fun InboxList(
    messages: List<SmsMessage>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(
            count = messages.size,
        ) {
            val sms = messages[it]

            val parsed = remember(sms) {
                BankParserFactory
                    .getParser(sms.address.orEmpty())
                    ?.parse(
                        sms.body.orEmpty(),
                        sms.address!!,
                        sms.date
                    )
            }

            SmsItem(
                sms = sms,
                parsed = parsed
            )
        }
    }
}

@Composable
private fun SmsItem(
    sms: SmsMessage,
    parsed: Any?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                if (parsed != null)
                    Color.Green.copy(alpha = 0.08f)
                else
                    Color.Red.copy(alpha = 0.08f)
        )
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = sms.address.orEmpty(),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = sms.body.orEmpty()
            )

            Spacer(
                Modifier.height(
                    8.dp
                )
            )

            Text(
                text = Instant.ofEpochMilli(sms.date)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                    .toString(),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun EmptyInboxScreen(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("No messages found.")
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
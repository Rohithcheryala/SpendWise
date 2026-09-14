package com.example.spendwise.ui.screens.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.spendwise.navigation.Screen
import com.example.spendwise.viewmodel.UpdatePhase
import com.example.spendwise.viewmodel.UpdateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    onNavigateBack: () -> Unit,
    viewModel: UpdateViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Screen.Update.label) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            UpdateScreenBody(viewModel)
        }
    }
}

@Composable
private fun UpdateScreenBody(viewModel: UpdateViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Installed version", style = MaterialTheme.typography.labelMedium)
            Text(
                "v${viewModel.installedVersion} (code ${viewModel.installedCode})",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    Button(
        onClick = viewModel::checkForUpdate,
        enabled = viewModel.phase != UpdatePhase.CHECKING &&
            viewModel.phase != UpdatePhase.DOWNLOADING,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            if (viewModel.phase == UpdatePhase.CHECKING) "Checking..."
            else "Check for updates"
        )
    }

    if (viewModel.phase == UpdatePhase.CHECKING) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }
    }

    viewModel.error?.let { msg ->
        Text(
            msg,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 12.dp),
        )
    }

    if (viewModel.phase == UpdatePhase.NO_UPDATE) {
        Text(
            "You're on the latest version!",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
    }

    viewModel.latestVersion?.let { info ->
        if (viewModel.phase != UpdatePhase.NO_UPDATE) {
            LatestVersionCard(info)
        }
        UpdateActionSection(viewModel, info)
    }
}

// ── latest version card & action buttons ──

@Composable
private fun LatestVersionCard(info: com.example.spendwise.viewmodel.VersionInfo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Latest version", style = MaterialTheme.typography.labelMedium)
            Text(
                "v${info.versionName} (code ${info.versionCode})",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (info.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(info.notes, style = MaterialTheme.typography.bodyMedium)
            }
            if (info.publishedAt.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Published: ${info.publishedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UpdateActionSection(
    viewModel: UpdateViewModel,
    info: com.example.spendwise.viewmodel.VersionInfo
) {
    Spacer(Modifier.height(16.dp))

    when (viewModel.phase) {
        UpdatePhase.UPDATE_AVAILABLE -> {
            Button(
                onClick = viewModel::downloadAndInstall,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Install v${info.versionName}") }
        }
        UpdatePhase.DOWNLOADING -> {
            LinearProgressIndicator(
                progress = { viewModel.downloadProgress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Downloading... ${(viewModel.downloadProgress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
            )
        }
        UpdatePhase.READY -> {
            OutlinedButton(
                onClick = { viewModel.installApk() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open installer") }
        }
        else -> {}
    }
}

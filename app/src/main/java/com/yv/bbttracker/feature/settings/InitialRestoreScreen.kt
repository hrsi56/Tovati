package com.yv.bbttracker.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yv.bbttracker.R

@Composable
fun InitialRestoreScreen(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedBackupUri by remember { mutableStateOf<String?>(null) }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedBackupUri = uri?.toString()
    }

    val message = state.message
    val messageText = when (message) {
        SettingsMessage.BACKUP_FAILED -> stringResource(R.string.backup_failed)
        SettingsMessage.BACKUP_UNSUPPORTED -> stringResource(R.string.backup_unsupported)
        SettingsMessage.FILE_FAILED -> stringResource(R.string.error_file_access)
        else -> null
    }
    LaunchedEffect(message, messageText) {
        if (messageText != null) snackbarHostState.showSnackbar(messageText)
        if (message != null) viewModel.onEvent(SettingsEvent.ClearMessage)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    Icons.Outlined.UploadFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                )
                Text(
                    stringResource(R.string.initial_restore_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.initial_restore_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            stringResource(R.string.initial_restore_privacy),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = {
                        restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    },
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    if (state.isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Outlined.UploadFile, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.choose_backup))
                    }
                }
                TextButton(
                    onClick = viewModel::dismissInitialRestorePrompt,
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Text(stringResource(R.string.skip_restore))
                }
            }
        }
    }

    selectedBackupUri?.let { uri ->
        BackupPasswordDialog(
            requireConfirmation = false,
            onDismiss = { selectedBackupUri = null },
            onConfirm = { password ->
                selectedBackupUri = null
                viewModel.restoreBackup(uri, password)
            },
        )
    }
}

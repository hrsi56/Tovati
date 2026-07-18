package com.yv.bbttracker.feature.settings

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yv.bbttracker.BuildConfig
import com.yv.bbttracker.R
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.TrackingGoal
import java.time.LocalDate

private enum class PasswordDialogMode { CREATE, RESTORE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backupShareSubject = stringResource(R.string.backup_share_subject)
    val backupShareBody = stringResource(R.string.backup_share_body)
    val backupShareClipLabel = stringResource(R.string.backup_share_clip_label)
    val backupShareChooserTitle = stringResource(R.string.backup_share_chooser_title)
    val snackbarHostState = remember { SnackbarHostState() }
    var showTimePicker by remember { mutableStateOf(false) }
    var showCsvWarning by remember { mutableStateOf(false) }
    var passwordMode by remember { mutableStateOf<PasswordDialogMode?>(null) }
    var pendingPassword by remember { mutableStateOf("") }
    var restoreUri by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMedicalInfo by remember { mutableStateOf(false) }
    var showPrivacyInfo by remember { mutableStateOf(false) }
    var shareAfterBackupCreation by remember { mutableStateOf(false) }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.exportCsv(it.toString()) }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            viewModel.createBackup(
                uri = it.toString(),
                password = pendingPassword,
                shareAfterCreation = shareAfterBackupCreation,
            )
        }
        pendingPassword = ""
        shareAfterBackupCreation = false
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            restoreUri = uri.toString()
            viewModel.inspectBackup(uri.toString(), pendingPassword)
        } else pendingPassword = ""
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onEvent(SettingsEvent.ReminderChanged(true))
        else viewModel.onEvent(SettingsEvent.NotificationDenied)
    }

    val message = state.message
    val messageText = message?.let { stringResource(it.stringResource()) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.onEvent(SettingsEvent.ClearMessage)
        }
    }
    LaunchedEffect(state.pendingBackupShareUri) {
        val uriValue = state.pendingBackupShareUri ?: return@LaunchedEffect
        val uri = Uri.parse(uriValue)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, backupShareSubject)
            putExtra(Intent.EXTRA_TEXT, backupShareBody)
            clipData = ClipData.newUri(
                context.contentResolver,
                backupShareClipLabel,
                uri,
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val launched = runCatching {
            context.startActivity(
                Intent.createChooser(
                    sendIntent,
                    backupShareChooserTitle,
                ),
            )
        }.isSuccess
        viewModel.backupShareHandled(launched)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium) }
            item {
                SettingsCard(stringResource(R.string.settings_tracking_goal)) {
                    Text(
                        stringResource(R.string.settings_tracking_goal_helper),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TrackingGoalChip(
                            goal = TrackingGoal.CYCLE_AWARENESS,
                            label = R.string.goal_track,
                            selected = state.settings.trackingGoal,
                            viewModel = viewModel,
                        )
                        TrackingGoalChip(
                            goal = TrackingGoal.TRYING_TO_CONCEIVE,
                            label = R.string.goal_conceive,
                            selected = state.settings.trackingGoal,
                            viewModel = viewModel,
                        )
                    }
                }
            }
            item {
                SettingsCard(stringResource(R.string.settings_measurement)) {
                    Text(stringResource(R.string.measurement_site), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SiteChip(MeasurementSite.ORAL, R.string.site_oral, state.settings.defaultMeasurementSite, viewModel)
                        SiteChip(MeasurementSite.VAGINAL, R.string.site_vaginal, state.settings.defaultMeasurementSite, viewModel)
                        SiteChip(MeasurementSite.RECTAL, R.string.site_rectal, state.settings.defaultMeasurementSite, viewModel)
                    }
                }
            }
            item {
                SettingsCard(stringResource(R.string.settings_reminders)) {
                    SwitchSetting(
                        title = stringResource(R.string.enable_reminder),
                        supporting = stringResource(R.string.reminder_approximate),
                        checked = state.settings.reminderEnabled,
                    ) { enabled ->
                        if (!enabled) viewModel.onEvent(SettingsEvent.ReminderChanged(false))
                        else if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else viewModel.onEvent(SettingsEvent.ReminderChanged(true))
                    }
                    if (state.settings.reminderEnabled) {
                        OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                            Text(stringResource(R.string.reminder_time) + "  %02d:%02d".format(state.settings.reminderHour, state.settings.reminderMinute))
                        }
                    }
                }
            }
            item {
                SettingsCard(stringResource(R.string.settings_privacy)) {
                    SwitchSetting(
                        title = stringResource(R.string.biometric_lock),
                        supporting = stringResource(R.string.biometric_lock_body),
                        checked = state.settings.biometricLockEnabled,
                    ) { enabled ->
                        if (!enabled) viewModel.onEvent(SettingsEvent.BiometricChanged(false))
                        else {
                            val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                            if (BiometricManager.from(context).canAuthenticate(allowed) == BiometricManager.BIOMETRIC_SUCCESS) {
                                viewModel.onEvent(SettingsEvent.BiometricChanged(true))
                            } else viewModel.onEvent(SettingsEvent.BiometricUnavailable)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    SwitchSetting(
                        title = stringResource(R.string.block_screenshots),
                        supporting = stringResource(R.string.block_screenshots_body),
                        checked = state.settings.screenshotsBlocked,
                        onCheckedChange = { viewModel.onEvent(SettingsEvent.ScreenshotsChanged(it)) },
                    )
                }
            }
            item {
                SettingsCard(stringResource(R.string.settings_data)) {
                    DataAction(Icons.Outlined.FileDownload, R.string.export_csv, enabled = !state.isBusy) { showCsvWarning = true }
                    DataAction(Icons.Outlined.SaveAlt, R.string.create_backup, enabled = !state.isBusy) { passwordMode = PasswordDialogMode.CREATE }
                    DataAction(
                        Icons.Outlined.Email,
                        R.string.share_backup_email,
                        enabled = !state.isBusy && state.hasShareableBackup,
                    ) { viewModel.prepareBackupShare() }
                    Text(
                        stringResource(
                            if (state.hasShareableBackup) R.string.share_backup_ready
                            else R.string.share_backup_requires_backup,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    DataAction(Icons.Outlined.UploadFile, R.string.restore_backup, enabled = !state.isBusy) { passwordMode = PasswordDialogMode.RESTORE }
                    DataAction(Icons.Outlined.DeleteForever, R.string.delete_all_data, destructive = true, enabled = !state.isBusy) {
                        showDeleteDialog = true
                    }
                }
            }
            item {
                SettingsCard(stringResource(R.string.settings_about)) {
                    DataAction(Icons.Outlined.HealthAndSafety, R.string.medical_info_title) { showMedicalInfo = true }
                    DataAction(Icons.Outlined.Lock, R.string.privacy_label) { showPrivacyInfo = true }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.engine_version, state.engineVersion), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.offline_promise), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            item { Spacer(Modifier.height(84.dp)) }
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(state.settings.reminderHour, state.settings.reminderMinute, is24Hour = true)
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.reminder_time)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onEvent(SettingsEvent.ReminderTimeChanged(pickerState.hour, pickerState.minute))
                    showTimePicker = false
                }) { Text(stringResource(R.string.done)) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) { TimeInput(pickerState) }
    }
    if (showCsvWarning) {
        AlertDialog(
            onDismissRequest = { showCsvWarning = false },
            title = { Text(stringResource(R.string.export_csv)) },
            text = { Text(stringResource(R.string.csv_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    showCsvWarning = false
                    csvLauncher.launch("bbt-data-${LocalDate.now()}.csv")
                }) { Text(stringResource(R.string.export_csv)) }
            },
            dismissButton = { TextButton(onClick = { showCsvWarning = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    passwordMode?.let { mode ->
        BackupPasswordDialog(
            requireConfirmation = mode == PasswordDialogMode.CREATE,
            onDismiss = {
                passwordMode = null
                if (mode == PasswordDialogMode.CREATE) shareAfterBackupCreation = false
            },
            onConfirm = { password ->
                pendingPassword = password
                passwordMode = null
                if (mode == PasswordDialogMode.CREATE) backupLauncher.launch("bbt-backup-${LocalDate.now()}.bbt.json")
                else restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            },
        )
    }
    if (state.staleBackupShareUri != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearStaleBackupPrompt,
            title = { Text(stringResource(R.string.stale_backup_title)) },
            text = { Text(stringResource(R.string.stale_backup_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearStaleBackupPrompt()
                        shareAfterBackupCreation = true
                        passwordMode = PasswordDialogMode.CREATE
                    },
                ) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::continueWithExistingBackup) {
                    Text(stringResource(R.string.stale_backup_continue_existing))
                }
            },
        )
    }
    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = {
                restoreUri = null
                pendingPassword = ""
                viewModel.clearRestorePreview()
            },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.restore_confirm_body))
                    when {
                        state.isBusy -> CircularProgressIndicator()
                        state.restorePreview != null -> Text(
                            stringResource(
                                R.string.restore_preview_summary,
                                state.restorePreview!!.cycleCount,
                                state.restorePreview!!.measurementCount,
                                state.restorePreview!!.observationCount,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        else -> Text(stringResource(R.string.backup_failed), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = state.restorePreview != null && !state.isBusy,
                    onClick = {
                        viewModel.restoreBackup(uri, pendingPassword)
                        restoreUri = null
                        pendingPassword = ""
                        viewModel.clearRestorePreview()
                    },
                ) { Text(stringResource(R.string.restore_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    restoreUri = null
                    pendingPassword = ""
                    viewModel.clearRestorePreview()
                }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (showDeleteDialog) {
        DeleteAllDialog(
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteAllData()
            },
        )
    }
    if (showMedicalInfo) {
        AlertDialog(
            onDismissRequest = { showMedicalInfo = false },
            title = { Text(stringResource(R.string.medical_info_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.medical_info_body))
                    Text(stringResource(R.string.disclaimer_text), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.offline_promise), color = MaterialTheme.colorScheme.tertiary)
                }
            },
            confirmButton = { TextButton(onClick = { showMedicalInfo = false }) { Text(stringResource(R.string.close)) } },
        )
    }
    if (showPrivacyInfo) {
        AlertDialog(
            onDismissRequest = { showPrivacyInfo = false },
            title = { Text(stringResource(R.string.privacy_label)) },
            text = { Text(stringResource(R.string.privacy_info_body)) },
            confirmButton = {
                TextButton(onClick = { showPrivacyInfo = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SiteChip(site: MeasurementSite, label: Int, selected: MeasurementSite, viewModel: SettingsViewModel) {
    FilterChip(
        selected = site == selected,
        onClick = { viewModel.onEvent(SettingsEvent.SiteChanged(site)) },
        label = { Text(stringResource(label)) },
    )
}

@Composable
private fun TrackingGoalChip(
    goal: TrackingGoal,
    label: Int,
    selected: TrackingGoal,
    viewModel: SettingsViewModel,
) {
    FilterChip(
        selected = goal == selected,
        onClick = { viewModel.onEvent(SettingsEvent.TrackingGoalChanged(goal)) },
        label = { Text(stringResource(label)) },
    )
}

@Composable
private fun DataAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: Int,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(50.dp)) {
        Icon(icon, contentDescription = null, tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.padding(horizontal = 6.dp))
        Text(
            stringResource(label),
            modifier = Modifier.weight(1f),
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BackupPasswordDialog(
    requireConfirmation: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val mismatch = requireConfirmation && confirmation.isNotEmpty() && password != confirmation
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (requireConfirmation) R.string.create_backup else R.string.restore_backup)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(128) },
                    label = { Text(stringResource(R.string.backup_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                if (requireConfirmation) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it.take(128) },
                        label = { Text(stringResource(R.string.backup_password_confirm)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = mismatch,
                    )
                }
                Text(
                    stringResource(if (mismatch) R.string.backup_password_mismatch else R.string.backup_password_helper),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (mismatch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = password.length >= 8 && (!requireConfirmation || password == confirmation),
            ) { Text(stringResource(R.string.continue_label)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DeleteAllDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var confirmation by remember { mutableStateOf("") }
    val expected = stringResource(R.string.delete_confirmation_word)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_all_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.delete_all_body))
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it.take(12) },
                    label = { Text(stringResource(R.string.delete_confirmation_prompt)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = confirmation == expected) {
                Text(stringResource(R.string.delete_all_action))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun SettingsMessage.stringResource(): Int = when (this) {
    SettingsMessage.CSV_EXPORTED -> R.string.csv_exported
    SettingsMessage.BACKUP_CREATED -> R.string.backup_created
    SettingsMessage.BACKUP_RESTORED -> R.string.backup_restored
    SettingsMessage.BACKUP_FAILED -> R.string.backup_failed
    SettingsMessage.BACKUP_UNSUPPORTED -> R.string.backup_unsupported
    SettingsMessage.BACKUP_SHARE_UNAVAILABLE -> R.string.backup_share_unavailable
    SettingsMessage.FILE_FAILED -> R.string.error_file_access
    SettingsMessage.NOTIFICATION_DENIED -> R.string.notification_permission_denied
    SettingsMessage.BIOMETRIC_UNAVAILABLE -> R.string.biometric_unavailable
    SettingsMessage.DATA_DELETED -> R.string.all_data_deleted
}

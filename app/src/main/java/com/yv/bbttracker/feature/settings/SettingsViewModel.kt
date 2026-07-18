package com.yv.bbttracker.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yv.bbttracker.data.backup.BackupManager
import com.yv.bbttracker.data.backup.DocumentGateway
import com.yv.bbttracker.data.backup.RestoreSummary
import com.yv.bbttracker.data.backup.UnsupportedBackupVersionException
import com.yv.bbttracker.domain.engine.ENGINE_VERSION
import com.yv.bbttracker.domain.model.AppSettings
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.TrackingGoal
import com.yv.bbttracker.domain.repository.SettingsRepository
import com.yv.bbttracker.notification.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId

enum class SettingsMessage {
    CSV_EXPORTED,
    BACKUP_CREATED,
    BACKUP_RESTORED,
    BACKUP_FAILED,
    BACKUP_UNSUPPORTED,
    BACKUP_SHARE_UNAVAILABLE,
    FILE_FAILED,
    NOTIFICATION_DENIED,
    BIOMETRIC_UNAVAILABLE,
    DATA_DELETED,
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isBusy: Boolean = false,
    val message: SettingsMessage? = null,
    val restorePreview: RestoreSummary? = null,
    val hasShareableBackup: Boolean = false,
    val pendingBackupShareUri: String? = null,
    val staleBackupShareUri: String? = null,
    val engineVersion: String = ENGINE_VERSION,
)

sealed interface SettingsEvent {
    data class TrackingGoalChanged(val goal: TrackingGoal) : SettingsEvent
    data class SiteChanged(val site: MeasurementSite) : SettingsEvent
    data class ReminderChanged(val enabled: Boolean) : SettingsEvent
    data class ReminderTimeChanged(val hour: Int, val minute: Int) : SettingsEvent
    data class BiometricChanged(val enabled: Boolean) : SettingsEvent
    data class ScreenshotsChanged(val blocked: Boolean) : SettingsEvent
    data object NotificationDenied : SettingsEvent
    data object BiometricUnavailable : SettingsEvent
    data object ClearMessage : SettingsEvent
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    private val backupManager: BackupManager,
    private val documentGateway: DocumentGateway,
) : ViewModel() {
    private val operation = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = combine(settingsRepository.settings, operation) { settings, local ->
        local.copy(settings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch {
            operation.update {
                it.copy(hasShareableBackup = documentGateway.hasShareableBackup())
            }
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.TrackingGoalChanged -> updateSettings { it.copy(trackingGoal = event.goal) }
            is SettingsEvent.SiteChanged -> updateSettings { it.copy(defaultMeasurementSite = event.site) }
            is SettingsEvent.ReminderChanged -> {
                updateSettings { it.copy(reminderEnabled = event.enabled) }
                val settings = state.value.settings
                if (event.enabled) reminderScheduler.schedule(settings.reminderHour, settings.reminderMinute)
                else reminderScheduler.cancel()
            }
            is SettingsEvent.ReminderTimeChanged -> {
                updateSettings { it.copy(reminderHour = event.hour, reminderMinute = event.minute) }
                if (state.value.settings.reminderEnabled) reminderScheduler.schedule(event.hour, event.minute)
            }
            is SettingsEvent.BiometricChanged -> updateSettings { it.copy(biometricLockEnabled = event.enabled) }
            is SettingsEvent.ScreenshotsChanged -> updateSettings { it.copy(screenshotsBlocked = event.blocked) }
            SettingsEvent.NotificationDenied -> operation.update { it.copy(message = SettingsMessage.NOTIFICATION_DENIED) }
            SettingsEvent.BiometricUnavailable -> operation.update { it.copy(message = SettingsMessage.BIOMETRIC_UNAVAILABLE) }
            SettingsEvent.ClearMessage -> operation.update { it.copy(message = null) }
        }
    }

    fun exportCsv(uri: String) = runOperation(SettingsMessage.CSV_EXPORTED, SettingsMessage.FILE_FAILED) {
        documentGateway.write(uri, backupManager.exportCsv())
    }

    fun createBackup(
        uri: String,
        password: String,
        shareAfterCreation: Boolean = false,
    ) = runOperation(SettingsMessage.BACKUP_CREATED, SettingsMessage.BACKUP_FAILED) {
        val passwordBuffer = password.toCharArray()
        try {
            val envelope = backupManager.createEncryptedBackup(passwordBuffer)
            documentGateway.write(uri, envelope.toByteArray(Charsets.UTF_8))
            documentGateway.storeLatestEncryptedBackup(envelope)
            backupManager.markBackupSuccessful()
            val shareUri = if (shareAfterCreation) documentGateway.shareableBackup().uri else null
            operation.update {
                it.copy(
                    hasShareableBackup = true,
                    pendingBackupShareUri = shareUri,
                    staleBackupShareUri = null,
                )
            }
        } finally {
            passwordBuffer.fill('\u0000')
        }
    }

    fun restoreBackup(uri: String, password: String) = runOperation(
        SettingsMessage.BACKUP_RESTORED,
        SettingsMessage.BACKUP_FAILED,
        failureMessage = ::backupFailureMessage,
    ) {
        val passwordBuffer = password.toCharArray()
        try {
            backupManager.restoreEncryptedBackup(documentGateway.readText(uri), passwordBuffer)
        } finally {
            passwordBuffer.fill('\u0000')
        }
        val restored = settingsRepository.getSettings()
        if (restored.reminderEnabled) reminderScheduler.schedule(restored.reminderHour, restored.reminderMinute)
        else reminderScheduler.cancel()
    }

    fun inspectBackup(uri: String, password: String) {
        if (operation.value.isBusy) return
        viewModelScope.launch {
            operation.update { it.copy(isBusy = true, message = null, restorePreview = null) }
            val passwordBuffer = password.toCharArray()
            val result = try {
                runCatching {
                    backupManager.inspectEncryptedBackup(documentGateway.readText(uri), passwordBuffer)
                }
            } finally {
                passwordBuffer.fill('\u0000')
            }
            result
                .onSuccess { summary -> operation.update { it.copy(isBusy = false, restorePreview = summary) } }
                .onFailure {
                    operation.update {
                        it.copy(isBusy = false, message = backupFailureMessage(result.exceptionOrNull()), restorePreview = null)
                    }
                }
        }
    }

    fun clearRestorePreview() {
        operation.update { it.copy(restorePreview = null) }
    }

    fun prepareBackupShare() {
        if (operation.value.isBusy || !operation.value.hasShareableBackup) return
        viewModelScope.launch {
            operation.update {
                it.copy(
                    isBusy = true,
                    message = null,
                    pendingBackupShareUri = null,
                    staleBackupShareUri = null,
                )
            }
            runCatching { documentGateway.shareableBackup() }
                .onSuccess { backup ->
                    val zone = ZoneId.systemDefault()
                    val createdToday = backupCreatedOnDate(
                        createdAt = backup.createdAt,
                        date = LocalDate.now(zone),
                        zoneId = zone,
                    )
                    operation.update {
                        it.copy(
                            isBusy = false,
                            pendingBackupShareUri = backup.uri.takeIf { createdToday },
                            staleBackupShareUri = backup.uri.takeUnless { createdToday },
                        )
                    }
                }
                .onFailure {
                    operation.update {
                        it.copy(
                            isBusy = false,
                            message = SettingsMessage.BACKUP_SHARE_UNAVAILABLE,
                            hasShareableBackup = false,
                        )
                    }
                }
        }
    }

    fun continueWithExistingBackup() {
        operation.update {
            it.copy(
                pendingBackupShareUri = it.staleBackupShareUri,
                staleBackupShareUri = null,
            )
        }
    }

    fun clearStaleBackupPrompt() {
        operation.update { it.copy(staleBackupShareUri = null) }
    }

    fun backupShareHandled(launched: Boolean) {
        operation.update {
            it.copy(
                pendingBackupShareUri = null,
                message = if (launched) it.message else SettingsMessage.BACKUP_SHARE_UNAVAILABLE,
            )
        }
    }

    fun deleteAllData() = runOperation(SettingsMessage.DATA_DELETED, SettingsMessage.FILE_FAILED) {
        backupManager.deleteAllData()
        documentGateway.deleteShareableBackup()
        reminderScheduler.cancel()
        settingsRepository.update { AppSettings() }
        operation.update {
            it.copy(
                hasShareableBackup = false,
                pendingBackupShareUri = null,
                staleBackupShareUri = null,
            )
        }
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    private fun runOperation(
        success: SettingsMessage,
        failure: SettingsMessage,
        failureMessage: (Throwable?) -> SettingsMessage = { failure },
        block: suspend () -> Unit,
    ) {
        if (operation.value.isBusy) return
        viewModelScope.launch {
            operation.update { it.copy(isBusy = true, message = null) }
            runCatching { block() }
                .onSuccess { operation.update { it.copy(isBusy = false, message = success) } }
                .onFailure { error -> operation.update { it.copy(isBusy = false, message = failureMessage(error)) } }
        }
    }

    private fun backupFailureMessage(error: Throwable?): SettingsMessage =
        if (error is UnsupportedBackupVersionException) SettingsMessage.BACKUP_UNSUPPORTED
        else SettingsMessage.BACKUP_FAILED

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val reminderScheduler: ReminderScheduler,
        private val backupManager: BackupManager,
        private val documentGateway: DocumentGateway,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(settingsRepository, reminderScheduler, backupManager, documentGateway) as T
    }
}

internal fun backupCreatedOnDate(
    createdAt: Instant,
    date: LocalDate,
    zoneId: ZoneId,
): Boolean = createdAt.atZone(zoneId).toLocalDate() == date

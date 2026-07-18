package com.yv.bbttracker.app

import android.app.Application
import com.yv.bbttracker.data.local.AppDatabase
import com.yv.bbttracker.data.backup.BackupManager
import com.yv.bbttracker.data.backup.DocumentGateway
import com.yv.bbttracker.data.repository.CycleRepositoryImpl
import com.yv.bbttracker.data.repository.MeasurementRepositoryImpl
import com.yv.bbttracker.data.repository.ObservationRepositoryImpl
import com.yv.bbttracker.data.settings.SettingsRepositoryImpl
import com.yv.bbttracker.domain.repository.CycleRepository
import com.yv.bbttracker.domain.repository.MeasurementRepository
import com.yv.bbttracker.domain.repository.ObservationRepository
import com.yv.bbttracker.domain.repository.SettingsRepository
import com.yv.bbttracker.notification.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class AppContainer(val application: Application) {
    val database: AppDatabase by lazy { AppDatabase.create(application) }

    val cycleRepository: CycleRepository by lazy { CycleRepositoryImpl(database) }
    val measurementRepository: MeasurementRepository by lazy { MeasurementRepositoryImpl(database) }
    val observationRepository: ObservationRepository by lazy { ObservationRepositoryImpl(database) }
    val settingsRepositoryImpl: SettingsRepositoryImpl by lazy { SettingsRepositoryImpl(application) }
    val settingsRepository: SettingsRepository get() = settingsRepositoryImpl
    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(application) }
    val backupManager: BackupManager by lazy {
        BackupManager(
            database = database,
            cycleRepository = cycleRepository,
            measurementRepository = measurementRepository,
            observationRepository = observationRepository,
            settingsRepository = settingsRepository,
        )
    }
    val documentGateway: DocumentGateway by lazy { DocumentGateway(application) }

    val applicationScope = CoroutineScope(SupervisorJob())

    fun close() {
        applicationScope.cancel()
        if (database.isOpen) database.close()
    }
}

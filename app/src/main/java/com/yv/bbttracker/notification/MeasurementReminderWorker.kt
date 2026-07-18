package com.yv.bbttracker.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yv.bbttracker.BbtTrackerApplication
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class MeasurementReminderWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? BbtTrackerApplication ?: return Result.failure()
        return runCatching {
            val settings = app.container.settingsRepository.getSettings()
            if (!settings.reminderEnabled) return Result.success()
            val hasMeasurement = app.container.measurementRepository
                .observeMeasurementForDate(LocalDate.now())
                .first()
                .isNotEmpty()
            if (!hasMeasurement) ReminderNotifications.show(applicationContext)
            app.container.reminderScheduler.scheduleNext(settings.reminderHour, settings.reminderMinute)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

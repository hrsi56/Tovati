package com.yv.bbttracker.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class ReminderScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun schedule(hour: Int, minute: Int) {
        enqueue(hour, minute, ExistingWorkPolicy.REPLACE)
    }

    /** Called by the worker so every occurrence is recalculated in the current time zone/DST. */
    fun scheduleNext(hour: Int, minute: Int) {
        enqueue(hour, minute, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun enqueue(hour: Int, minute: Int, policy: ExistingWorkPolicy) {
        val now = ZonedDateTime.now()
        var next = now.withHour(hour.coerceIn(0, 23)).withMinute(minute.coerceIn(0, 59)).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<MeasurementReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            policy,
            request,
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "morning-measurement-reminder"
    }
}

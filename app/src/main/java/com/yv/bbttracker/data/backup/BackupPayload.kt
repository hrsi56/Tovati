package com.yv.bbttracker.data.backup

import com.yv.bbttracker.BuildConfig
import com.yv.bbttracker.domain.model.AppSettings
import com.yv.bbttracker.domain.model.Cycle
import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import kotlinx.serialization.Serializable

@Serializable
data class BackupPayload(
    val schemaVersion: Int = SCHEMA_VERSION,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val cycles: List<Cycle>,
    val temperatureMeasurements: List<TemperatureMeasurement>,
    val dailyObservations: List<DailyObservation>,
    val settings: AppSettings,
) {
    companion object {
        const val MIN_SUPPORTED_SCHEMA_VERSION = 1
        const val SCHEMA_VERSION = 5
    }
}

data class RestoreSummary(
    val cycleCount: Int,
    val measurementCount: Int,
    val observationCount: Int,
)

/**
 * Backups created before app version 1.6.2 may contain several temperatures for one date.
 * Restoring follows the current rule: the record saved most recently for each date wins.
 */
internal fun BackupPayload.withLatestTemperaturePerDate(): BackupPayload = copy(
    temperatureMeasurements = temperatureMeasurements
        .groupBy(TemperatureMeasurement::measurementEpochDay)
        .values
        .map { sameDay ->
            sameDay.maxWith(
                compareBy<TemperatureMeasurement> { it.updatedAtEpochMillis }
                    .thenBy { it.id },
            )
        }
        .sortedBy(TemperatureMeasurement::measurementEpochDay),
)

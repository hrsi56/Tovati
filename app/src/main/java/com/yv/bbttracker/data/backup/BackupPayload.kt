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

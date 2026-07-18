package com.yv.bbttracker.domain.repository

import com.yv.bbttracker.domain.model.AppSettings
import com.yv.bbttracker.domain.model.Cycle
import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.MeasurementInput
import com.yv.bbttracker.domain.model.ObservationInput
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface CycleRepository {
    fun observeCurrentCycle(): Flow<Cycle?>
    fun observeCycles(): Flow<List<Cycle>>
    suspend fun getCycles(): List<Cycle>
    suspend fun startCycle(date: LocalDate, note: String? = null): Result<Long>
    suspend fun updateCycleStart(cycleId: Long, date: LocalDate): Result<Unit>
    suspend fun deleteCycle(cycleId: Long): Result<Unit>
}

interface MeasurementRepository {
    fun observeMeasurementsForCycle(cycleId: Long): Flow<List<TemperatureMeasurement>>
    fun observeMeasurementsInRange(start: LocalDate, end: LocalDate): Flow<List<TemperatureMeasurement>>
    fun observeMeasurementForDate(date: LocalDate): Flow<List<TemperatureMeasurement>>
    fun observeAllMeasurements(): Flow<List<TemperatureMeasurement>>
    suspend fun getAllMeasurements(): List<TemperatureMeasurement>
    suspend fun getMeasurement(id: Long): TemperatureMeasurement?
    suspend fun saveMeasurement(input: MeasurementInput): Result<Long>
    suspend fun selectForAnalysis(measurementId: Long): Result<Unit>
    suspend fun deleteMeasurement(measurementId: Long): Result<Unit>
}

interface ObservationRepository {
    fun observeObservation(date: LocalDate): Flow<DailyObservation?>
    fun observeObservations(start: LocalDate, end: LocalDate): Flow<List<DailyObservation>>
    fun observeAllObservations(): Flow<List<DailyObservation>>
    suspend fun getAllObservations(): List<DailyObservation>
    suspend fun upsertObservation(input: ObservationInput, startCycle: Boolean = false): Result<Unit>
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun getSettings(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

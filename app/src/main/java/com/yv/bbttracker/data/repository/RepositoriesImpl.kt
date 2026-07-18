package com.yv.bbttracker.data.repository

import androidx.room.withTransaction
import com.yv.bbttracker.data.local.AppDatabase
import com.yv.bbttracker.data.local.entity.CycleEntity
import com.yv.bbttracker.data.local.entity.DailyObservationEntity
import com.yv.bbttracker.data.local.entity.TemperatureMeasurementEntity
import com.yv.bbttracker.domain.model.Cycle
import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.BleedingLevel
import com.yv.bbttracker.domain.model.LH_TEST_BRAND_MAX_LENGTH
import com.yv.bbttracker.domain.model.LH_TEST_SENSITIVITY_MAX_MILLI_IU
import com.yv.bbttracker.domain.model.LH_TEST_SENSITIVITY_MIN_MILLI_IU
import com.yv.bbttracker.domain.model.LhResult
import com.yv.bbttracker.domain.model.MAX_DAILY_PAIN_RELIEF_PILLS
import com.yv.bbttracker.domain.model.MOOD_NOTE_MAX_LENGTH
import com.yv.bbttracker.domain.model.MeasurementInput
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.MeasurementSource
import com.yv.bbttracker.domain.model.ObservationInput
import com.yv.bbttracker.domain.model.PAIN_RELIEF_MEDICATION_NOTE_MAX_LENGTH
import com.yv.bbttracker.domain.model.SexualContact
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import com.yv.bbttracker.domain.repository.CycleRepository
import com.yv.bbttracker.domain.repository.MeasurementRepository
import com.yv.bbttracker.domain.repository.ObservationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private class CycleOverlapException : IllegalArgumentException("Cycle dates overlap")

private fun BleedingLevel.isActualBleeding(): Boolean =
    this == BleedingLevel.LIGHT || this == BleedingLevel.MEDIUM || this == BleedingLevel.HEAVY

private val selectedMeasurementOrder =
    compareBy<TemperatureMeasurementEntity> { it.measurementEpochDay }
        .thenBy { it.measuredAtEpochMillis }
        .thenBy { it.createdAtEpochMillis }
        .thenBy { it.id }

private fun List<TemperatureMeasurementEntity>.resolveAnalysisSiteInRange(
    currentSite: MeasurementSite?,
    startEpochDay: Long,
    endEpochDay: Long? = null,
): MeasurementSite? {
    val selectedInRange = asSequence()
        .filter { it.selectedForAnalysis }
        .filter { it.measurementEpochDay >= startEpochDay }
        .filter { endEpochDay == null || it.measurementEpochDay <= endEpochDay }
        .sortedWith(selectedMeasurementOrder)
        .toList()
    return currentSite?.takeIf { site -> selectedInRange.any { it.site == site } }
        ?: selectedInRange.firstOrNull()?.site
}

private suspend fun AppDatabase.firstSelectedSiteInRange(
    startEpochDay: Long,
    endEpochDay: Long? = null,
): MeasurementSite? = measurementDao().getAll().resolveAnalysisSiteInRange(
    currentSite = null,
    startEpochDay = startEpochDay,
    endEpochDay = endEpochDay,
)

private suspend fun AppDatabase.lockAnalysisSiteForDay(epochDay: Long, site: MeasurementSite) {
    val cycle = cycleDao().getAll().lastOrNull { entity ->
        epochDay >= entity.startEpochDay && (entity.endEpochDay == null || epochDay <= entity.endEpochDay)
    }
    if (cycle != null && cycle.analysisSite == null) {
        cycleDao().update(
            cycle.copy(
                analysisSite = site,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }
}

class CycleRepositoryImpl(private val database: AppDatabase) : CycleRepository {
    private val dao = database.cycleDao()

    override fun observeCurrentCycle(): Flow<Cycle?> = dao.observeCurrent().map { it?.toDomain() }

    override fun observeCycles(): Flow<List<Cycle>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getCycles(): List<Cycle> = dao.getAll().map { it.toDomain() }

    override suspend fun startCycle(date: LocalDate, note: String?): Result<Long> = runCatching {
        database.withTransaction {
            val day = date.toEpochDay()
            val cycles = dao.getAll()
            if (cycles.any { it.endEpochDay != null && day >= it.startEpochDay && day <= it.endEpochDay }) {
                throw CycleOverlapException()
            }
            val latest = cycles.maxByOrNull { it.startEpochDay }
            if (latest != null && day <= latest.startEpochDay) throw CycleOverlapException()
            val now = System.currentTimeMillis()
            if (latest != null && latest.endEpochDay == null) {
                dao.update(latest.copy(endEpochDay = day - 1, updatedAtEpochMillis = now))
            }
            dao.insert(
                CycleEntity(
                    startEpochDay = day,
                    endEpochDay = null,
                    analysisSite = database.firstSelectedSiteInRange(day),
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                    note = note?.trim()?.takeIf { it.isNotEmpty() },
                ),
            )
        }
    }

    override suspend fun updateCycleStart(cycleId: Long, date: LocalDate): Result<Unit> = runCatching {
        database.withTransaction {
            val cycles = dao.getAll()
            val entityIndex = cycles.indexOfFirst { it.id == cycleId }
            if (entityIndex < 0) error("Cycle not found")
            val entity = cycles[entityIndex]
            val previous = cycles.getOrNull(entityIndex - 1)
            val newStart = date.toEpochDay()
            val newEnd = entity.endEpochDay
            val targetObservation = database.observationDao().getByDay(newStart)
            if (targetObservation?.bleeding?.isActualBleeding() != true) {
                throw IllegalArgumentException("Actual bleeding must be recorded on cycle start")
            }
            if (previous != null && newStart <= previous.startEpochDay) throw CycleOverlapException()
            if (newEnd != null && newStart > newEnd) throw CycleOverlapException()
            val thisEnd = newEnd ?: Long.MAX_VALUE
            val overlapsNonAdjacentCycle = cycles.any { other ->
                other.id != entity.id &&
                    other.id != previous?.id &&
                    newStart <= (other.endEpochDay ?: Long.MAX_VALUE) &&
                    other.startEpochDay <= thisEnd
            }
            if (overlapsNonAdjacentCycle) throw CycleOverlapException()
            val now = System.currentTimeMillis()
            val measurements = database.measurementDao().getAll()
            previous?.let { previousCycle ->
                val adjustedEnd = newStart - 1
                dao.update(
                    previousCycle.copy(
                        endEpochDay = adjustedEnd,
                        analysisSite = measurements.resolveAnalysisSiteInRange(
                            currentSite = previousCycle.analysisSite,
                            startEpochDay = previousCycle.startEpochDay,
                            endEpochDay = adjustedEnd,
                        ),
                        updatedAtEpochMillis = now,
                    ),
                )
            }
            dao.update(
                entity.copy(
                    startEpochDay = newStart,
                    analysisSite = measurements.resolveAnalysisSiteInRange(
                        currentSite = entity.analysisSite,
                        startEpochDay = newStart,
                        endEpochDay = newEnd,
                    ),
                    updatedAtEpochMillis = now,
                ),
            )
            database.observationDao().getByDay(entity.startEpochDay)
                ?.takeIf { it.isExplicitCycleStart && it.epochDay != newStart }
                ?.let { database.observationDao().update(it.copy(isExplicitCycleStart = false, updatedAtEpochMillis = now)) }
            if (!targetObservation.isExplicitCycleStart) {
                database.observationDao().update(
                    targetObservation.copy(isExplicitCycleStart = true, updatedAtEpochMillis = now),
                )
            }
        }
    }

    override suspend fun deleteCycle(cycleId: Long): Result<Unit> = runCatching {
        database.withTransaction {
            val entity = dao.getById(cycleId) ?: return@withTransaction
            dao.delete(entity)
            database.observationDao().getByDay(entity.startEpochDay)
                ?.takeIf { it.isExplicitCycleStart }
                ?.let {
                    database.observationDao().update(
                        it.copy(isExplicitCycleStart = false, updatedAtEpochMillis = System.currentTimeMillis()),
                    )
                }
            val latest = dao.getAll().maxByOrNull { it.startEpochDay }
            if (latest != null && latest.endEpochDay != null) {
                dao.update(
                    latest.copy(
                        endEpochDay = null,
                        analysisSite = latest.analysisSite
                            ?: database.firstSelectedSiteInRange(latest.startEpochDay),
                        updatedAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }
}

class MeasurementRepositoryImpl(private val database: AppDatabase) : MeasurementRepository {
    private val dao = database.measurementDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMeasurementsForCycle(cycleId: Long): Flow<List<TemperatureMeasurement>> =
        database.cycleDao().observeAll().flatMapLatest { cycles ->
            val cycle = cycles.firstOrNull { it.id == cycleId }
            if (cycle == null) flowOf(emptyList())
            else dao.observeInRange(cycle.startEpochDay, cycle.endEpochDay ?: Long.MAX_VALUE)
                .map { rows -> rows.map { it.toDomain() } }
        }

    override fun observeMeasurementsInRange(start: LocalDate, end: LocalDate): Flow<List<TemperatureMeasurement>> =
        dao.observeInRange(start.toEpochDay(), end.toEpochDay()).map { rows -> rows.map { it.toDomain() } }

    override fun observeMeasurementForDate(date: LocalDate): Flow<List<TemperatureMeasurement>> =
        dao.observeForDate(date.toEpochDay()).map { rows -> rows.map { it.toDomain() } }

    override fun observeAllMeasurements(): Flow<List<TemperatureMeasurement>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getAllMeasurements(): List<TemperatureMeasurement> = dao.getAll().map { it.toDomain() }

    override suspend fun getMeasurement(id: Long): TemperatureMeasurement? = dao.getById(id)?.toDomain()

    override suspend fun saveMeasurement(input: MeasurementInput): Result<Long> = runCatching {
        database.withTransaction {
            val now = System.currentTimeMillis()
            val existing = input.id.takeIf { it != 0L }?.let { dao.getById(it) }
            val entity = TemperatureMeasurementEntity(
                id = input.id,
                measurementEpochDay = input.date.toEpochDay(),
                measuredAtEpochMillis = input.measuredAtEpochMillis,
                timezoneId = input.timezoneId,
                temperatureCentiC = input.temperatureCentiC,
                site = input.site,
                sleepMinutes = input.sleepMinutes,
                measuredImmediatelyAfterWaking = input.measuredImmediatelyAfterWaking,
                disturbanceMask = input.disturbanceMask,
                disturbanceNote = input.disturbanceNote?.trim()?.takeIf { it.isNotEmpty() },
                note = input.note?.trim()?.takeIf { it.isNotEmpty() },
                selectedForAnalysis = false,
                source = MeasurementSource.MANUAL,
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
            )
            val id = if (existing == null) dao.insert(entity) else {
                dao.update(entity)
                entity.id
            }
            if (input.selectedForAnalysis) {
                dao.selectForAnalysis(id, input.date.toEpochDay(), now)
                database.lockAnalysisSiteForDay(input.date.toEpochDay(), input.site)
            }
            id
        }
    }

    override suspend fun selectForAnalysis(measurementId: Long): Result<Unit> = runCatching {
        database.withTransaction {
            val measurement = dao.getById(measurementId) ?: error("Measurement not found")
            dao.selectForAnalysis(measurementId, measurement.measurementEpochDay, System.currentTimeMillis())
            database.lockAnalysisSiteForDay(measurement.measurementEpochDay, measurement.site)
        }
    }

    override suspend fun deleteMeasurement(measurementId: Long): Result<Unit> = runCatching {
        database.withTransaction {
            val deleted = dao.getById(measurementId) ?: return@withTransaction
            dao.deleteById(measurementId)
            if (deleted.selectedForAnalysis) {
                val remaining = dao.getAll().filter { it.measurementEpochDay == deleted.measurementEpochDay }
                remaining.lastOrNull()?.let { dao.selectForAnalysis(it.id, it.measurementEpochDay, System.currentTimeMillis()) }
            }
        }
    }
}

class ObservationRepositoryImpl(private val database: AppDatabase) : ObservationRepository {
    private val dao = database.observationDao()

    override fun observeObservation(date: LocalDate): Flow<DailyObservation?> =
        dao.observeForDate(date.toEpochDay()).map { it?.toDomain() }

    override fun observeObservations(start: LocalDate, end: LocalDate): Flow<List<DailyObservation>> =
        dao.observeInRange(start.toEpochDay(), end.toEpochDay()).map { rows -> rows.map { it.toDomain() } }

    override fun observeAllObservations(): Flow<List<DailyObservation>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getAllObservations(): List<DailyObservation> = dao.getAll().map { it.toDomain() }

    override suspend fun upsertObservation(input: ObservationInput, startCycle: Boolean): Result<Unit> = runCatching {
        database.withTransaction {
            val day = input.date.toEpochDay()
            val now = System.currentTimeMillis()
            if (startCycle) {
                require(input.isExplicitCycleStart)
                require(input.bleeding.isActualBleeding())
                val cycleDao = database.cycleDao()
                val cycles = cycleDao.getAll()
                if (cycles.none { it.startEpochDay == day }) {
                    if (cycles.any { it.endEpochDay != null && day in it.startEpochDay..it.endEpochDay }) {
                        throw CycleOverlapException()
                    }
                    val latest = cycles.maxByOrNull { it.startEpochDay }
                    if (latest != null && day <= latest.startEpochDay) throw CycleOverlapException()
                    if (latest != null && latest.endEpochDay == null) {
                        cycleDao.update(latest.copy(endEpochDay = day - 1, updatedAtEpochMillis = now))
                    }
                    cycleDao.insert(
                        CycleEntity(
                            startEpochDay = day,
                            endEpochDay = null,
                            analysisSite = database.firstSelectedSiteInRange(day),
                            createdAtEpochMillis = now,
                            updatedAtEpochMillis = now,
                            note = null,
                        ),
                    )
                }
            }
            val existing = dao.getByDay(day)
            val hasLhTest = input.lhResult != LhResult.NOT_TESTED
            val lhTestMinutesOfDay = input.lhTestMinutesOfDay
                ?.takeIf { hasLhTest }
                ?.also { require(it in 0..1_439) }
            val lhTestBrand = input.lhTestBrand
                ?.trim()
                ?.takeIf { hasLhTest && it.isNotEmpty() }
                ?.also { require(it.length <= LH_TEST_BRAND_MAX_LENGTH) }
            val lhTestSensitivity = input.lhTestSensitivityMilliIu
                ?.takeIf { hasLhTest }
                ?.also {
                    require(it in LH_TEST_SENSITIVITY_MIN_MILLI_IU..LH_TEST_SENSITIVITY_MAX_MILLI_IU)
                }
            val moodNote = input.moodNote
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.take(MOOD_NOTE_MAX_LENGTH)
            val initiatedByUser = input.sexualContactInitiatedByUser
                ?.takeIf { input.sexualContact == SexualContact.YES }
            val painReliefPillCount = input.painReliefPillCount
                ?.also { require(it in 0..MAX_DAILY_PAIN_RELIEF_PILLS) }
            val painReliefMedicationNote = input.painReliefMedicationNote
                ?.trim()
                ?.takeIf { painReliefPillCount != null && painReliefPillCount > 0 && it.isNotEmpty() }
                ?.take(PAIN_RELIEF_MEDICATION_NOTE_MAX_LENGTH)
            val entity = DailyObservationEntity(
                id = existing?.id ?: 0,
                epochDay = day,
                bleeding = input.bleeding,
                mucus = input.mucus,
                mucusSensation = input.mucusSensation,
                mucusObscured = input.mucusObscured,
                lhResult = input.lhResult,
                lhTestMinutesOfDay = lhTestMinutesOfDay,
                lhTestBrand = lhTestBrand,
                lhTestSensitivityMilliIu = lhTestSensitivity,
                ovulationPain = input.ovulationPain,
                moodMask = input.moodMask,
                moodNote = moodNote,
                libidoLevel = input.libidoLevel,
                sexualContact = input.sexualContact,
                sexualContactInitiatedByUser = initiatedByUser,
                physicalSymptomMask = input.physicalSymptomMask,
                painReliefPillCount = painReliefPillCount,
                painReliefMedicationNote = painReliefMedicationNote,
                isExplicitCycleStart = input.isExplicitCycleStart,
                note = input.note?.trim()?.takeIf { it.isNotEmpty() },
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
            )
            if (existing == null) dao.insert(entity) else dao.update(entity)
        }
    }
}

package com.yv.bbttracker.data.backup

import androidx.room.withTransaction
import com.yv.bbttracker.data.local.AppDatabase
import com.yv.bbttracker.data.local.entity.CycleEntity
import com.yv.bbttracker.data.local.entity.DailyObservationEntity
import com.yv.bbttracker.data.local.entity.TemperatureMeasurementEntity
import com.yv.bbttracker.data.repository.toDomain
import com.yv.bbttracker.domain.model.AppSettings
import com.yv.bbttracker.domain.model.Cycle
import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.LH_TEST_BRAND_MAX_LENGTH
import com.yv.bbttracker.domain.model.LH_TEST_SENSITIVITY_MAX_MILLI_IU
import com.yv.bbttracker.domain.model.LH_TEST_SENSITIVITY_MIN_MILLI_IU
import com.yv.bbttracker.domain.model.LhResult
import com.yv.bbttracker.domain.model.MAX_DAILY_PAIN_RELIEF_PILLS
import com.yv.bbttracker.domain.model.MAX_TYPICAL_MENSTRUATION_LENGTH_DAYS
import com.yv.bbttracker.domain.model.MAX_TYPICAL_CYCLE_LENGTH_DAYS
import com.yv.bbttracker.domain.model.MOOD_NOTE_MAX_LENGTH
import com.yv.bbttracker.domain.model.MeasurementSource
import com.yv.bbttracker.domain.model.MIN_TYPICAL_MENSTRUATION_LENGTH_DAYS
import com.yv.bbttracker.domain.model.MIN_TYPICAL_CYCLE_LENGTH_DAYS
import com.yv.bbttracker.domain.model.MoodFlag
import com.yv.bbttracker.domain.model.PhysicalSymptomFlag
import com.yv.bbttracker.domain.model.PAIN_RELIEF_MEDICATION_NOTE_MAX_LENGTH
import com.yv.bbttracker.domain.model.SexualContact
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import com.yv.bbttracker.domain.repository.CycleRepository
import com.yv.bbttracker.domain.repository.MeasurementRepository
import com.yv.bbttracker.domain.repository.ObservationRepository
import com.yv.bbttracker.domain.repository.SettingsRepository
import com.yv.bbttracker.ui.formatting.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

class BackupManager(
    private val database: AppDatabase,
    private val cycleRepository: CycleRepository,
    private val measurementRepository: MeasurementRepository,
    private val observationRepository: ObservationRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        explicitNulls = true
    }

    suspend fun createEncryptedBackup(password: CharArray): String = withContext(Dispatchers.Default) {
        val payload = currentPayload()
        BackupCrypto.encrypt(json.encodeToString(BackupPayload.serializer(), payload), password)
    }

    suspend fun restoreEncryptedBackup(envelope: String, password: CharArray): RestoreSummary = withContext(Dispatchers.Default) {
        val payload = decodeAndValidate(envelope, password)

        // This in-memory snapshot provides a recovery source in addition to Room's transaction
        // rollback, without ever writing unencrypted health data to shared storage.
        val previous = currentPayload()
        try {
            database.withTransaction {
                database.predictionDao().deleteAll()
                database.observationDao().deleteAll()
                database.measurementDao().deleteAll()
                database.cycleDao().deleteAll()
                database.cycleDao().insertAll(payload.cycles.map(Cycle::toEntity))
                database.measurementDao().insertAll(payload.temperatureMeasurements.map(TemperatureMeasurement::toEntity))
                database.observationDao().insertAll(payload.dailyObservations.map(DailyObservation::toEntity))
            }
            settingsRepository.update {
                payload.settings.copy(
                    biometricLockEnabled = false,
                    lastSuccessfulBackupEpochMillis = it.lastSuccessfulBackupEpochMillis,
                )
            }
        } catch (error: Exception) {
            // Room already rolls back database failures. If a failure happened after commit (for
            // example while updating DataStore), restore the original database snapshot.
            database.withTransaction {
                database.predictionDao().deleteAll()
                database.observationDao().deleteAll()
                database.measurementDao().deleteAll()
                database.cycleDao().deleteAll()
                database.cycleDao().insertAll(previous.cycles.map(Cycle::toEntity))
                database.measurementDao().insertAll(previous.temperatureMeasurements.map(TemperatureMeasurement::toEntity))
                database.observationDao().insertAll(previous.dailyObservations.map(DailyObservation::toEntity))
            }
            throw error
        }
        RestoreSummary(
            cycleCount = payload.cycles.size,
            measurementCount = payload.temperatureMeasurements.size,
            observationCount = payload.dailyObservations.size,
        )
    }

    /** Decrypts and validates without changing local data, for the restore confirmation preview. */
    suspend fun inspectEncryptedBackup(envelope: String, password: CharArray): RestoreSummary =
        withContext(Dispatchers.Default) {
            decodeAndValidate(envelope, password).toSummary()
        }

    suspend fun exportCsv(): ByteArray = withContext(Dispatchers.Default) {
        val cycles = cycleRepository.getCycles()
        val observations = observationRepository.getAllObservations().associateBy { it.epochDay }
        val measurementsByDay = measurementRepository.getAllMeasurements().groupBy { it.measurementEpochDay }
        val days = (measurementsByDay.keys + observations.keys).distinct().sorted()
        val rows = days.flatMap { epochDay ->
            val date = LocalDate.ofEpochDay(epochDay)
            val cycle = cycles.lastOrNull { it.contains(date) }
            val observation = observations[epochDay]
            val dayMeasurements: List<TemperatureMeasurement?> = measurementsByDay[epochDay]
                ?.sortedBy { it.measuredAtEpochMillis }
                ?.map { it }
                ?: listOf(null)
            dayMeasurements.map { measurement ->
                listOf(
                    Formatters.date(date),
                    cycle?.let { (epochDay - it.startEpochDay + 1).toString() },
                    measurement?.let { Formatters.temperature(it.temperatureCentiC) },
                    measurement?.let { Formatters.time(it.measuredAtEpochMillis, it.zoneId) },
                    measurement?.site?.name,
                    measurement?.selectedForAnalysis?.toString(),
                    measurement?.disturbanceMask?.toString(),
                    measurement?.sleepMinutes?.toString(),
                    observation?.bleeding?.name,
                    observation?.mucus?.name,
                    observation?.mucusSensation?.name,
                    observation?.mucusObscured?.toString(),
                    observation?.lhResult?.name,
                    observation?.lhTestMinutesOfDay?.let(::formatMinutesOfDay),
                    observation?.lhTestBrand,
                    observation?.lhTestSensitivityMilliIu?.toString(),
                    observation?.ovulationPain?.name,
                    observation?.moodMask?.let(::formatMoodFlags),
                    observation?.moodNote,
                    observation?.libidoLevel?.name,
                    observation?.sexualContact?.name,
                    observation?.sexualContactInitiatedByUser?.toString(),
                    observation?.physicalSymptomMask?.let(::formatPhysicalSymptoms),
                    observation?.painReliefPillCount?.toString(),
                    observation?.painReliefMedicationNote,
                    listOfNotNull(
                        measurement?.note,
                        measurement?.disturbanceNote,
                        observation?.note,
                    ).distinct().joinToString("\n").ifEmpty { null },
                )
            }
        }
        CsvEncoder.encode(rows)
    }

    suspend fun markBackupSuccessful() {
        settingsRepository.update { it.copy(lastSuccessfulBackupEpochMillis = System.currentTimeMillis()) }
    }

    suspend fun deleteAllData() {
        database.withTransaction {
            database.predictionDao().deleteAll()
            database.observationDao().deleteAll()
            database.measurementDao().deleteAll()
            database.cycleDao().deleteAll()
        }
    }

    private suspend fun currentPayload(): BackupPayload {
        val settings = settingsRepository.getSettings().copy(biometricLockEnabled = false)
        return database.withTransaction {
            BackupPayload(
                cycles = database.cycleDao().getAll().map { it.toDomain() },
                temperatureMeasurements = database.measurementDao().getAll().map { it.toDomain() },
                dailyObservations = database.observationDao().getAll().map { it.toDomain() },
                settings = settings,
            )
        }
    }

    private fun decodeAndValidate(envelope: String, password: CharArray): BackupPayload {
        val decrypted = BackupCrypto.decrypt(envelope, password)
        val decodedPayload = try {
            json.decodeFromString(BackupPayload.serializer(), decrypted)
        } catch (_: Exception) {
            throw BackupCryptoException()
        }
        val payload = decodedPayload.withBackfilledAnalysisSites()
        validate(payload)
        return payload
    }

    private fun BackupPayload.toSummary() = RestoreSummary(
        cycleCount = cycles.size,
        measurementCount = temperatureMeasurements.size,
        observationCount = dailyObservations.size,
    )

    private fun validate(payload: BackupPayload) {
        if (payload.schemaVersion !in
            BackupPayload.MIN_SUPPORTED_SCHEMA_VERSION..BackupPayload.SCHEMA_VERSION
        ) {
            throw UnsupportedBackupVersionException()
        }
        if (payload.cycles.map { it.startEpochDay }.distinct().size != payload.cycles.size) throw BackupCryptoException()
        if (payload.settings.typicalCycleLengthDays != null &&
            payload.settings.typicalCycleLengthDays !in
            MIN_TYPICAL_CYCLE_LENGTH_DAYS..MAX_TYPICAL_CYCLE_LENGTH_DAYS
        ) {
            throw BackupCryptoException()
        }
        if (payload.settings.typicalMenstruationLengthDays != null &&
            payload.settings.typicalMenstruationLengthDays !in
            MIN_TYPICAL_MENSTRUATION_LENGTH_DAYS..MAX_TYPICAL_MENSTRUATION_LENGTH_DAYS
        ) {
            throw BackupCryptoException()
        }
        val sortedCycles = payload.cycles.sortedBy { it.startEpochDay }
        if (sortedCycles.count { it.endEpochDay == null } > 1) throw BackupCryptoException()
        sortedCycles.forEachIndexed { index, cycle ->
            if (cycle.endEpochDay != null && cycle.endEpochDay < cycle.startEpochDay) throw BackupCryptoException()
            val next = sortedCycles.getOrNull(index + 1)
            if (next != null && (cycle.endEpochDay ?: Long.MAX_VALUE) >= next.startEpochDay) throw BackupCryptoException()
        }
        if (payload.dailyObservations.map { it.epochDay }.distinct().size != payload.dailyObservations.size) {
            throw BackupCryptoException()
        }
        payload.dailyObservations.forEach { observation ->
            val allowedMoodMask = MoodFlag.all.fold(0L) { mask, flag -> mask or flag }
            val allowedSymptomMask = PhysicalSymptomFlag.all.fold(0L) { mask, flag -> mask or flag }
            if (observation.moodMask and allowedMoodMask.inv() != 0L) throw BackupCryptoException()
            if (observation.physicalSymptomMask and allowedSymptomMask.inv() != 0L) {
                throw BackupCryptoException()
            }
            if (observation.moodNote != null &&
                (observation.moodNote.isBlank() || observation.moodNote.length > MOOD_NOTE_MAX_LENGTH)
            ) {
                throw BackupCryptoException()
            }
            if (observation.lhTestMinutesOfDay != null && observation.lhTestMinutesOfDay !in 0..1_439) {
                throw BackupCryptoException()
            }
            if (observation.lhTestBrand != null &&
                (observation.lhTestBrand.isBlank() || observation.lhTestBrand.length > LH_TEST_BRAND_MAX_LENGTH)
            ) {
                throw BackupCryptoException()
            }
            if (observation.lhTestSensitivityMilliIu != null &&
                observation.lhTestSensitivityMilliIu !in
                LH_TEST_SENSITIVITY_MIN_MILLI_IU..LH_TEST_SENSITIVITY_MAX_MILLI_IU
            ) {
                throw BackupCryptoException()
            }
            if (observation.lhResult == LhResult.NOT_TESTED &&
                (observation.lhTestMinutesOfDay != null ||
                    observation.lhTestBrand != null ||
                    observation.lhTestSensitivityMilliIu != null)
            ) {
                throw BackupCryptoException()
            }
            if (observation.sexualContactInitiatedByUser != null &&
                observation.sexualContact != SexualContact.YES
            ) {
                throw BackupCryptoException()
            }
            if (observation.painReliefPillCount != null &&
                observation.painReliefPillCount !in 0..MAX_DAILY_PAIN_RELIEF_PILLS
            ) {
                throw BackupCryptoException()
            }
            if (observation.painReliefMedicationNote != null &&
                (
                    observation.painReliefPillCount == null ||
                        observation.painReliefPillCount <= 0 ||
                        observation.painReliefMedicationNote.isBlank() ||
                        observation.painReliefMedicationNote.length >
                        PAIN_RELIEF_MEDICATION_NOTE_MAX_LENGTH
                    )
            ) {
                throw BackupCryptoException()
            }
        }
        payload.temperatureMeasurements.forEach { measurement ->
            if (measurement.temperatureCentiC !in 3200..4300) throw BackupCryptoException()
            if (measurement.source != MeasurementSource.MANUAL) throw BackupCryptoException()
            if (measurement.sleepMinutes != null && measurement.sleepMinutes !in 0..1_440) throw BackupCryptoException()
        }
        val selectedCounts = payload.temperatureMeasurements.filter { it.selectedForAnalysis }.groupingBy { it.measurementEpochDay }.eachCount()
        if (selectedCounts.values.any { it > 1 }) throw BackupCryptoException()
    }
}

private fun Cycle.toEntity() = CycleEntity(
    id = id,
    startEpochDay = startEpochDay,
    endEpochDay = endEpochDay,
    analysisSite = analysisSite,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    note = note,
)

private fun TemperatureMeasurement.toEntity() = TemperatureMeasurementEntity(
    id = id,
    measurementEpochDay = measurementEpochDay,
    measuredAtEpochMillis = measuredAtEpochMillis,
    timezoneId = timezoneId,
    temperatureCentiC = temperatureCentiC,
    site = site,
    sleepMinutes = sleepMinutes,
    measuredImmediatelyAfterWaking = measuredImmediatelyAfterWaking,
    disturbanceMask = disturbanceMask,
    disturbanceNote = disturbanceNote,
    note = note,
    selectedForAnalysis = selectedForAnalysis,
    source = source,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun DailyObservation.toEntity() = DailyObservationEntity(
    id = id,
    epochDay = epochDay,
    bleeding = bleeding,
    mucus = mucus,
    mucusSensation = mucusSensation,
    mucusObscured = mucusObscured,
    lhResult = lhResult,
    lhTestMinutesOfDay = lhTestMinutesOfDay,
    lhTestBrand = lhTestBrand,
    lhTestSensitivityMilliIu = lhTestSensitivityMilliIu,
    ovulationPain = ovulationPain,
    moodMask = moodMask,
    moodNote = moodNote,
    libidoLevel = libidoLevel,
    sexualContact = sexualContact,
    sexualContactInitiatedByUser = sexualContactInitiatedByUser,
    physicalSymptomMask = physicalSymptomMask,
    painReliefPillCount = painReliefPillCount,
    painReliefMedicationNote = painReliefMedicationNote,
    isExplicitCycleStart = isExplicitCycleStart,
    note = note,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun formatMinutesOfDay(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

private fun formatMoodFlags(mask: Long): String = buildList {
    if (mask and MoodFlag.CALM != 0L) add("CALM")
    if (mask and MoodFlag.HAPPY != 0L) add("HAPPY")
    if (mask and MoodFlag.ENERGETIC != 0L) add("ENERGETIC")
    if (mask and MoodFlag.SENSITIVE != 0L) add("SENSITIVE")
    if (mask and MoodFlag.IRRITABLE != 0L) add("IRRITABLE")
    if (mask and MoodFlag.ANXIOUS != 0L) add("ANXIOUS")
    if (mask and MoodFlag.SAD != 0L) add("SAD")
    if (mask and MoodFlag.LOW != 0L) add("LOW")
}.joinToString("|")

private fun formatPhysicalSymptoms(mask: Long): String = buildList {
    if (mask and PhysicalSymptomFlag.ABDOMINAL_PAIN != 0L) add("ABDOMINAL_PAIN")
    if (mask and PhysicalSymptomFlag.HEADACHE != 0L) add("HEADACHE")
    if (mask and PhysicalSymptomFlag.ACNE != 0L) add("ACNE")
    if (mask and PhysicalSymptomFlag.BLOATING != 0L) add("BLOATING")
    if (mask and PhysicalSymptomFlag.BACK_PAIN != 0L) add("BACK_PAIN")
    if (mask and PhysicalSymptomFlag.BREAST_TENDERNESS != 0L) add("BREAST_TENDERNESS")
    if (mask and PhysicalSymptomFlag.FATIGUE != 0L) add("FATIGUE")
    if (mask and PhysicalSymptomFlag.NAUSEA != 0L) add("NAUSEA")
    if (mask and PhysicalSymptomFlag.PELVIC_PRESSURE != 0L) add("PELVIC_PRESSURE")
    if (mask and PhysicalSymptomFlag.SLEEP_CHANGES != 0L) add("SLEEP_CHANGES")
    if (mask and PhysicalSymptomFlag.OTHER != 0L) add("OTHER")
}.joinToString("|")

internal fun BackupPayload.withBackfilledAnalysisSites(): BackupPayload = copy(
    cycles = cycles.map { cycle ->
        if (cycle.analysisSite != null) {
            cycle
        } else {
            val firstSelectedSite = temperatureMeasurements
                .asSequence()
                .filter { it.selectedForAnalysis && cycle.contains(it.date) }
                .minWithOrNull(
                    compareBy<TemperatureMeasurement> { it.measurementEpochDay }
                        .thenBy { it.measuredAtEpochMillis }
                        .thenBy { it.createdAtEpochMillis }
                        .thenBy { it.id },
                )
                ?.site
            cycle.copy(analysisSite = firstSelectedSite)
        }
    },
)

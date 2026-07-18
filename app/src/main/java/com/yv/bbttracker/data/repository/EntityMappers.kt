package com.yv.bbttracker.data.repository

import com.yv.bbttracker.data.local.entity.CycleEntity
import com.yv.bbttracker.data.local.entity.DailyObservationEntity
import com.yv.bbttracker.data.local.entity.TemperatureMeasurementEntity
import com.yv.bbttracker.domain.model.Cycle
import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.TemperatureMeasurement

internal fun CycleEntity.toDomain() = Cycle(
    id = id,
    startEpochDay = startEpochDay,
    endEpochDay = endEpochDay,
    analysisSite = analysisSite,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    note = note,
)

internal fun TemperatureMeasurementEntity.toDomain() = TemperatureMeasurement(
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

internal fun DailyObservationEntity.toDomain() = DailyObservation(
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

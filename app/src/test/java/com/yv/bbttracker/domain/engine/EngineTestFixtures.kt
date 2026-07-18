package com.yv.bbttracker.domain.engine

import com.yv.bbttracker.domain.model.BleedingLevel
import com.yv.bbttracker.domain.model.CervicalMucus
import com.yv.bbttracker.domain.model.Cycle
import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.LhResult
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.MeasurementSource
import com.yv.bbttracker.domain.model.OvulationPain
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import java.time.LocalDate
import java.time.ZoneOffset

internal fun measurement(
    date: LocalDate,
    temperatureCentiC: Int,
    id: Long = date.toEpochDay(),
    site: MeasurementSite = MeasurementSite.ORAL,
    selectedForAnalysis: Boolean = true,
    disturbanceMask: Long = 0,
    hour: Int = 7,
    updatedAtEpochMillis: Long = date.toEpochDay(),
): TemperatureMeasurement {
    val measuredAt = date.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    return TemperatureMeasurement(
        id = id,
        measurementEpochDay = date.toEpochDay(),
        measuredAtEpochMillis = measuredAt,
        timezoneId = "UTC",
        temperatureCentiC = temperatureCentiC,
        site = site,
        sleepMinutes = 420,
        measuredImmediatelyAfterWaking = true,
        disturbanceMask = disturbanceMask,
        disturbanceNote = null,
        selectedForAnalysis = selectedForAnalysis,
        source = MeasurementSource.MANUAL,
        createdAtEpochMillis = measuredAt,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

internal fun cycle(
    start: LocalDate,
    lengthDays: Int? = null,
    id: Long = start.toEpochDay(),
): Cycle = Cycle(
    id = id,
    startEpochDay = start.toEpochDay(),
    endEpochDay = lengthDays?.let { start.plusDays((it - 1).toLong()).toEpochDay() },
    createdAtEpochMillis = 0,
    updatedAtEpochMillis = 0,
)

internal fun observation(
    date: LocalDate,
    mucus: CervicalMucus = CervicalMucus.NOT_CHECKED,
    lhResult: LhResult = LhResult.NOT_TESTED,
): DailyObservation = DailyObservation(
    id = date.toEpochDay(),
    epochDay = date.toEpochDay(),
    bleeding = BleedingLevel.NONE,
    mucus = mucus,
    lhResult = lhResult,
    ovulationPain = OvulationPain.NONE,
    isExplicitCycleStart = false,
    note = null,
    createdAtEpochMillis = 0,
    updatedAtEpochMillis = date.toEpochDay(),
)

internal fun regularConfirmedPattern(start: LocalDate): List<TemperatureMeasurement> {
    val baseline = listOf(3600, 3601, 3602, 3600, 3603, 3602)
        .mapIndexed { index, temperature -> measurement(start.plusDays(index.toLong()), temperature) }
    val firstHigh = start.plusDays(6)
    return baseline + listOf(
        measurement(firstHigh, 3613),
        measurement(firstHigh.plusDays(1), 3613),
        measurement(firstHigh.plusDays(2), 3623),
    )
}
